package app.aaps.plugins.aps.openAPSAutoISF.steps

/**
 * Morning wake gate: no SMB until the person is up.
 *
 * Steps cannot tell a dawn rise from breakfast, but they can tell sleep from being awake. While asleep
 * a rising glucose gets only temp basal (as with SMB off), which fits a slow dawn rise better than
 * SMBs. Once awake, SMBs work as usual.
 *
 * - The gate watches between [startMinute] and [endMinute] of the local day.
 * - "Awake" is a **latch**: the first time the 60 minute window holds at least [wakeSteps] steps,
 *   the gate opens and stays open until [endMinute], also while the person sits still at breakfast.
 * - Outside the window the latch is reset for the next night.
 * - No step data means no block (fail open): a stopped sensor must not switch SMBs off.
 *
 * Pure: the caller keeps [State] between runs.
 */
object WakeGate {

    /** Minutes of the local day, 0..1439. */
    data class State(
        /** True once the person is up this morning; reset outside the window. */
        val awake: Boolean = false,
    )

    data class Result(
        val state: State,
        /** True: this run gives no SMB. */
        val blockSmb: Boolean,
        /** True while the local time is inside the gate window. */
        val inWindow: Boolean,
    )

    fun evaluate(
        prior: State,
        windows: StepWindows?,
        localMinuteOfDay: Int,
        wakeSteps: Int,
        startMinute: Int,
        endMinute: Int,
    ): Result {
        val inWindow = startMinute < endMinute && localMinuteOfDay >= startMinute && localMinuteOfDay < endMinute
        if (!inWindow) return Result(State(awake = false), blockSmb = false, inWindow = false)
        if (windows == null) return Result(prior, blockSmb = false, inWindow = true)
        val awake = prior.awake || windows.steps60 >= wakeSteps
        return Result(State(awake = awake), blockSmb = !awake, inWindow = true)
    }
}
