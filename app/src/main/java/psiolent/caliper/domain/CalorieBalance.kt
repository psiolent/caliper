package psiolent.caliper.domain

/**
 * A day's calories: what has been eaten, what the day will burn, and the deficit or
 * surplus set for the day, which moves that burn into an allowance to eat against.
 *
 * [adjustmentKcal] is signed — negative to lose weight (eat under what you burn),
 * positive to gain (eat over it), zero to hold. [leftKcal] goes negative once the
 * allowance is passed and is not clamped here: whether that is said as "over" or shown
 * some other way is the caller's business, not the arithmetic's.
 */
data class CalorieBalance(
    val eatenKcal: Double,
    val burnKcal: Double,
    val adjustmentKcal: Int,
) {
    /** What the day allows you to eat: what it burns, moved by the deficit or surplus. */
    val allowanceKcal: Double get() = burnKcal + adjustmentKcal

    /** How much more can be eaten before the allowance runs out. Negative past it. */
    val leftKcal: Double get() = allowanceKcal - eatenKcal

    /** The slice of the burn a deficit holds back, or zero when there is none. */
    val deficitKcal: Double get() = maxOf(-adjustmentKcal, 0).toDouble()
}
