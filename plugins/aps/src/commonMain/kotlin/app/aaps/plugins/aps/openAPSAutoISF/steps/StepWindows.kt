package app.aaps.plugins.aps.openAPSAutoISF.steps

/** Where the step numbers of one run come from. The switch "Use the phone as step source" picks one. */
enum class StepSource(val label: String) {

    PHONE("phone"),
    WATCH("watch"),
}

/**
 * Steps taken in the last 5, 10, 15, 30, 60 and 180 minutes, all counted up to the same moment.
 *
 * Every window is a **sum** over that many minutes. The phone step counter ([StepBuckets]) writes sums.
 * A watch build of AAPS fills the same database columns differently: its 10 and 15 minute values are
 * single 5 minute blocks (the block 5 to 10 and the block 10 to 15 minutes ago), and its 5 minute
 * value is the last finished block, so it can be up to 5 minutes old. [fromWatch] turns the watch
 * values into sums. After that the AutoISF rules read both sources the same way.
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
    val source: StepSource = StepSource.PHONE,
) {

    /** Steps per minute over the 5 minute window. */
    val rate5: Double get() = steps5 / 5.0

    /** Steps per minute over the 15 minute window. */
    val rate15: Double get() = steps15 / 15.0

    /** Steps per minute over the 60 minute window. */
    val rate60: Double get() = steps60 / 60.0

    companion object {

        /**
         * Windows from the values a watch writes: [block5] is the newest 5 minute block, [block10] the
         * block before it and [block15] the block before that. The 30, 60 and 180 minute values of the
         * watch are already sums.
         */
        fun fromWatch(
            block5: Int,
            block10: Int,
            block15: Int,
            steps30: Int,
            steps60: Int,
            steps180: Int,
            timestamp: Long,
        ) = StepWindows(
            steps5 = block5,
            steps10 = block5 + block10,
            steps15 = block5 + block10 + block15,
            steps30 = steps30,
            steps60 = steps60,
            steps180 = steps180,
            timestamp = timestamp,
            source = StepSource.WATCH,
        )
    }
}
