package psiolent.caliper.data

import org.junit.Assert.assertEquals
import org.junit.Test
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.domain.Serving

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `a nutrient panel survives a round trip`() {
        val panel = Nutrients(mapOf(Nutrient.KCAL to 380.0, Nutrient.PROTEIN to 13.5))
        assertEquals(panel, converters.stringToNutrients(converters.nutrientsToString(panel)))
    }

    @Test
    fun `an empty panel round trips`() {
        assertEquals(Nutrients.EMPTY, converters.stringToNutrients(converters.nutrientsToString(Nutrients.EMPTY)))
    }

    @Test
    fun `servings survive a round trip`() {
        val servings = listOf(Serving("cup", 80.0), Serving("slice", 28.0))
        assertEquals(servings, converters.stringToServings(converters.servingsToString(servings)))
    }

    @Test
    fun `ingredients survive a round trip`() {
        val ingredients = listOf(Ingredient("abc", 1.5, "cup"))
        assertEquals(ingredients, converters.stringToIngredients(converters.ingredientsToString(ingredients)))
    }

    @Test
    fun `the estimated set survives a round trip`() {
        val estimated = setOf(Nutrient.SOLUBLE_FIBER, Nutrient.POLYUNSATURATED_FAT)
        assertEquals(estimated, converters.stringToEstimated(converters.estimatedToString(estimated)))
    }
}
