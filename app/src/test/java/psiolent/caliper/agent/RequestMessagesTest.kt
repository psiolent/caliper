package psiolent.caliper.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import psiolent.caliper.data.MessageEntity
import psiolent.caliper.data.MessageRole

/**
 * Pins the shape of one turn's request. The order is the point: the prompt, the profile
 * and the transcript are stable, so a provider's prompt cache can reuse them, and only
 * the newest turn — where today's totals ride — is new. A volatile line that drifted to
 * the front would quietly throw the cache away, and a turn replayed differently than it
 * was sent would break the prefix at that message.
 */
class RequestMessagesTest {

    private fun storedTurn(id: Long, role: MessageRole, content: String) = MessageEntity(
        id = id,
        sessionId = "s",
        role = role,
        content = content,
        thumbs = emptyList(),
        at = id,
    )

    private fun JsonObject.role() = this["role"]!!.jsonPrimitive.content

    private fun JsonObject.text() = this["content"]!!.jsonPrimitive.content

    private fun JsonObject.parts() = this["content"]!! as JsonArray

    private fun JsonArray.partType(index: Int) =
        this[index].jsonObject["type"]!!.jsonPrimitive.content

    private fun JsonArray.partText(index: Int) =
        this[index].jsonObject["text"]!!.jsonPrimitive.content

    private fun JsonArray.partImageUrl(index: Int) =
        this[index].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content

    private fun JsonObject.contentIsText() = this["content"] is JsonPrimitive

    @Test
    fun `stable first, the newest turn last`() {
        val messages = requestMessages(
            system = "PROMPT\nUser profile:\nnothing yet",
            earlier = listOf(
                storedTurn(1, MessageRole.USER, "hi"),
                storedTurn(2, MessageRole.ASSISTANT, "hello"),
            ),
            photosOf = { emptyList() },
            userContent = "Today is 2026-10-20. Today so far: nothing logged\n\nan apple",
            userPhotos = emptyList(),
        )

        assertEquals(listOf("system", "user", "assistant", "user"), messages.map { it.role() })
        assertEquals("PROMPT\nUser profile:\nnothing yet", messages[0].text())
        assertEquals("an apple", messages.last().text().substringAfter("\n\n"))
    }

    @Test
    fun `the volatile briefing is not in the system message`() {
        val messages = requestMessages(
            system = "PROMPT",
            earlier = emptyList(),
            photosOf = { emptyList() },
            userContent = "Today is 2026-10-20. Today so far: nothing logged\n\nan apple",
            userPhotos = emptyList(),
        )

        assertFalse(messages[0].text().contains("Today so far"))
        assertTrue(messages[1].text().startsWith("Today is 2026-10-20."))
    }

    @Test
    fun `an earlier turn is replayed with the photos it was sent with`() {
        val messages = requestMessages(
            system = "PROMPT",
            earlier = listOf(storedTurn(1, MessageRole.USER, "what is this?")),
            photosOf = { if (it == 1L) listOf("data:image/jpeg;base64,AAA") else emptyList() },
            userContent = "and this?",
            userPhotos = listOf("data:image/jpeg;base64,BBB"),
        )

        val replayed = messages[1].parts()
        assertEquals("text", replayed.partType(0))
        assertEquals("what is this?", replayed.partText(0))
        assertEquals("image_url", replayed.partType(1))
        assertEquals("data:image/jpeg;base64,AAA", replayed.partImageUrl(1))
    }

    @Test
    fun `an earlier turn with no photos stays plain text`() {
        val messages = requestMessages(
            system = "PROMPT",
            earlier = listOf(storedTurn(1, MessageRole.USER, "hi")),
            photosOf = { emptyList() },
            userContent = "and this?",
            userPhotos = emptyList(),
        )

        assertTrue(messages[1].contentIsText())
    }

    @Test
    fun `an assistant turn never carries photos`() {
        val messages = requestMessages(
            system = "PROMPT",
            earlier = listOf(storedTurn(2, MessageRole.ASSISTANT, "hello")),
            photosOf = { listOf("data:image/jpeg;base64,AAA") },
            userContent = "and this?",
            userPhotos = emptyList(),
        )

        assertTrue(messages[1].contentIsText())
        assertEquals("assistant", messages[1].role())
    }

    @Test
    fun `the newest turn's own photos are attached as parts`() {
        val messages = requestMessages(
            system = "PROMPT",
            earlier = emptyList(),
            photosOf = { emptyList() },
            userContent = "what is this?",
            userPhotos = listOf("data:image/jpeg;base64,AAA", "data:image/jpeg;base64,BBB"),
        )

        val parts = messages.last().parts()
        assertEquals(3, parts.size)
        assertEquals("text", parts.partType(0))
        assertEquals("what is this?", parts.partText(0))
        assertEquals("image_url", parts.partType(2))
        assertEquals("data:image/jpeg;base64,BBB", parts.partImageUrl(2))
    }

    @Test
    fun `a photo-only turn still carries the briefing`() {
        val messages = requestMessages(
            system = "PROMPT",
            earlier = emptyList(),
            photosOf = { emptyList() },
            userContent = "Today is 2026-10-20. Today so far: nothing logged\n\n",
            userPhotos = listOf("data:image/jpeg;base64,AAA"),
        )

        val parts = messages.last().parts()
        assertTrue(parts.partText(0).startsWith("Today is 2026-10-20."))
    }
}
