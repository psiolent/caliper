package psiolent.caliper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
