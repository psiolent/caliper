package psiolent.caliper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import psiolent.caliper.data.FoodEntity
import psiolent.caliper.data.FoodSource
import psiolent.caliper.data.LogEntryEntity
import psiolent.caliper.data.TargetType
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients
import psiolent.caliper.domain.Serving
import psiolent.caliper.integrations.UsdaFood

/**
 * What the model is told about foods and amounts. Both used to be quietly wrong: a recipe
 * printed "0 cup" for half a cup, and a search miss claimed the library was empty.
 */
class FoodDescriptionTest {

    @Test
    fun `amounts keep their fraction`() {
        assertEquals("0.5", formatAmount(0.5))
        assertEquals("0.25", formatAmount(0.25))
        assertEquals("1.75", formatAmount(1.75))
    }

    @Test
    fun `whole amounts lose the decimal point`() {
        assertEquals("80", formatAmount(80.0))
        assertEquals("2", formatAmount(2.0))
    }

    @Test
    fun `measures keep one decimal`() {
        assertEquals("0.4", formatMeasure(0.4))
        assertEquals("12.2", formatMeasure(12.1878))
        assertEquals("22.4", formatMeasure(22.4))
    }

    @Test
    fun `whole measures lose the decimal point`() {
        assertEquals("80", formatMeasure(80.0))
    }

    @Test
    fun `a miss on a full library says how many foods there are`() {
        val line = noSavedMatchLine("grams protein powder", librarySize = 27)
        assertTrue(line.contains("27"))
        assertTrue(line.contains("No saved food matches"))
        assertFalse(line.contains("nothing is saved"))
    }

    @Test
    fun `an empty library is the only thing that says nothing is saved`() {
        val line = noSavedMatchLine("oats", librarySize = 0)
        assertTrue(line.contains("nothing is saved yet"))
    }

    @Test
    fun `one food is singular`() {
        assertTrue(noSavedMatchLine("oats", librarySize = 1).contains("1 food."))
    }

    @Test
    fun `numbers do not follow the phone's language`() {
        val previous = java.util.Locale.getDefault()
        try {
            // Where a comma separates decimals, "0,5" is not a number the model can read.
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("0.5", formatAmount(0.5))
            assertEquals("12.2", formatMeasure(12.1878))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `a logged entry keeps its fraction in the unit and in grams`() {
        val entry = LogEntryEntity(
            id = "e",
            mealId = "m",
            targetType = TargetType.FOOD,
            targetId = "f",
            label = "Rolled oats",
            amount = 0.5,
            unit = "cup",
            grams = 40.0,
            nutrients = Nutrients(mapOf(Nutrient.KCAL to 151.4)),
        )
        val text = describeEntry(entry)
        assertTrue(text.contains("0.5 cup"))
        assertTrue(text.contains("40 g"))
        assertTrue(text.contains("151.4 kcal"))
    }

    @Test
    fun `a nutrient value below one is not rounded away`() {
        val text = describeNutrients(Nutrients(mapOf(Nutrient.FAT to 12.1878, Nutrient.KCAL to 0.4)))
        assertTrue(text.contains("fat=12.2"))
        assertTrue(text.contains("kcal=0.4"))
    }

    @Test
    fun `a confident match is returned alone`() {
        val reply = foodLookupReply(
            query = "oats",
            librarySize = 27,
            confident = listOf("- id=1 name=\"Rolled oats\""),
            close = emptyList(),
            usda = null,
        )
        assertEquals("- id=1 name=\"Rolled oats\"", reply)
    }

    @Test
    fun `a suggestion is labelled, and no USDA alternatives are offered`() {
        val reply = foodLookupReply(
            query = "protein powder",
            librarySize = 27,
            confident = emptyList(),
            close = listOf("- id=2 name=\"gramms peach tea protein\""),
            usda = null,
        )
        assertTrue(reply.contains("No saved food matches"))
        assertTrue(reply.contains("27 foods"))
        // The words that carry the meaning, not the constant that holds them.
        assertTrue(reply.contains("not matches"))
        assertTrue(reply.contains("do not log"))
        assertTrue(reply.contains("gramms peach tea protein"))
        assertFalse(reply.contains("USDA"))
    }

    @Test
    fun `a recipe ingredient keeps a fraction`() {
        assertEquals("0.5 cup Rolled oats", ingredientLine(0.5, "cup", "Rolled oats"))
        assertEquals("0.25 cup Greek yogurt", ingredientLine(0.25, "cup", "Greek yogurt"))
        assertEquals("80 g Honey", ingredientLine(80.0, "g", "Honey"))
    }

    @Test
    fun `a saved food is described with what the model logs against`() {
        val food = FoodEntity(
            id = "f1",
            name = "Rolled oats",
            brand = "MALT O MEAL",
            per100g = Nutrients(mapOf(Nutrient.KCAL to 375.0, Nutrient.FIBER to 10.4)),
            estimated = setOf(Nutrient.SOLUBLE_FIBER),
            servings = listOf(Serving("cup", 81.0)),
            source = FoodSource.USDA,
            createdAt = 0,
        )

        val line = describeFood(food)

        assertTrue(line.contains("id=f1"))
        assertTrue(line.contains("name=\"Rolled oats\""))
        assertTrue(line.contains("brand=\"MALT O MEAL\""))
        // The portion is how a "cup of it" becomes grams; losing it breaks logging.
        assertTrue(line.contains("servings=[cup=81g]"))
        assertTrue(line.contains("fiber=10.4"))
        assertTrue(line.contains("source=usda"))
        assertTrue(line.contains("estimated=[soluble_fiber]"))
    }

    @Test
    fun `a USDA candidate is described with its fdc id`() {
        val food = UsdaFood(
            fdcId = 42,
            name = "Milk, whole",
            brand = null,
            per100g = Nutrients(mapOf(Nutrient.KCAL to 60.5)),
            servings = listOf(Serving("cup", 244.0)),
        )

        val line = describeUsda(food)

        assertTrue(line.contains("fdc_id=42"))
        assertTrue(line.contains("servings=[cup=244g]"))
        assertTrue(line.contains("kcal=60.5"))
    }

    @Test
    fun `a real miss falls back to USDA`() {
        val reply = foodLookupReply(
            query = "broccoli",
            librarySize = 27,
            confident = emptyList(),
            close = emptyList(),
            usda = listOf("- fdc_id=1 name=\"Broccoli, raw\""),
        )
        assertTrue(reply.contains("USDA candidates"))
        assertTrue(reply.contains("save_food"))
    }

    @Test
    fun `a real miss with nothing anywhere says so`() {
        val reply = foodLookupReply(
            query = "zorblax",
            librarySize = 27,
            confident = emptyList(),
            close = emptyList(),
            usda = emptyList(),
        )
        assertTrue(reply.contains("USDA has no match either"))
    }
}
