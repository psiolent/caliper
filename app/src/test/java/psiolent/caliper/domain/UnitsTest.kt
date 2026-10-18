package psiolent.caliper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {

    @Test
    fun `mass units convert exactly`() {
        assertEquals(100.0, resolveGrams(100.0, "g", emptyList())!!, 1e-9)
        assertEquals(1000.0, resolveGrams(1.0, "kg", emptyList())!!, 1e-9)
        assertEquals(28.349523125, resolveGrams(1.0, "oz", emptyList())!!, 1e-9)
        assertEquals(453.59237, resolveGrams(1.0, "lb", emptyList())!!, 1e-9)
    }

    @Test
    fun `a name matching a serving uses that serving's weight`() {
        val servings = listOf(Serving("cup", 80.0))
        assertEquals(80.0, resolveGrams(1.0, "cup", servings)!!, 1e-9)
        assertEquals(40.0, resolveGrams(0.5, "cup", servings)!!, 1e-9)
    }

    @Test
    fun `a volume unit resolves through a volume serving`() {
        val servings = listOf(Serving("cup", 240.0))
        val twoTablespoons = resolveGrams(2.0, "tbsp", servings)!!
        assertEquals(2.0 * 14.78676478125 / 236.5882365 * 240.0, twoTablespoons, 1e-9)
    }

    @Test
    fun `an unknown unit resolves to nothing rather than a guess`() {
        assertNull(resolveGrams(1.0, "splash", emptyList()))
        assertNull(resolveGrams(1.0, "cup", emptyList()))
        assertNull(resolveGrams(1.0, "", emptyList()))
    }

    @Test
    fun `grams scale a per-hundred-gram panel`() {
        val per100g = Nutrients(mapOf(Nutrient.KCAL to 380.0, Nutrient.PROTEIN to 13.0))
        val portion = per100g.forGrams(150.0)
        assertEquals(570.0, portion[Nutrient.KCAL]!!, 1e-9)
        assertEquals(19.5, portion[Nutrient.PROTEIN]!!, 1e-9)
    }

    @Test
    fun `summing panels adds present nutrients and keeps unknown ones out`() {
        val a = Nutrients(mapOf(Nutrient.KCAL to 100.0, Nutrient.FIBER to 2.0))
        val b = Nutrients(mapOf(Nutrient.KCAL to 50.0, Nutrient.PROTEIN to 3.0))
        val total = a + b
        assertEquals(150.0, total[Nutrient.KCAL]!!, 1e-9)
        assertEquals(2.0, total[Nutrient.FIBER]!!, 1e-9)
        assertEquals(3.0, total[Nutrient.PROTEIN]!!, 1e-9)
    }

    @Test
    fun `a nutrient absent from a panel is unknown, not zero`() {
        val panel = Nutrients(mapOf(Nutrient.KCAL to 100.0))
        assertNull(panel[Nutrient.FIBER])
    }
}
