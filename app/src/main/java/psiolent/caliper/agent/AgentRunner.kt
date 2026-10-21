package psiolent.caliper.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import psiolent.caliper.data.CaliperRepository
import psiolent.caliper.data.MessageEntity
import psiolent.caliper.data.MessageRole
import psiolent.caliper.data.SessionEntity
import psiolent.caliper.data.SettingsStore
import psiolent.caliper.integrations.AgentLog
import psiolent.caliper.integrations.Attachment
import psiolent.caliper.integrations.UsdaClient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs one session turn: assemble a small context, call the model, execute any
 * tools it asks for, and return its reply. The conversation is append-only for
 * the life of the session; durable facts go to the database, not the thread.
 */
class AgentRunner(
    private val repo: CaliperRepository,
    private val settings: SettingsStore,
    private val agentLog: AgentLog,
) {
    private val tools = ToolSchema.all()

    /**
     * The photos sent so far in the session that is open, by stored message id.
     *
     * Kept only while the session is the active one: a new session archives the old, and
     * the database keeps just the thumbnails for History. Replaying a turn's own photos is
     * what keeps the request prefix identical from turn to turn, which is what a provider's
     * prompt cache can reuse — dropping them would change the prefix and cost a full miss.
     */
    private val sessionPhotos = ConcurrentHashMap<String, ConcurrentHashMap<Long, List<String>>>()

    suspend fun newSession(): String {
        // The previous session is now archived; its full images are not kept.
        sessionPhotos.clear()
        val id = UUID.randomUUID().toString()
        repo.saveSession(SessionEntity(id = id, startedAt = Instant.now().toEpochMilli()))
        return id
    }

    suspend fun send(
        sessionId: String,
        userText: String,
        photos: List<Attachment> = emptyList(),
        onProgress: (String) -> Unit = {},
    ): String {
        val apiKey = settings.openRouterApiKey
        if (apiKey.isBlank()) {
            return reply(sessionId, "Add your OpenRouter API key in Settings, then try again.", 0.0)
        }

        val imageDataUrls = photos.map { it.dataUrl }
        val photosThisSession = sessionPhotos.getOrPut(sessionId) { ConcurrentHashMap() }
        val messageId = repo.addMessage(
            MessageEntity(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = userText,
                // The small copies, so the transcript can show the photo that was sent.
                thumbs = photos.map { it.thumbnail },
                at = Instant.now().toEpochMilli(),
            ),
        )
        if (imageDataUrls.isNotEmpty()) photosThisSession[messageId] = imageDataUrls

        val executor = ToolExecutor(repo, UsdaClient(settings.usdaApiKey), agentLog)
        agentLog.append("── user: ${userText.ifBlank { "(no text)" }} (${photos.size} photo(s))")
        var cost = 0.0
        return try {
            val model = settings.modelFor(imageDataUrls.isNotEmpty())
            val client = OpenRouterClient(apiKey)
            val messages = mutableListOf<JsonObject>()

            val earlier = repo.messages(sessionId).dropLast(1)
            agentLog.append(
                "── context: ${earlier.size} earlier messages, " +
                    "${earlier.sumOf { photosThisSession[it.id]?.size ?: 0 }} photo(s) replayed",
            )

            messages += requestMessages(
                system = Prompt.SYSTEM + "\n\n" + profileSection(),
                earlier = earlier,
                photosOf = { photosThisSession[it].orEmpty() },
                userContent = briefing().let { if (it.isBlank()) userText else "$it\n\n$userText" },
                userPhotos = imageDataUrls,
            )

            onProgress("Thinking\u2026")
            var replyText: String? = null
            var steps = 0
            // A step is a strike when nothing in it succeeded, attributed to its first
            // refused tool. Counting steps rather than calls matters: the model sends
            // several calls at once, so ten refused calls can be two round trips.
            val refusals = RefusalStreak(MAX_REFUSAL_STEPS)
            var promptTokens = 0
            var cachedTokens = 0
            var cacheWriteTokens = 0
            var completionTokens = 0
            while (replyText == null && steps < MAX_STEPS) {
                steps++
                val response = client.chat(model, messages, tools, webSearch = true, sessionId = sessionId)
                val usage = response.usage
                cost += usage?.cost ?: 0.0
                promptTokens += usage?.promptTokens ?: 0
                completionTokens += usage?.completionTokens ?: 0
                cachedTokens += usage?.promptTokensDetails?.cachedTokens ?: 0
                cacheWriteTokens += usage?.promptTokensDetails?.cacheWriteTokens ?: 0

                val message = response.choices.firstOrNull()?.message
                if (message == null) {
                    replyText = "The model returned no reply."
                    break
                }
                val calls = message.toolCalls.orEmpty()
                if (calls.isEmpty()) {
                    replyText = message.content?.takeIf { it.isNotBlank() } ?: "(no reply)"
                    break
                }
                onProgress("Working\u2026 " + calls.joinToString(", ") { it.function.name })
                messages += assistantToolCallMessage(message)
                var failedTool: String? = null
                var failedError = ""
                var anySucceeded = false
                calls.forEach { call ->
                    val result = executor.execute(call.function.name, call.function.arguments)
                    if (result.startsWith("Error:")) {
                        if (failedTool == null) {
                            failedTool = call.function.name
                            failedError = result
                        }
                    } else {
                        anySucceeded = true
                    }
                    messages += toolResultMessage(call.id, result)
                }

                if (refusals.record(failedTool, anySucceeded)) {
                    agentLog.append("── stuck: $failedTool refused ${refusals.steps} steps in a row, giving up")
                    replyText = stuckReply(failedError)
                    break
                }
            }

            agentLog.append(
                "── usage: $promptTokens in, $cachedTokens cached, $cacheWriteTokens written, " +
                    "$completionTokens out",
            )

            reply(
                sessionId,
                replyText ?: "I ran out of room to finish that one, but what I logged is saved. " +
                    "Ask me to continue and I'll pick up where I left off.",
                cost,
            )
        } catch (e: CancellationException) {
            // The turn was abandoned, not broken: the app was closed before it could
            // finish. Reporting that as a model failure wrote "I couldn't reach the
            // model: Job was cancelled" into the transcript, which was not true.
            throw e
        } catch (e: Exception) {
            // A model or network failure must never take the app down, and the key
            // must never end up in an error string.
            val detail = (e.message ?: e.javaClass.simpleName).replace(apiKey, "\u2039key\u203a")
            reply(sessionId, "I couldn't reach the model: $detail", cost)
        }
    }

    private suspend fun reply(sessionId: String, text: String, cost: Double): String {
        repo.addMessage(
            MessageEntity(sessionId = sessionId, role = MessageRole.ASSISTANT, content = text, at = Instant.now().toEpochMilli()),
        )
        settings.recordCost(cost)
        agentLog.append("── reply: ${text.replace('\n', ' ').take(200)}")
        return text
    }

    /**
     * The profile, in the leading system message. It changes only when the agent rewrites
     * it, so it can sit at the front of the request without breaking the cached prefix.
     */
    private suspend fun profileSection(): String =
        "User profile:\n" + repo.profileText().ifBlank { "(nothing recorded yet)" }

    /**
     * The date and today's totals. Volatile by nature — logging anything changes it — so it
     * rides at the tail of the request, where a change costs only the newest turn instead of
     * invalidating everything behind it.
     */
    private suspend fun briefing(): String {
        val today = LocalDate.now(ZoneId.systemDefault())
        val day = repo.day(today)
        val totals = day.totals.amounts.entries
            .joinToString(", ") { "${it.key.name.lowercase()}=${formatMeasure(it.value)}${it.key.unit}" }
            .ifBlank { "nothing logged" }
        return "Today is $today.\nToday so far: $totals"
    }

    /** What to say when the same tool has been refused too many steps running. */
    private fun stuckReply(error: String): String {
        val detail = error.removePrefix("Error:").trim().trimEnd('.')
        return "I kept hitting the same problem, so I stopped rather than looping: $detail. " +
            "Anything saved before that is saved \u2014 ask me to continue and I'll try another way."
    }

    private companion object {
        /**
         * How many model round trips one turn may take before it gives up and offers
         * to continue. Generous on purpose: a single photo of a sushi roll burned
         * through 20, and the model is cheap (all development cost about 11 cents).
         * The bound exists to stop a runaway, not to hurry the agent — a stuck turn is
         * caught much earlier by [MAX_REFUSAL_STEPS].
         */
        const val MAX_STEPS = 100

        /**
         * How many steps running the same tool may be refused before the turn gives up.
         * A step counts once, however many calls it made, and any success in a step
         * resets the count — so this is ten round trips of getting nowhere, which is a
         * lot of leeway, not a budget.
         */
        const val MAX_REFUSAL_STEPS = 10
    }
}

