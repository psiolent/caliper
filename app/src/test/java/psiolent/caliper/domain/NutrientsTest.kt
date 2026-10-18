package psiolent.caliper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one calculation the breakdown sheet rests on: what a nutrient's parts leave of
 * the whole. It decides whether a group shows a remainder line, so "nothing known" and
 * "nothing left over" must not be confused — one is a gap, the other is not.
 */
class NutrientsTest {

    private val fats = listOf(
        Nutrient.SATURATED_FAT,
        Nutrient.TRANS_FAT,
        Nutrient.MONOUNSATURATED_FAT,
        Nutrient.POLYUNSATURATED_FAT,
    )

    @Test
    fun `parts short of the whole leave a remainder`() {
        val panel = Nutrients(mapOf(Nutrient.FAT to 10.0, Nutrient.SATURATED_FAT to 6.0))
        assertEquals(4.0, panel.unaccounted(Nutrient.FAT, fats)!!, 0.001)
    }

    @Test
    fun `parts that account for the whole leave nothing over`() {
        val panel = Nutrients(
            mapOf(
                Nutrient.FAT to 10.0,
                Nutrient.SATURATED_FAT to 4.0,
                Nutrient.MONOUNSATURATED_FAT to 4.0,
                Nutrient.POLYUNSATURATED_FAT to 2.0,
            ),
        )
        assertEquals(0.0, panel.unaccounted(Nutrient.FAT, fats)!!, 0.001)
    }

    @Test
    fun `an unknown whole is null rather than nothing left over`() {
        val panel = Nutrients(mapOf(Nutrient.SATURATED_FAT to 6.0))
        assertNull(panel.unaccounted(Nutrient.FAT, fats))
    }

    @Test
    fun `a whole with no parts known is entirely unaccounted`() {
        val panel = Nutrients(mapOf(Nutrient.FAT to 10.0))
        assertEquals(10.0, panel.unaccounted(Nutrient.FAT, fats)!!, 0.001)
    }
}
