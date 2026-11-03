package app.aaps.plugins.aps.openAPSAutoISF.steps

/**
 * The sport guard with a slow way back: what the dosing does while the person moves, in the first
 * minutes after the movement stops, and while the dosing returns to full strength.
 *
 * The old guard let go at once when there were 0 steps in 5 minutes, so the dosing jumped back to
 * 100 %. Now the way back is a ramp. The size of the ramp follows how much the person walked.
 *
 * Levels (the steps are sums, see [StepWindows]):
 *  - [Level.MILD]:  2500 steps in 60 min.
 *  - [Level.GUARD]: 2000 steps in 30 min, 4000 in 60 min, or a burst (400 in 5 min and 800 in 10 min).
 *  - [Level.LONG]:  6000 steps in 60 min, or 7000 in 180 min.
 *
 * Phases:
 *  - [Phase.MOVING]: temp basal 0 %. SMB 0 (MILD: SMB x0.8).
 *  - [Phase.HOLD]: [HOLD_MIN] minutes after the movement, only GUARD and LONG. Temp basal 0 %, SMB 0.
 *  - [Phase.TAPER]: SMB and temp basal go from the start factor back to 100 % in a straight line.
 *
 * Exceptions in the taper: when glucose is high and still rising, the taper is half as long. When
 * glucose is low or falls fast, SMB stays 0 (the basal still follows the ramp).
 *
 * The level is kept in [Memory] from the moment of the movement. The step windows are not used for
 * the taper, because the 60 and 180 minute sums stay high for hours after a walk.
 *
 * Pure: no clock, no I/O. The caller keeps [Memory] between runs and passes the time in.
 */
object ActivityAfterglow {

    enum class Level { NONE, MILD, GUARD, LONG }

    enum class Phase { NONE, MOVING, HOLD, TAPER }

    /** Steps in 60 minutes for [Level.MILD]. */
    const val MILD_STEPS_60 = 2500

    /** Steps in 30 minutes for [Level.GUARD]. */
    const val GUARD_STEPS_30 = 2000

    /** Steps in 60 minutes for [Level.GUARD]. */
    const val GUARD_STEPS_60 = 4000

    /** Steps in 60 minutes for [Level.LONG]. */
    const val LONG_STEPS_60 = 6000

    /** Steps in 180 minutes for [Level.LONG]. */
    const val LONG_STEPS_180 = 7000

    /** Steps in the last 5 minutes that count as "moving now". */
    const val MOVING_STEPS_5 = StepsActivityRules.MOVING_STEPS_5

    /** SMB size while moving at [Level.MILD]. */
    const val MILD_SMB_FACTOR = 0.8

    /** Minutes with no basal and no SMB after the movement, for GUARD and LONG. */
    const val HOLD_MIN = 10.0

    /** Glucose (mg/dl) above which a rising glucose halves the taper. 10 mmol/l. */
    const val HIGH_BG = 180.0

    /** Glucose (mg/dl) under which SMB stays 0 in the taper. 4.5 mmol/l. */
    const val LOW_BG = 81.0

    /** Delta (mg/dl in 5 min) at or under which glucose falls fast. About -0.3 mmol/l. */
    const val FAST_FALL_DELTA = -5.0

    /** Memory between runs, kept by the caller. */
    data class Memory(
        val level: Level = Level.NONE,
        /** Time of the last run that was still moving, 0 = never. */
        val lastMovingMs: Long = 0L,
    )

    data class Result(
        val level: Level,
        val phase: Phase,
        /** SMB multiplier 0.0..1.0 (the taper or [MILD_SMB_FACTOR]); 1.0 = no reduction. */
        val smbFactor: Double,
        /** True: no SMB at all in this run. */
        val smbBlocked: Boolean,
        /** Temp basal may not be above this share of the profile basal; 0.0 = zero temp, 1.0 = free. */
        val basalFactor: Double,
        val log: String,
    ) {

        val active: Boolean get() = phase != Phase.NONE

        companion object {

            val NONE = Result(Level.NONE, Phase.NONE, 1.0, false, 1.0, "")
        }
    }

    private fun startFactor(level: Level): Double = when (level) {
        Level.MILD -> MILD_SMB_FACTOR
        else       -> 0.5
    }

