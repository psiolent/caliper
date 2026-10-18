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
    private val executor get() = ToolExecutor(repo, UsdaClient(settings.usdaApiKey))
    private val tools = ToolSchema.all()

    suspend fun newSession(): String {
        val id = UUID.randomUUID().toString()
        repo.saveSession(SessionEntity(id = id, startedAt = Instant.now().toEpochMilli()))
        return id
    }

    suspend fun send(sessionId: String, userText: String, imageDataUrl: String? = null): String {
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isBlank()) return "Add your OpenRouter API key in Settings, then try again."

        repo.addMessage(
            MessageEntity(sessionId = sessionId, role = MessageRole.USER, content = userText, at = Instant.now().toEpochMilli()),
        )

        val model = settings.modelFor(imageDataUrl != null)
        val client = OpenRouterClient(apiKey)
        val messages = mutableListOf<JsonObject>()
        messages += textMessage("system", Prompt.SYSTEM)
        messages += textMessage("system", context())
        repo.messages(sessionId).dropLast(1).forEach { previous ->
            messages += textMessage(previous.role.name.lowercase(), previous.content)
        }
        messages += if (imageDataUrl != null) {
            userMessageWithImage(userText, imageDataUrl)
        } else {
            textMessage("user", userText)
        }

        var cost = 0.0
        repeat(MAX_STEPS) {
            val response = client.chat(model, messages, tools, webSearch = true)
            cost += response.usage?.cost ?: 0.0
            val message = response.choices.firstOrNull()?.message
                ?: return reply(sessionId, "The model returned no reply.", cost)
            val calls = message.toolCalls.orEmpty()
            if (calls.isEmpty()) {
                val text = message.content?.takeIf { it.isNotBlank() } ?: "(no reply)"
                return reply(sessionId, text, cost)
            }
            messages += assistantToolCallMessage(message)
            calls.forEach { call ->
                messages += toolResultMessage(call.id, executor.execute(call.function.name, call.function.arguments))
            }
        }
        return reply(sessionId, "I stopped after $MAX_STEPS steps without finishing. Try rephrasing.", cost)
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
        const val MAX_STEPS = 8
    }
}
