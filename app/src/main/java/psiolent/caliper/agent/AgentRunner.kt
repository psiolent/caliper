package psiolent.caliper.agent

import kotlinx.serialization.json.JsonObject
import psiolent.caliper.data.CaliperRepository
import psiolent.caliper.data.MessageEntity
import psiolent.caliper.data.MessageRole
import psiolent.caliper.data.SessionEntity
import psiolent.caliper.data.SettingsStore
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
        imageDataUrls: List<String> = emptyList(),
        onProgress: (String) -> Unit = {},
    ): String {
        val apiKey = settings.openRouterApiKey
        if (apiKey.isBlank()) {
            return reply(sessionId, "Add your OpenRouter API key in Settings, then try again.", 0.0)
        }

        repo.addMessage(
            MessageEntity(sessionId = sessionId, role = MessageRole.USER, content = userText, at = Instant.now().toEpochMilli()),
        )

        val executor = ToolExecutor(repo, UsdaClient(settings.usdaApiKey))
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
                calls.forEach { call ->
                    messages += toolResultMessage(call.id, executor.execute(call.function.name, call.function.arguments))
                }
            }

            reply(
                sessionId,
                replyText ?: "I ran out of room to finish that one, but what I logged is saved. " +
                    "Ask me to continue and I'll pick up where I left off.",
                cost,
            )
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
        return text
    }

    /** Profile plus a compact briefing: enough state to act, never the whole history. */
    private suspend fun context(): String {
        val profile = repo.profileText().ifBlank { "(nothing recorded yet)" }
        val today = LocalDate.now(ZoneId.systemDefault())
        val day = repo.day(today)
        val batches = repo.recentBatches(3)
        val totals = day.totals.amounts.entries
            .joinToString(", ") { "${it.key.name.lowercase()}=${it.value.toInt()}${it.key.unit}" }
            .ifBlank { "nothing logged" }
        return buildString {
            appendLine("Today is $today.")
            appendLine()
            appendLine("User profile:")
            appendLine(profile)
            appendLine()
            appendLine("Today so far: $totals")
            if (batches.isNotEmpty()) {
                appendLine("Batches on hand: " + batches.joinToString("; ") { "${it.name} (id=${it.id})" })
            }
        }
    }

    private companion object {
        const val MAX_STEPS = 20
    }
}
