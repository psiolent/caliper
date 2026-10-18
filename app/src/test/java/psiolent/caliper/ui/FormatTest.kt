package psiolent.caliper.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Numbers on screen are read as facts, so rounding must not quietly drop a unit. Anything
 * ten or over used to be truncated, showing 41.9 g of fat as 41.
 */
class FormatTest {

    @Test
    fun `ten and over is rounded, not truncated`() {
        assertEquals("42", round(41.9))
        assertEquals("10", round(10.4))
        assertEquals("1683", round(1682.7))
    }

    @Test
    fun `under ten keeps a decimal`() {
        assertEquals("9.9", round(9.94))
        assertEquals("0.4", round(0.42))
    }

    @Test
    fun `whole numbers print whole`() {
        assertEquals("5", round(5.0))
        assertEquals("10", round(10.0))
        assertEquals("0", round(0.0))
    }
}
