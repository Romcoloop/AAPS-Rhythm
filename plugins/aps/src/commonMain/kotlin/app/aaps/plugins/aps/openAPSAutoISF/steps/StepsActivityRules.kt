package app.aaps.plugins.aps.openAPSAutoISF.steps

/**
 * What the steps ask `determine_basal` to do in this run. Every field only makes dosing more careful.
 *
 * [NONE] is "no steps rule active": the run is the same as upstream AutoISF.
 */
data class StepsActivity(
    /** True: no SMB this run and no temp basal above the profile basal. */
    val sportGuard: Boolean = false,
    /** Lowest allowed minutes between two SMBs; 0 = keep the SMB interval of the settings. */
    val smbIntervalFloorMinutes: Int = 0,
    /** SMB size multiplier from the effort belief, 0.45..1.0; 1.0 = no reduction. */
    val effortSmbFactor: Double = 1.0,
    /** One line for the log, empty when nothing is active. */
    val log: String = "",
) {

    val active: Boolean get() = sportGuard || smbIntervalFloorMinutes > 0 || effortSmbFactor < 1.0

    companion object {

        val NONE = StepsActivity()
    }
}

/**
 * The step rules of OpenAPS AIMI (MTR93600/OpenApsAIMI, AGPL-3.0) as pure functions for AutoISF.
 *
 * In AIMI they live in `DetermineBasalAIMI2.kt` (`isSportSafetyCondition`, `calculateSMBInterval`).
 * The thresholds are AIMI's. One difference: AIMI reads some windows as single 5 minute blocks, here
 * every window is a sum (see [StepWindows]), which is what the AIMI comments describe.
 *
 * No step data (null) means no rule fires. Without data we cannot claim a still person, and a rule
 * that fires on a missing sensor would cut insulin for the wrong reason.
 */
object StepsActivityRules {

    /** Steps in 5 minutes that start the sport guard together with [BURST_STEPS_10]. */
    const val BURST_STEPS_5 = 400

    /** Steps in 10 minutes that start the sport guard together with [BURST_STEPS_5]. */
    const val BURST_STEPS_10 = 800

    /** Steps in 30 minutes that count as sustained activity. A 20 minute walk is about 2000 steps. */
    const val SUSTAINED_STEPS_30 = 1200

    /** Steps in 60 minutes that count as sustained activity. */
    const val SUSTAINED_STEPS_60 = 3000

    /** Steps in 180 minutes that count as sustained activity. */
    const val SUSTAINED_STEPS_180 = 4500

    /** Steps in 180 minutes above which SMBs are spaced to at least [INTERVAL_AFTER_ACTIVITY_MIN]. */
    const val INTERVAL_STEPS_180 = 1500

    /** The SMB spacing after activity applies only below this glucose (mg/dl). */
    const val INTERVAL_BG_LIMIT = 120.0

    const val INTERVAL_AFTER_ACTIVITY_MIN = 10

    /** Steps in 5 minutes for the "still moving" SMB spacing, together with [MOVING_STEPS_30]. */
    const val MOVING_STEPS_5 = 100

    const val MOVING_STEPS_30 = 500

    /** The "still moving" spacing needs the last SMB to be at least this old (minutes). */
    const val MOVING_LAST_SMB_MIN = 20

    const val INTERVAL_WHILE_MOVING_MIN = 15

    /**
     * The sport guard: a burst of steps, or sustained activity, stops SMBs.
     *
     * Zero steps in the last 5 minutes release the guard at once, so the loop can resume SMBs right
     * after a walk, for example when a meal is rising.
     */
    fun sportGuard(w: StepWindows?): Boolean {
        if (w == null) return false
        if (w.steps5 == 0) return false
        val burst = w.steps5 >= BURST_STEPS_5 && w.steps10 >= BURST_STEPS_10
        val sustained = w.steps30 >= SUSTAINED_STEPS_30 || w.steps60 >= SUSTAINED_STEPS_60 || w.steps180 >= SUSTAINED_STEPS_180
        return burst || sustained
    }

    /**
     * Lowest minutes between two SMBs after activity, 0 when no rule fires.
     *
     * @param bg glucose in mg/dl
     * @param lastSmbAgeMinutes minutes since the last SMB; a large number when there was none
     */
    fun smbIntervalFloorMinutes(w: StepWindows?, bg: Double, lastSmbAgeMinutes: Double): Int {
        if (w == null) return 0
        var floor = 0
        if (w.steps180 > INTERVAL_STEPS_180 && bg < INTERVAL_BG_LIMIT) floor = INTERVAL_AFTER_ACTIVITY_MIN
        if (w.steps5 > MOVING_STEPS_5 && w.steps30 > MOVING_STEPS_30 && lastSmbAgeMinutes > MOVING_LAST_SMB_MIN) {
            floor = maxOf(floor, INTERVAL_WHILE_MOVING_MIN)
        }
        return floor
    }

    /**
     * Puts the three rules together for one run. Each rule has its own switch.
     *
     * @param effort the effort belief of this run, or null when that switch is off
     */
    fun decide(
        w: StepWindows?,
        bg: Double,
        lastSmbAgeMinutes: Double,
        sportGuardEnabled: Boolean,
        smbIntervalEnabled: Boolean,
        effort: EffortActivityBelief.Assessment?,
    ): StepsActivity {
        if (w == null) return StepsActivity.NONE
        val guard = sportGuardEnabled && sportGuard(w)
        val floor = if (smbIntervalEnabled) smbIntervalFloorMinutes(w, bg, lastSmbAgeMinutes) else 0
        val factor = effort?.smbFactor ?: 1.0
        val parts = mutableListOf<String>()
        parts.add("steps 5m=${w.steps5} 10m=${w.steps10} 30m=${w.steps30} 60m=${w.steps60} 180m=${w.steps180}")
        if (guard) parts.add("sport guard: SMB 0")
        if (floor > 0) parts.add("SMB interval at least ${floor}m")
        if (effort != null && factor < 1.0) parts.add("effort x${formatFactor(factor)} (${effort.reasons.joinToString(" ")})")
        return StepsActivity(
            sportGuard = guard,
            smbIntervalFloorMinutes = floor,
            effortSmbFactor = factor,
            log = parts.joinToString("; "),
        )
    }

    /** Two decimals without platform formatting, for the log only. */
    internal fun formatFactor(value: Double): String {
        val hundredths = (value * 100 + 0.5).toInt()
        val whole = hundredths / 100
        val rest = hundredths % 100
        return "$whole.${if (rest < 10) "0$rest" else "$rest"}"
    }
}
