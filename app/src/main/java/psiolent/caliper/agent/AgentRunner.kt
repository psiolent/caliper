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

    suspend fun newSession(): String {
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
        repo.addMessage(
            MessageEntity(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = userText,
                // The small copies, so the transcript can show the photo that was sent.
                thumbs = photos.map { it.thumbnail },
                at = Instant.now().toEpochMilli(),
            ),
        )

        val executor = ToolExecutor(repo, UsdaClient(settings.usdaApiKey), agentLog)
        agentLog.append("── user: ${userText.ifBlank { "(no text)" }} (${photos.size} photo(s))")
        var cost = 0.0
        return try {
            val model = settings.modelFor(imageDataUrls.isNotEmpty())
            val client = OpenRouterClient(apiKey)
            val messages = mutableListOf<JsonObject>()
            messages += textMessage("system", Prompt.SYSTEM)
            messages += textMessage("system", context())
            repo.messages(sessionId).dropLast(1).forEach { previous ->
                messages += textMessage(previous.role.name.lowercase(), previous.content)
            }
            messages += if (imageDataUrls.isNotEmpty()) {
                userMessageWithImages(userText, imageDataUrls)
            } else {
                textMessage("user", userText)
            }

            onProgress("Thinking\u2026")
            var replyText: String? = null
            var steps = 0
            // A step is a strike when nothing in it succeeded, attributed to its first
            // refused tool. Counting steps rather than calls matters: the model sends
            // several calls at once, so ten refused calls can be two round trips.
            val refusals = RefusalStreak(MAX_REFUSAL_STEPS)
            while (replyText == null && steps < MAX_STEPS) {
                steps++
                val response = client.chat(model, messages, tools, webSearch = true)
                cost += response.usage?.cost ?: 0.0

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

    /** Profile plus a compact briefing: enough state to act, never the whole history. */
    private suspend fun context(): String {
        val profile = repo.profileText().ifBlank { "(nothing recorded yet)" }
        val today = LocalDate.now(ZoneId.systemDefault())
        val day = repo.day(today)
        val totals = day.totals.amounts.entries
            .joinToString(", ") { "${it.key.name.lowercase()}=${formatMeasure(it.value)}${it.key.unit}" }
            .ifBlank { "nothing logged" }
        return buildString {
            appendLine("Today is $today.")
            appendLine()
            appendLine("User profile:")
            appendLine(profile)
            appendLine()
            appendLine("Today so far: $totals")
        }
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