    private fun holdMin(level: Level): Double = if (level == Level.MILD || level == Level.NONE) 0.0 else HOLD_MIN

    /** Minutes of the taper (after the hold). */
    fun taperMin(level: Level): Double = when (level) {
        Level.NONE  -> 0.0
        Level.MILD  -> 30.0
        Level.GUARD -> 60.0
        Level.LONG  -> 120.0
    }

    /** The level the step sums show right now. */
    fun classify(w: StepWindows): Level = when {
        w.steps60 >= LONG_STEPS_60 || w.steps180 >= LONG_STEPS_180                                                        -> Level.LONG
        w.steps30 >= GUARD_STEPS_30 || w.steps60 >= GUARD_STEPS_60 ||
            (w.steps5 >= StepsActivityRules.BURST_STEPS_5 && w.steps10 >= StepsActivityRules.BURST_STEPS_10)                -> Level.GUARD
        w.steps60 >= MILD_STEPS_60                                                                                         -> Level.MILD
        else                                                                                                               -> Level.NONE
    }

    /**
     * @param bg glucose in mg/dl
     * @param delta glucose change in mg/dl per 5 minutes
     */
    fun assess(w: StepWindows, bg: Double, delta: Double, nowMs: Long, prior: Memory): Pair<Result, Memory> {
        val priorAgeMin = if (prior.lastMovingMs > 0L) ((nowMs - prior.lastMovingMs) / 60_000.0).coerceAtLeast(0.0) else Double.MAX_VALUE
        val priorLive = prior.level != Level.NONE && priorAgeMin <= holdMin(prior.level) + taperMin(prior.level)

        // Every start, new or after a stop, needs a real pace over 15 minutes (the same 25 steps/min as
        // the effort belief) and 100 steps in the last 5 minutes. A few steps at home do not start it,
        // also when the 60 and 180 minute sums are still high from a walk long ago. A new start needs
        // a level from the sums as well.
        val level = classify(w)
        val moving = w.steps5 >= MOVING_STEPS_5 &&
            w.rate15 >= EffortActivityBelief.ACTIVE_STEPS_PER_MIN &&
            (priorLive || level != Level.NONE)
        val memory = when {
            moving -> Memory(level = if (priorLive) maxOf(prior.level, level) else level, lastMovingMs = nowMs)
            priorLive -> prior
            else -> Memory()
        }
        if (memory.level == Level.NONE) return Result.NONE to memory

        val ageMin = if (moving) 0.0 else priorAgeMin
        val name = memory.level.name
        val result = when {
            moving -> {
                val mild = memory.level == Level.MILD
                Result(
                    memory.level, Phase.MOVING, if (mild) MILD_SMB_FACTOR else 0.0, !mild, 0.0,
                    "afterglow $name: moving, basal 0%, ${if (mild) "SMB x$MILD_SMB_FACTOR" else "SMB 0"}",
                )
            }
            ageMin < holdMin(memory.level) -> Result(
                memory.level, Phase.HOLD, 0.0, true, 0.0,
                "afterglow $name: hold ${ageMin.toInt()}/${holdMin(memory.level).toInt()}m, basal 0%, SMB 0",
            )
            else -> taper(memory.level, ageMin - holdMin(memory.level), bg, delta)
        }
        return result to memory
    }

    private fun taper(level: Level, minutesIn: Double, bg: Double, delta: Double): Result {
        val highAndRising = bg > HIGH_BG && delta > 0.0
        val lengthMin = taperMin(level) * if (highAndRising) 0.5 else 1.0
        val share = (minutesIn / lengthMin).coerceIn(0.0, 1.0)
        val start = startFactor(level)
        val factor = start + (1.0 - start) * share
        val lowOrFalling = bg < LOW_BG || delta <= FAST_FALL_DELTA
        if (share >= 1.0 && !lowOrFalling) return Result.NONE
        val parts = mutableListOf("afterglow ${level.name}: taper ${minutesIn.toInt()}/${lengthMin.toInt()}m, x${StepsActivityRules.formatFactor(factor)}")
        if (highAndRising) parts.add("high and rising, half length")
        if (lowOrFalling) parts.add("low or falling, SMB 0")
        return Result(level, Phase.TAPER, factor, lowOrFalling, factor, parts.joinToString(", "))
    }
}
