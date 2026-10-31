package app.aaps.plugins.aps.openAPSAutoISF.steps

/**
 * Steps taken in the last 5, 10, 15, 30, 60 and 180 minutes, all counted up to the same moment.
 *
 * Every window is a **sum** over that many minutes. This is what the phone step counter writes
 * ([StepBuckets]) and what the AutoISF rules read. A watch build of AAPS fills the same database
 * columns a little differently (its 10 and 15 minute values are single 5 minute blocks, not sums),
 * so with watch data the 10 and 15 minute rules are more careful, never more aggressive.
 */
data class StepWindows(
    val steps5: Int,
    val steps10: Int,
    val steps15: Int,
    val steps30: Int,
    val steps60: Int,
    val steps180: Int,
    /** Milliseconds since the epoch, the end of all the windows. */
    val timestamp: Long,
) {

    /** Steps per minute over the 5 minute window. */
    val rate5: Double get() = steps5 / 5.0

    /** Steps per minute over the 15 minute window. */
    val rate15: Double get() = steps15 / 15.0

    /** Steps per minute over the 60 minute window. */
    val rate60: Double get() = steps60 / 60.0
}
