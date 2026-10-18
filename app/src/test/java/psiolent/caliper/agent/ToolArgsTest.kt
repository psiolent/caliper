package psiolent.caliper.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import psiolent.caliper.data.Ingredient
import psiolent.caliper.domain.Serving

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
        val parsed = args("""{"ingredients": [{"food_id": "a", "amount": 1, "unit": "cup"}]}""").ingredients()
        assertEquals(listOf(Ingredient("a", 1.0, "cup")), parsed)
    }

    @Test
    fun `a stringified ingredients value is rejected, not dropped`() {
        assertNull(args("""{"ingredients": "[{\"food_id\": \"a\"}]"}""").ingredients())
    }

    @Test
    fun `an ingredients row missing its unit is rejected`() {
        assertNull(args("""{"ingredients": [{"food_id": "a", "amount": 1}]}""").ingredients())
    }
}