/**
 * The request body for one turn, in the order that keeps the provider's prompt cache
 * useful: the stable part first, the volatile part last.
 *
 * The prompt and the profile change only when the agent rewrites the profile, and the
 * transcript only grows. Anything that changes every turn — today's date, today's totals
 * — rides at the tail, where it costs at most the newest turn instead of invalidating
 * everything behind it.
 */
internal fun requestMessages(
    system: String,
    earlier: List<MessageEntity>,
    photosOf: (Long) -> List<String>,
    userContent: String,
    userPhotos: List<String>,
): List<JsonObject> = buildList {
    add(textMessage("system", system))
    earlier.forEach { add(replayMessage(it, photosOf(it.id))) }
    add(
        if (userPhotos.isEmpty()) {
            textMessage("user", userContent)
        } else {
            userMessageWithImages(userContent, userPhotos)
        },
    )
}

/**
 * One earlier turn, rebuilt exactly as it was sent: text, or text plus the photos that
 * went with it. Identical bytes are the point — a turn replayed differently would change
 * the prefix and cost a cache miss for everything after it.
 */
internal fun replayMessage(previous: MessageEntity, imageDataUrls: List<String>): JsonObject =
    if (previous.role == MessageRole.USER && imageDataUrls.isNotEmpty()) {
        userMessageWithImages(previous.content, imageDataUrls)
    } else {
        textMessage(previous.role.name.lowercase(), previous.content)
    }
