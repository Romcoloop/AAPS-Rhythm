package app.aaps.plugins.aps.openAPSAutoISF.steps

/**
 * What the steps ask `determine_basal` to do in this run. Every field only makes dosing more careful.
 *
 * [NONE] is "no steps rule active": the run is the same as upstream AutoISF.
 */
data class StepsActivity(
    /** True: no SMB this run (sport guard: movement, the hold after it, or low glucose in the taper). */
    val sportGuard: Boolean = false,
    /** Temp basal may not be above this share of the profile basal; 0.0 = zero temp, 1.0 = no limit. */
    val basalFactor: Double = 1.0,
    /** Lowest allowed minutes between two SMBs; 0 = keep the SMB interval of the settings. */
    val smbIntervalFloorMinutes: Int = 0,
    /** SMB size multiplier from the effort belief, 0.45..1.0; 1.0 = no reduction. */
    val effortSmbFactor: Double = 1.0,
    /** True: no SMB because the morning wake gate is closed (asleep); corrections go by temp basal. */
    val wakeGateClosed: Boolean = false,
    /** One line for the log, empty when nothing is active. */
    val log: String = "",
) {

    val active: Boolean get() = sportGuard || basalFactor < 1.0 || smbIntervalFloorMinutes > 0 || effortSmbFactor < 1.0 || wakeGateClosed

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

    /** Steps in 5 minutes that start the sport guard together with [BURST_STEPS_10]. See [ActivityAfterglow]. */
    const val BURST_STEPS_5 = 400

    /** Steps in 10 minutes that start the sport guard together with [BURST_STEPS_5]. */
    const val BURST_STEPS_10 = 800

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
     * @param afterglow the sport guard of this run (movement, hold, taper), or null when that switch is off
     * @param effort the effort belief of this run, or null when that switch is off
     * @param wakeGate the morning wake gate of this run, or null when that switch is off
     */
    fun decide(
        w: StepWindows?,
        bg: Double,
        lastSmbAgeMinutes: Double,
        smbIntervalEnabled: Boolean,
        afterglow: ActivityAfterglow.Result?,
        effort: EffortActivityBelief.Assessment?,
        wakeGate: WakeGate.Result? = null,
    ): StepsActivity {
        if (w == null) return StepsActivity.NONE
        val guard = afterglow?.smbBlocked == true
        val glowSmbFactor = afterglow?.smbFactor ?: 1.0
        val basalFactor = afterglow?.basalFactor ?: 1.0
        val floor = if (smbIntervalEnabled) smbIntervalFloorMinutes(w, bg, lastSmbAgeMinutes) else 0
        // The stricter of the two, never the product: both ask for the same thing.
        val factor = minOf(effort?.smbFactor ?: 1.0, glowSmbFactor)
        val gateClosed = wakeGate?.blockSmb == true
        val parts = mutableListOf<String>()
        parts.add("steps 5m=${w.steps5} 10m=${w.steps10} 30m=${w.steps30} 60m=${w.steps60} 180m=${w.steps180}")
        if (afterglow != null && afterglow.active) parts.add(afterglow.log)
        if (floor > 0) parts.add("SMB interval at least ${floor}m")
        if (effort != null && effort.smbFactor < 1.0) parts.add("effort x${formatFactor(effort.smbFactor)} (${effort.reasons.joinToString(" ")})")
        if (wakeGate != null && wakeGate.inWindow) parts.add(if (gateClosed) "wake gate: asleep, SMB 0" else "wake gate: awake")
        return StepsActivity(
            sportGuard = guard,
            basalFactor = basalFactor,
            smbIntervalFloorMinutes = floor,
            effortSmbFactor = factor,
            wakeGateClosed = gateClosed,
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
