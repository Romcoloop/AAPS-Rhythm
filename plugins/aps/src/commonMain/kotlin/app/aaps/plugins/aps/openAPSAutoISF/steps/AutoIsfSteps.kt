package app.aaps.plugins.aps.openAPSAutoISF.steps

import app.aaps.core.data.model.SC
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * The steps side of the AutoISF plugin: the phone step counter, the steps in the database and the
 * effort memory between runs.
 *
 * - [tick] runs once a minute while the plugin is on. It starts or stops the phone step counter
 *   after the switch and stores the counted steps, in the same six-row form the watch uses, so the
 *   automation trigger "Steps count" and the graph see them too.
 * - [decide] is called once per loop run. It reads the newest steps from ONE source, picked by the
 *   switch (on = the phone, off = the watch; see [windows]), runs the sport guard
 *   ([ActivityAfterglow]), the effort belief and the morning wake gate and puts the rules together.
 *
 * Every rule has its own switch and all are off by default. Off = the run is the same as upstream.
 */
class AutoIsfSteps(
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val phone: PhoneStepCounter,
    private val aapsLogger: AAPSLogger,
    /** Minute of the local day, 0..1439, for the morning wake gate. */
    private val localMinuteOfDay: () -> Int,
) {

    private var effortMemory = EffortActivityBelief.Memory()
    private var afterglowMemory = ActivityAfterglow.Memory()
    private var wakeState = WakeGate.State()
    private var lastStoredMinute = 0L

    /** Last effort assessment, for the log and the tests. */
    var lastEffort: EffortActivityBelief.Assessment? = null
        private set

    val phoneSourceOn: Boolean get() = preferences.get(BooleanKey.ApsAutoIsfStepsPhoneSource)

    /** True when any step rule is switched on, so a missing step source is worth a log line. */
    val anyRuleOn: Boolean
        get() = preferences.get(BooleanKey.ApsAutoIsfStepsSportGuard) ||
            preferences.get(BooleanKey.ApsAutoIsfStepsSmbInterval) ||
            preferences.get(BooleanKey.ApsAutoIsfStepsEffortProtection) ||
            preferences.get(BooleanKey.ApsAutoIsfStepsWakeGate)

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
     * The newest steps for this run, from the ONE source the switch picks. There is no fallback to
     * the other source, so the numbers always come from the place the log line names.
     *
     * - Switch on: the phone's own count. Null when the phone has no step sensor, no permission, or
     *   no sensor event yet.
     * - Switch off: the newest watch row of the last [MAX_AGE_MS], turned into sums
     *   ([StepWindows.fromWatch]). Rows the phone stored itself are ignored. Null when there is no
     *   such row.
     */
    suspend fun windows(nowMs: Long): StepWindows? {
        if (phoneSourceOn) return if (phone.available) phone.windows(nowMs) else null
        val rows = persistenceLayer.getStepsCountFromTimeToTime(nowMs - MAX_AGE_MS, nowMs)
        // Only the 180 minute row carries all six windows; the shorter rows leave the rest at 0.
        val row = rows
            .filter { it.duration == 180 * 60 * 1000L && it.device != phone.deviceName }
            .maxByOrNull { it.timestamp } ?: return null
        return StepWindows.fromWatch(
            block5 = row.steps5min,
            block10 = row.steps10min,
            block15 = row.steps15min,
            steps30 = row.steps30min,
            steps60 = row.steps60min,
            steps180 = row.steps180min,
            timestamp = row.timestamp,
        )
    }

    /** The five sport guard step limits from the settings. */
    private fun guardLimits() = ActivityAfterglow.Limits(
        mild60 = preferences.get(IntKey.ApsAutoIsfStepsMildSteps60),
        guard30 = preferences.get(IntKey.ApsAutoIsfStepsGuardSteps30),
        guard60 = preferences.get(IntKey.ApsAutoIsfStepsGuardSteps60),
        long60 = preferences.get(IntKey.ApsAutoIsfStepsLongSteps60),
        long180 = preferences.get(IntKey.ApsAutoIsfStepsLongSteps180),
    )

    /**
     * The step rules for one run.
     *
     * @param bg glucose in mg/dl
     * @param delta glucose change in mg/dl per 5 minutes
     * @param lastBolusTimeMs time of the last bolus or SMB, 0 when there was none
     */
    suspend fun decide(nowMs: Long, bg: Double, delta: Double, lastBolusTimeMs: Long): StepsActivity {
        val sportGuard = preferences.get(BooleanKey.ApsAutoIsfStepsSportGuard)
        val smbInterval = preferences.get(BooleanKey.ApsAutoIsfStepsSmbInterval)
        val effortOn = preferences.get(BooleanKey.ApsAutoIsfStepsEffortProtection)
        val wakeGateOn = preferences.get(BooleanKey.ApsAutoIsfStepsWakeGate)
        if (!sportGuard && !smbInterval && !effortOn && !wakeGateOn) {
            lastEffort = null
            afterglowMemory = ActivityAfterglow.Memory()
            return StepsActivity.NONE
        }
        val w = windows(nowMs)
        if (w == null) {
            lastEffort = null
            val source = if (phoneSourceOn) StepSource.PHONE else StepSource.WATCH
            aapsLogger.debug(LTag.APS, "AutoISF steps: a step rule is on but there is no step data from the ${source.label}")
            return StepsActivity.NONE
        }
        val wakeGate = if (wakeGateOn) {
            val result = WakeGate.evaluate(
                prior = wakeState,
                windows = w,
                localMinuteOfDay = localMinuteOfDay(),
                wakeSteps = preferences.get(IntKey.ApsAutoIsfStepsWakeSteps),
                startMinute = preferences.get(IntKey.ApsAutoIsfStepsWakeStartHour) * 60,
                endMinute = preferences.get(IntKey.ApsAutoIsfStepsWakeEndHour) * 60,
            )
            wakeState = result.state
            result
        } else {
            wakeState = WakeGate.State()
            null
        }
        val effort = if (effortOn) {
            val (assessment, memory) = EffortActivityBelief.assess(w, nowMs, effortMemory)
            effortMemory = memory
            assessment
        } else {
            null
        }
        lastEffort = effort
        val afterglow = if (sportGuard) {
            val (result, memory) = ActivityAfterglow.assess(w, bg, delta, nowMs, afterglowMemory, guardLimits())
            afterglowMemory = memory
            result
        } else {
            afterglowMemory = ActivityAfterglow.Memory()
            null
        }
        val lastSmbAgeMinutes = if (lastBolusTimeMs > 0L) (nowMs - lastBolusTimeMs) / 60_000.0 else Double.MAX_VALUE
        return StepsActivityRules.decide(w, bg, lastSmbAgeMinutes, smbInterval, afterglow, effort, wakeGate)
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
