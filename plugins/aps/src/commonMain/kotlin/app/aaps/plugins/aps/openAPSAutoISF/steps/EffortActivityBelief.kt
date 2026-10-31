package app.aaps.plugins.aps.openAPSAutoISF.steps

import kotlin.math.max

/**
 * Effort belief from steps: how hard the person is moving now, and for how long after a walk the
 * SMBs should still be smaller.
 *
 * Ported from OpenAPS AIMI (MTR93600/OpenApsAIMI, `activity/EffortActivityBelief.kt`, AGPL-3.0).
 * The AIMI version also fuses heart rate and HRV; this one uses steps only, so the heart rate parts
 * are left out. The step maths and the numbers are the same.
 *
 * How it works:
 *  - **Reduction only**: [Assessment.smbFactor] is at most 1.0. It can only make SMBs smaller.
 *  - **Onset is fast, memory is slow**: the 5 minute window alone may open the current reduction
 *    ([State.ACTIVE]), so a real walk is not seen late. But only the 15 minute window may arm the
 *    memory. A short burst (a walk to the kitchen) must not start hours of protection.
 *  - **Protection follows the effort**: after the walk ends, [State.RECENT_EFFORT] fades out over
 *    30 minutes for a short walk, up to 120 minutes for a long one.
 *  - **Pure**: no clock, no I/O. The caller keeps [Memory] between runs and passes the time in.
 */
object EffortActivityBelief {

    enum class State { IDLE, ACTIVE, RECENT_EFFORT }

    /** Steps per minute of a brisk walk, the reference for full exertion. */
    const val WALK_STEPS_PER_MIN = 40.0

    /** Lowest steps per minute that count as "moving". */
    const val ACTIVE_STEPS_PER_MIN = 25.0

    /** Longest protection after an effort, earned by [EFFORT_MINUTES_CAP] minutes of walking. */
    const val EFFORT_MEMORY_MAX_MIN = 120.0

    /** Shortest protection after an effort. */
    const val EFFORT_MEMORY_BASE_MIN = 30.0

    /** Extra protection minutes per minute of effort. */
    const val EFFORT_MEMORY_PER_EFFORT_MIN = 3.0

    /** Effort minutes given to the first confirmed run (one loop cycle). */
    const val EFFORT_SEED_MIN = 5.0

    /** Longest gap between two confirmed runs that still counts as the same effort. */
    const val EFFORT_GAP_MAX_MIN = 15.0

    /** Effort minutes stop growing here; this already earns [EFFORT_MEMORY_MAX_MIN]. */
    const val EFFORT_MINUTES_CAP = 30.0

    /** Strongest SMB reduction at full exertion. */
    const val SMB_FLOOR = 0.45

    /** Memory between runs, kept by the caller. */
    data class Memory(
        val lastEffortMs: Long = 0L,
        /** Steps per minute of the 15 minute window that armed the memory, never the 5 minute burst. */
        val peakStepsPerMin: Double = 0.0,
        /** How long the confirmed effort has lasted, in minutes. */
        val effortMinutes: Double = 0.0,
    )

    data class Assessment(
        val state: State,
        val minutesSinceEffort: Double,
        /** SMB multiplier in [SMB_FLOOR]..1.0; 1.0 = no reduction. */
        val smbFactor: Double,
        val reasons: List<String>,
    )

