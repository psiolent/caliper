package psiolent.caliper.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which tracker's activity figure the day uses. This decides a number the user compares
 * against the app on their wrist, so the rule is pinned rather than left to whichever
 * aggregate Health Connect happens to return.
 */
class BurnSelectionTest {

    @Test
    fun `the app reporting the most is used, not the aggregate`() {
        val selection = selectBurn(
            perOriginKcal = mapOf("com.ouraring.oura" to 735.9, "com.android.healthconnect.phone" to 384.8),
            aggregateActive = 167.0,
            aggregateTotal = 1849.7,
        )
        assertEquals(735.9, selection.activeKcal!!, 0.01)
        assertEquals("com.ouraring.oura", selection.source)
    }

    @Test
    fun `the total moves with the active figure so resting stays put`() {
        val selection = selectBurn(
            perOriginKcal = mapOf("com.ouraring.oura" to 735.9),
            aggregateActive = 167.0,
            aggregateTotal = 1849.7,
        )
        // 1849.7 - 167.0 + 735.9, and 2418.6 - 735.9 = 1682.7, the same resting burn.
        val total = selection.totalKcal!!
        assertEquals(2418.6, total, 0.01)
        assertEquals(1682.7, total - selection.activeKcal!!, 0.01)
    }

    @Test
    fun `with no records it falls back to the aggregate`() {
        val selection = selectBurn(
            perOriginKcal = emptyMap(),
            aggregateActive = 167.0,
            aggregateTotal = 1849.7,
        )
        assertEquals(167.0, selection.activeKcal!!, 0.01)
        assertEquals(1849.7, selection.totalKcal!!, 0.01)
        assertNull(selection.source)
    }

    @Test
    fun `an app reporting nothing is not chosen`() {
        val selection = selectBurn(
            perOriginKcal = mapOf("com.example.idle" to 0.0),
            aggregateActive = 167.0,
            aggregateTotal = 1849.7,
        )
        assertEquals(167.0, selection.activeKcal!!, 0.01)
        assertNull(selection.source)
    }

    @Test
    fun `no total recorded means no total`() {
        val selection = selectBurn(
            perOriginKcal = mapOf("com.ouraring.oura" to 735.9),
            aggregateActive = 167.0,
            aggregateTotal = null,
        )
        assertNull(selection.totalKcal)
        assertEquals(735.9, selection.activeKcal!!, 0.01)
    }
}
