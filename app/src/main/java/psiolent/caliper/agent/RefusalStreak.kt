package psiolent.caliper.agent

/**
 * Counts how long a turn has been stuck: consecutive steps in which the same tool was
 * refused and nothing succeeded.
 *
 * A step counts once however many calls it made, because the model sends several at a
 * time — ten refused calls can be two round trips, and that is not what "stuck ten times"
 * means. Any success in a step resets the count, so a turn making progress is never cut
 * short; so does a different tool, since that is a new problem rather than the same one.
 */
internal class RefusalStreak(private val limit: Int) {

    private var tool: String? = null

    /** How many steps running the current tool has been refused. */
    var steps: Int = 0
        private set

    /** Records one step's outcome. True once the turn should give up. */
    fun record(failedTool: String?, anySucceeded: Boolean): Boolean {
        if (failedTool == null || anySucceeded) {
            tool = null
            steps = 0
            return false
        }
        steps = if (failedTool == tool) steps + 1 else 1
        tool = failedTool
        return steps >= limit
    }
}