    fun assess(w: StepWindows, nowMs: Long, prior: Memory): Pair<Assessment, Memory> {
        val reasons = mutableListOf<String>()

        val stepRate = max(w.rate5, w.rate15)
        val sustainedRate = w.rate60

        // Onset: the 5 minute window alone may open the reduction, for as long as the movement lasts.
        val exertingNow = stepRate >= ACTIVE_STEPS_PER_MIN
        // Confirmation: only the 15 minute window may arm the memory. In AIMI's field data a short
        // burst had at most 18 steps/min over 15 minutes, every real walk 28 to 89.
        val sustainedEffortNow = w.rate15 >= ACTIVE_STEPS_PER_MIN
        if (exertingNow) reasons.add("steps=${stepRate.toInt()}/min(5=${w.rate5.toInt()},15=${w.rate15.toInt()})")
        if (exertingNow && !sustainedEffortNow) reasons.add("burst_unconfirmed")

        val stepIntensity = when {
            stepRate >= WALK_STEPS_PER_MIN -> (0.6 + 0.4 * ((stepRate - WALK_STEPS_PER_MIN) / WALK_STEPS_PER_MIN)).coerceIn(0.6, 1.0)
            stepRate >= ACTIVE_STEPS_PER_MIN -> 0.35 + 0.25 * ((stepRate - ACTIVE_STEPS_PER_MIN) / (WALK_STEPS_PER_MIN - ACTIVE_STEPS_PER_MIN))
            else -> 0.0
        }.coerceIn(0.0, 1.0)
        val sustainedBonus = (sustainedRate / WALK_STEPS_PER_MIN).coerceIn(0.0, 0.30)

        // Memory: only a confirmed effort stamps the time, the 15 minute rate and the effort length.
        val priorHorizonMin = memoryHorizonMin(prior.effortMinutes)
        val priorAgeMin = if (prior.lastEffortMs > 0L) ((nowMs - prior.lastEffortMs) / 60_000.0).coerceAtLeast(0.0) else Double.MAX_VALUE
        val priorExpired = prior.lastEffortMs == 0L || priorAgeMin > priorHorizonMin
        val memory = if (sustainedEffortNow) {
            val sameEffort = !priorExpired && priorAgeMin <= EFFORT_GAP_MAX_MIN
            val effortMinutes = if (sameEffort) (safeMinutes(prior.effortMinutes) + priorAgeMin).coerceAtMost(EFFORT_MINUTES_CAP) else EFFORT_SEED_MIN
            val basePeak = if (sameEffort) prior.peakStepsPerMin else 0.0
            Memory(lastEffortMs = nowMs, peakStepsPerMin = max(basePeak, w.rate15), effortMinutes = effortMinutes)
        } else {
            prior
        }
        val minutesSinceEffort = if (memory.lastEffortMs > 0L) ((nowMs - memory.lastEffortMs) / 60_000.0).coerceAtLeast(0.0) else Double.MAX_VALUE
        val horizonMin = memoryHorizonMin(memory.effortMinutes)

        val state = when {
            exertingNow                     -> State.ACTIVE
            minutesSinceEffort <= horizonMin -> State.RECENT_EFFORT
            else                            -> State.IDLE
        }
        val recency = when (state) {
            State.ACTIVE        -> 1.0
            State.RECENT_EFFORT -> (1.0 - minutesSinceEffort / horizonMin).coerceIn(0.0, 1.0)
            State.IDLE          -> 0.0
        }

        val exertionStrength = when (state) {
            State.ACTIVE        -> (stepIntensity + sustainedBonus).coerceIn(0.0, 1.0)
            State.RECENT_EFFORT -> {
                // peakStepsPerMin is a confirmed 15 minute rate, so this is at least 0.625 for a memory
                // armed here. The 0.30 floor only matters for a memory restored by the caller.
                val peakScale = (memory.peakStepsPerMin / WALK_STEPS_PER_MIN).coerceIn(0.30, 1.0)
                (recency * peakScale).coerceIn(0.0, 1.0)
            }
            State.IDLE          -> 0.0
        }

        val smbFactor = clampFactor(1.0 - (1.0 - SMB_FLOOR) * exertionStrength, SMB_FLOOR)
        reasons.add("${state.name} since=${if (minutesSinceEffort == Double.MAX_VALUE) "-" else "${minutesSinceEffort.toInt()}m"}")

        return Assessment(state, minutesSinceEffort, smbFactor, reasons) to memory
    }

    /** Protection minutes after an effort, growing with how long the effort lasted. */
    fun memoryHorizonMin(effortMinutes: Double): Double =
        (EFFORT_MEMORY_BASE_MIN + EFFORT_MEMORY_PER_EFFORT_MIN * safeMinutes(effortMinutes))
            .coerceIn(EFFORT_MEMORY_BASE_MIN, EFFORT_MEMORY_MAX_MIN)

    private fun safeMinutes(minutes: Double): Double =
        if (minutes.isFinite()) minutes.coerceIn(0.0, EFFORT_MINUTES_CAP) else 0.0

    /** A value that is not a number means "unknown" and must fail open at 1.0, never reach the dose. */
    private fun clampFactor(value: Double, floor: Double): Double =
        if (value.isFinite()) value.coerceIn(floor, 1.0) else 1.0
}
