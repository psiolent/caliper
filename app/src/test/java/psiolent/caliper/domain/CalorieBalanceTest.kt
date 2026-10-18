package psiolent.caliper.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The day's calorie arithmetic, and in particular its sign convention: eating 500 under
 * what you burn is a deficit, so the setting is negative and the allowance drops below
 * the burn. Getting that backwards would silently invert the whole screen.
 */
class CalorieBalanceTest {

    @Test
    fun `a deficit lowers the allowance below the burn`() {
        val balance = CalorieBalance(eatenKcal = 1134.0, burnKcal = 1991.0, adjustmentKcal = -500)
        assertEquals(1491.0, balance.allowanceKcal, 0.001)
        assertEquals(357.0, balance.leftKcal, 0.001)
        assertEquals(500.0, balance.deficitKcal, 0.001)
    }

    @Test
    fun `a surplus raises the allowance above the burn`() {
        val balance = CalorieBalance(eatenKcal = 1000.0, burnKcal = 2000.0, adjustmentKcal = 500)
        assertEquals(2500.0, balance.allowanceKcal, 0.001)
        assertEquals(1500.0, balance.leftKcal, 0.001)
        assertEquals(0.0, balance.deficitKcal, 0.001)
    }

    @Test
    fun `holding leaves the allowance at the burn`() {
        val balance = CalorieBalance(eatenKcal = 1134.0, burnKcal = 1991.0, adjustmentKcal = 0)
        assertEquals(1991.0, balance.allowanceKcal, 0.001)
        assertEquals(857.0, balance.leftKcal, 0.001)
        assertEquals(0.0, balance.deficitKcal, 0.001)
    }

    @Test
    fun `eating past the allowance leaves a negative remainder, not zero`() {
        val balance = CalorieBalance(eatenKcal = 2300.0, burnKcal = 1991.0, adjustmentKcal = 0)
        assertEquals(-309.0, balance.leftKcal, 0.001)
    }
}
