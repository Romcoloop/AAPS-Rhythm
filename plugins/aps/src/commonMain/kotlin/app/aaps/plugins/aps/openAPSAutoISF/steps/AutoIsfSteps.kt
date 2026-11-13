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
     * - Switch off: the newest watch row of the last [MAX_AGE_MS], as sums. A watch app before AAPS
     *   #5499 sends single blocks for 10 and 15 minutes and they are added up
     *   ([StepWindows.fromWatchBlocks]); a newer one sends sums and they are taken as they are
     *   ([StepWindows.fromWatchSums]). [watchSendsSums] tells the two apart. Rows the phone stored
     *   itself are ignored. Null when there is no such row.
     */
    suspend fun windows(nowMs: Long): StepWindows? {
        if (phoneSourceOn) return if (phone.available) phone.windows(nowMs) else null
        val watchRows = persistenceLayer.getStepsCountFromTimeToTime(nowMs - MAX_AGE_MS, nowMs)
            .filter { it.device != phone.deviceName }
        // The 180 minute row has all six windows with every version of the watch app.
        val row = watchRows
            .filter { it.duration == LONGEST_WINDOW_MS }
            .maxByOrNull { it.timestamp } ?: return null
        return if (watchSendsSums(row, watchRows)) {
            StepWindows.fromWatchSums(
                steps5 = row.steps5min,
                steps10 = row.steps10min,
                steps15 = row.steps15min,
                steps30 = row.steps30min,
                steps60 = row.steps60min,
                steps180 = row.steps180min,
                timestamp = row.timestamp,
            )
        } else {
            StepWindows.fromWatchBlocks(
                block5 = row.steps5min,
                block10 = row.steps10min,
                block15 = row.steps15min,
                steps30 = row.steps30min,
                steps60 = row.steps60min,
                steps180 = row.steps180min,
                timestamp = row.timestamp,
            )
        }
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

        /** The length of the longest step window, the one of the row that has all six windows. */
        private const val LONGEST_WINDOW_MS = 180 * 60 * 1000L

        /**
         * True when the watch that wrote [row] sends its 10 and 15 minute values as sums (the watch app
         * of AAPS #5499 and newer). False when they are single 5 minute blocks (older watch apps).
         *
         * The 180 minute row looks the same in both forms, so the shorter rows of the same watch give
         * the answer. An older watch app fills a row only up to its own length: its 5 minute row has 0
         * for 10 minutes and longer. A newer one fills all six windows in every row. So a shorter row
         * with steps in a window longer than itself can only come from a newer watch app.
         *
         * The rows of the same sample as [row] decide. Only when the sample has no shorter row, the
         * other rows of this watch in [rows] decide. Without any such steps the answer is false, the
         * form every watch app had before, so nothing changes for an older watch.
         *
         * @param row the 180 minute row the steps are read from
         * @param rows the watch rows of the last minutes, [row] among them
         */
        internal fun watchSendsSums(row: SC, rows: List<SC>): Boolean {
            val shorter = rows.filter { it.device == row.device && it.duration < row.duration }
            val sameSample = shorter.filter { it.timestamp == row.timestamp }
            return sameSample.ifEmpty { shorter }.any { it.hasStepsBeyondOwnWindow() }
        }

        /** True when this row has steps in a window that is longer than the row itself. */
        private fun SC.hasStepsBeyondOwnWindow(): Boolean {
            val minutes = duration / 60_000L
            return (minutes < 10 && steps10min > 0) ||
                (minutes < 15 && steps15min > 0) ||
                (minutes < 30 && steps30min > 0) ||
                (minutes < 60 && steps60min > 0) ||
                (minutes < 180 && steps180min > 0)
        }

        /**
         * Six rows for one sample of the phone: one per window length, each with the windows up to its
         * own length filled and the longer ones at 0. This is the form of a watch app before AAPS
         * #5499. An automation trigger of that time picks the row by its duration, so all six are
         * needed. The trigger of today reads the 180 minute row, which has all the windows.
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
