package psiolent.caliper.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import psiolent.caliper.data.Ingredient
import psiolent.caliper.domain.Serving
import java.time.Instant

/**
 * These pin the "fail loudly" behaviour: a shape the app cannot read must come back
 * as null so the caller can tell the agent, never as a quietly emptier result.
 */
class ToolArgsTest {

    private fun args(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `servings read from the documented list shape`() {
        val parsed = args("""{"servings": [{"name": "cup", "grams": 80}]}""").servings()
        assertEquals(listOf(Serving("cup", 80.0)), parsed)
    }

    @Test
    fun `servings also read from the map shape models send`() {
        val parsed = args("""{"servings": {"large egg": 50}}""").servings()
        assertEquals(listOf(Serving("large egg", 50.0)), parsed)
    }

    @Test
    fun `absent servings are simply none`() {
        assertEquals(emptyList<Serving>(), args("""{}""").servings())
    }

    @Test
    fun `a stringified servings value is rejected, not dropped`() {
        assertNull(args("""{"servings": "{\"large egg\": 50}"}""").servings())
    }

    @Test
    fun `a servings row missing its grams is rejected`() {
        assertNull(args("""{"servings": [{"name": "cup"}]}""").servings())
    }

    @Test
    fun `ingredients read from the documented list shape`() {
        val parsed = args("""{"ingredients": [{"target_id": "a", "amount": 1, "unit": "cup"}]}""").ingredients()
        assertEquals(listOf(Ingredient("a", 1.0, "cup")), parsed)
    }

    @Test
    fun `an ingredient using the old food_id key is rejected, not absorbed`() {
        // The key was renamed to match log's shape. Accepting both would hide the drift.
        assertNull(args("""{"ingredients": [{"food_id": "a", "amount": 1, "unit": "cup"}]}""").ingredients())
    }

    @Test
    fun `a stringified ingredients value is rejected, not dropped`() {
        assertNull(args("""{"ingredients": "[{\"target_id\": \"a\"}]"}""").ingredients())
    }

    @Test
    fun `an ingredients row missing its unit is rejected`() {
        assertNull(args("""{"ingredients": [{"target_id": "a", "amount": 1}]}""").ingredients())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a number sent as text is rejected`() {
        args("""{"amount": "two"}""").number("amount")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `text sent where a number belongs is rejected`() {
        args("""{"amount": 2}""").string("amount")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a flag that is not a boolean is rejected rather than read as false`() {
        args("""{"delete": "yes"}""").boolean("delete")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unknown nutrient name is rejected`() {
        args("""{"estimated": ["protien"]}""").stringSet("estimated")
    }

    @Test
    fun `unknown nutrient keys in a panel are reported`() {
        assertEquals(listOf("protien"), unknownNutrientKeys(args("""{"kcal": 100, "protien": 5}""")))
    }

    @Test
    fun `a time in the future is not accepted`() {
        val now = Instant.parse("2026-10-18T14:55:00Z")
        assertEquals(true, isFuture(Instant.parse("2026-10-18T16:00:00Z"), now))
    }

    @Test
    fun `a time a moment ago is accepted despite clock skew`() {
        val now = Instant.parse("2026-10-18T14:55:00Z")
        assertEquals(false, isFuture(Instant.parse("2026-10-18T14:56:00Z"), now))
    }
}
