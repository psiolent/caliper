package psiolent.caliper.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the point at which a turn stops being slow and starts being stuck. Getting this
 * too eager would cut off a turn that is recovering; too lax and the same refused call
 * runs until the whole budget is gone.
 */
class RefusalStreakTest {

    @Test
    fun `ten refusals of one tool in a row give up`() {
        val streak = RefusalStreak(limit = 10)
        repeat(9) { assertFalse(streak.record("save_food", anySucceeded = false)) }
        assertTrue(streak.record("save_food", anySucceeded = false))
    }

    @Test
    fun `nine is not enough`() {
        val streak = RefusalStreak(limit = 10)
        repeat(9) { streak.record("save_food", anySucceeded = false) }
        assertEquals(9, streak.steps)
    }

    @Test
    fun `a success resets the count`() {
        val streak = RefusalStreak(limit = 10)
        repeat(9) { streak.record("save_food", anySucceeded = false) }
        assertFalse(streak.record(null, anySucceeded = true))
        assertEquals(0, streak.steps)
        repeat(9) { assertFalse(streak.record("save_food", anySucceeded = false)) }
    }

    @Test
    fun `a step that partly succeeded is progress, not a strike`() {
        val streak = RefusalStreak(limit = 10)
        repeat(9) { streak.record("save_food", anySucceeded = false) }
        assertFalse(streak.record("save_food", anySucceeded = true))
        assertEquals(0, streak.steps)
    }

    @Test
    fun `a different tool starts the count again`() {
        val streak = RefusalStreak(limit = 10)
        repeat(9) { streak.record("save_food", anySucceeded = false) }
        assertFalse(streak.record("log", anySucceeded = false))
        assertEquals(1, streak.steps)
    }
}
