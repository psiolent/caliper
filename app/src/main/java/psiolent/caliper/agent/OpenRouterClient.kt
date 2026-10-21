package psiolent.caliper.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import psiolent.caliper.integrations.Http

/** One assistant turn as returned by the API. */
@Serializable
data class AssistantMessage(
    val role: String = "assistant",
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(val name: String, val arguments: String)

@Serializable
data class Choice(
    val message: AssistantMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    val cost: Double? = null,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
)

/** How much of a prompt a provider served from its cache instead of reading it anew. */
@Serializable
data class PromptTokensDetails(
    @SerialName("cached_tokens") val cachedTokens: Int = 0,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Int = 0,
)

@Serializable
data class ChatResponse(
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null,
)

@Serializable
data class ApiError(val message: String? = null, val code: Int? = null)

/** A single model listing entry from `GET /api/v1/models`. */
@Serializable
data class ModelInfo(val id: String, val name: String? = null)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Thin client for OpenRouter's OpenAI-compatible chat endpoint.
 *
 * Requests are built as JSON directly because message content is polymorphic
 * (plain text, or a list of text and image parts); responses are parsed into
 * typed models. This is the one place that speaks the wire format.
 */
class OpenRouterClient(private val apiKey: String) {

    suspend fun chat(
        model: String,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        webSearch: Boolean,
        sessionId: String,
    ): ChatResponse {
        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(messages))
            if (tools.isNotEmpty() || webSearch) {
                put("tools", JsonArray(tools + if (webSearch) listOf(webSearchTool()) else emptyList()))
            }
            put("stream", false)
            // The conversation key. Without it OpenRouter treats each request as unrelated,
            // routes it to whichever provider is next, and both the provider's prompt cache
            // and OpenRouter's sticky routing — which keeps a session on one provider — go
            // unused. The value also groups the session's requests in OpenRouter's logs.
            put("session_id", sessionId)
        }
        val response = Http.postJson(
            url = "$BASE_URL/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "HTTP-Referer" to "https://caliper.local",
                "X-Title" to "Caliper",
            ),
            body = json.encodeToString(JsonObject.serializer(), body),
        )
        val parsed = json.decodeFromString(ChatResponse.serializer(), response)
        parsed.error?.message?.let { throw IllegalStateException("OpenRouter error: $it") }
        return parsed
    }

    suspend fun models(): List<ModelInfo> {
        val body = Http.get("$BASE_URL/models")
        val root = json.parseToJsonElement(body) as JsonObject
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.content
            ModelInfo(id, name)
        }
    }

    private fun webSearchTool(): JsonObject = buildJsonObject {
        put("type", "openrouter:web_search")
    }

    private companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
    }
}

/** Build a plain-text message object. */
fun textMessage(role: String, text: String): JsonObject = buildJsonObject {
    put("role", role)
    put("content", text)
}

/** Build a user message whose content is a text part plus any number of images. */
fun userMessageWithImages(text: String, imageDataUrls: List<String>): JsonObject = buildJsonObject {
    put("role", "user")
    put(
        "content",
        JsonArray(
            buildList {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
                imageDataUrls.forEach { url ->
                    add(
                        buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", url) })
                        },
                    )
                }
            },
        ),
    )
}

/** Build the assistant turn that requested tools, so it can be echoed back. */
fun assistantToolCallMessage(message: AssistantMessage): JsonObject = buildJsonObject {
    put("role", "assistant")
    message.content?.let { put("content", it) }
    message.toolCalls?.let { calls ->
        put(
            "tool_calls",
            JsonArray(
                calls.map { call ->
                    buildJsonObject {
                        put("id", call.id)
                        put("type", call.type)
                        put(
                            "function",
                            buildJsonObject {
                                put("name", call.function.name)
                                put("arguments", call.function.arguments)
                            },
                        )
                    }
                },
            ),
        )
    }
}

/** Build a tool result message. */
fun toolResultMessage(toolCallId: String, content: String): JsonObject = buildJsonObject {
    put("role", "tool")
    put("tool_call_id", toolCallId)
    put("content", content)
}
