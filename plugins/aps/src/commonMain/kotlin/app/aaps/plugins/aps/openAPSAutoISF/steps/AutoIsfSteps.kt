package app.aaps.plugins.aps.openAPSAutoISF.steps

import app.aaps.core.data.model.SC
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * The steps side of the AutoISF plugin: the phone step counter, the steps in the database and the
 * effort memory between runs.
 *
 * - [tick] runs once a minute while the plugin is on. It starts or stops the phone step counter
 *   after the switch and stores the counted steps, in the same six-row form the watch uses, so the
 *   automation trigger "Steps count" and the graph see them too.
 * - [decide] is called once per loop run. It reads the newest steps (phone first, then the database,
 *   so a watch works as well), runs the effort belief and puts the rules together.
 *
 * Every rule has its own switch and all are off by default. Off = the run is the same as upstream.
 */
class AutoIsfSteps(
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val phone: PhoneStepCounter,
    private val aapsLogger: AAPSLogger,
) {

    private var effortMemory = EffortActivityBelief.Memory()
    private var lastStoredMinute = 0L

    /** Last effort assessment, for the log and the tests. */
    var lastEffort: EffortActivityBelief.Assessment? = null
        private set

    val phoneSourceOn: Boolean get() = preferences.get(BooleanKey.ApsAutoIsfStepsPhoneSource)

    /** True when any step rule is switched on, so a missing step source is worth a log line. */
    val anyRuleOn: Boolean
        get() = preferences.get(BooleanKey.ApsAutoIsfStepsSportGuard) ||
            preferences.get(BooleanKey.ApsAutoIsfStepsSmbInterval) ||
            preferences.get(BooleanKey.ApsAutoIsfStepsEffortProtection)

    /** Starts or stops the phone step counter after the switch, and stores the steps once a minute. */
    suspend fun tick(nowMs: Long) {
        if (!phoneSourceOn || !phone.available) {
            phone.stop()
            return
        }
        phone.start()
        val minute = nowMs / StepBuckets.MINUTE_MS
        if (minute == lastStoredMinute) return
        val w = phone.windows(nowMs) ?: return
        lastStoredMinute = minute
        persistenceLayer.insertOrUpdateStepsCounts(rows(w, phone.deviceName))
    }

    /** Stops the phone step counter, for example when the plugin is switched off. */
    fun stop() {
        phone.stop()
    }

    /**
     * The newest steps for this run: the phone's own count when it is on and has data, otherwise the
     * newest database row of the last [MAX_AGE_MS] (a watch, or the rows this class stored). Null
     * when there is nothing recent enough.
     */
    suspend fun windows(nowMs: Long): StepWindows? {
        if (phoneSourceOn && phone.available) phone.windows(nowMs)?.let { return it }
        val rows = persistenceLayer.getStepsCountFromTimeToTime(nowMs - MAX_AGE_MS, nowMs)
        // Only the 180 minute row carries all six windows; the shorter rows leave the rest at 0.
        val row = rows.filter { it.duration == 180 * 60 * 1000L }.maxByOrNull { it.timestamp } ?: return null
        return StepWindows(
            steps5 = row.steps5min,
            steps10 = row.steps10min,
            steps15 = row.steps15min,
            steps30 = row.steps30min,
            steps60 = row.steps60min,
            steps180 = row.steps180min,
            timestamp = row.timestamp,
        )
    }

    /**
     * The step rules for one run.
     *
     * @param bg glucose in mg/dl
     * @param lastBolusTimeMs time of the last bolus or SMB, 0 when there was none
     */
    suspend fun decide(nowMs: Long, bg: Double, lastBolusTimeMs: Long): StepsActivity {
        val sportGuard = preferences.get(BooleanKey.ApsAutoIsfStepsSportGuard)
        val smbInterval = preferences.get(BooleanKey.ApsAutoIsfStepsSmbInterval)
        val effortOn = preferences.get(BooleanKey.ApsAutoIsfStepsEffortProtection)
        if (!sportGuard && !smbInterval && !effortOn) {
            lastEffort = null
            return StepsActivity.NONE
        }
        val w = windows(nowMs)
        if (w == null) {
            lastEffort = null
            aapsLogger.debug(LTag.APS, "AutoISF steps: a step rule is on but there is no step data")
            return StepsActivity.NONE
        }
        val effort = if (effortOn) {
            val (assessment, memory) = EffortActivityBelief.assess(w, nowMs, effortMemory)
            effortMemory = memory
            assessment
        } else {
            null
        }
        lastEffort = effort
        val lastSmbAgeMinutes = if (lastBolusTimeMs > 0L) (nowMs - lastBolusTimeMs) / 60_000.0 else Double.MAX_VALUE
        return StepsActivityRules.decide(w, bg, lastSmbAgeMinutes, sportGuard, smbInterval, effort)
    }

    companion object {

        /** Steps older than this are not used for dosing. */
        const val MAX_AGE_MS = 15 * 60 * 1000L

        /**
         * The six rows the watch writes for one sample: one per window length, each with the windows
         * up to its own length filled and the longer ones at 0. The automation trigger picks the row
         * by its duration, so all six are needed.
         */
        fun rows(w: StepWindows, device: String): List<SC> {
            val lengths = listOf(5, 10, 15, 30, 60, 180)
            return lengths.map { minutes ->
                SC(
                    duration = minutes * 60 * 1000L,
                    timestamp = w.timestamp,
                    steps5min = w.steps5,
                    steps10min = if (minutes >= 10) w.steps10 else 0,
                    steps15min = if (minutes >= 15) w.steps15 else 0,
                    steps30min = if (minutes >= 30) w.steps30 else 0,
                    steps60min = if (minutes >= 60) w.steps60 else 0,
                    steps180min = if (minutes >= 180) w.steps180 else 0,
                    device = device,
                )
            }
        }
    }
}
