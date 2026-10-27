package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.core.keys.DoubleKey
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** The AutoISF settings the analyzer can suggest a new value for. */
enum class AutoIsfParam(
    val key: DoubleKey,
    /** Name of the value in the `OapsProfileAutoIsf(...)` log line. */
    val logName: String,
    val decimals: Int
) {

    AutoIsfMax(DoubleKey.ApsAutoIsfMax, "autoISF_max", 2),
    BgAccelWeight(DoubleKey.ApsAutoIsfBgAccelWeight, "bgAccel_ISF_weight", 2),
    HighBgWeight(DoubleKey.ApsAutoIsfHighBgWeight, "higher_ISFrange_weight", 2),
    SmbRatioMax(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax, "smb_delivery_ratio_max", 2),
    SmbRangeExtension(DoubleKey.ApsAutoIsfSmbMaxRangeExtension, "smb_max_range_extension", 1);

    fun roundValue(value: Double): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        return round(value * factor) / factor
    }

    fun clamp(value: Double): Double = min(key.max, max(key.min, value))
}

/** Why a value is suggested. The UI turns it into text. */
enum class AutoIsfSuggestionReason {

    /** Lows came after SMBs where `autoISF_max` cut the factor. Lower limit = smaller SMBs there. */
    LowsAfterMaxLimitedSmb,

    /** Lows came after SMBs driven by acce_ISF (fast rise that did not continue). */
    LowsAfterAcceDrivenSmb,

    /** Lows came after SMBs driven by bg_ISF (high BG alone made ISF stronger). */
    LowsAfterBgDrivenSmb,

    /** Lows came after SMBs where the delivery ratio was at its maximum. */
    LowsAfterMaxRatioSmb,

    /** Lows came after SMBs that were as big as maxBolus allowed. */
    LowsAfterMaxBolusSmb,

    /** BG stayed high, no lows, and `autoISF_max` often cut the factor. */
    HighsLimitedByAutoIsfMax,

    /** BG stayed high, no lows, and bg_ISF was the factor that worked. */
    HighsWithWeakBgIsf
}

data class AutoIsfSuggestion(
    val param: AutoIsfParam,
    val current: Double,
    val suggested: Double,
    val reason: AutoIsfSuggestionReason,
    /** How many events support the suggestion. */
    val events: Int,
    /** Out of how many events in total. */
    val ofEvents: Int
)

/** What happened after an SMB. */
enum class AutoIsfOutcome {

    /** BG went below [AutoIsfLogAnalyzer.LOW_MGDL]. */
    Low,

    /** BG did not go low, but the loop predicted it would and switched SMB off. */
    PredictedLow
}

/** An SMB that was followed by a low or by a predicted low. */
data class AutoIsfSmbEvent(
    val smbTime: Long,
    val smbUnits: Double,
    val bgAtSmb: Double,
    val iobAtSmb: Double?,
    val finalIsfFactor: Double?,
    val driver: AutoIsfDriver,
    val outcome: AutoIsfOutcome,
    val outcomeTime: Long,
    val outcomeBg: Double
)

/** Which part of AutoISF made the SMB big. */
enum class AutoIsfDriver { AutoIsfMax, Acce, Bg, Pp, Dura, None }

data class AutoIsfLogStats(
    val runs: Int,
    val hours: Double,
    val meanBg: Double,
    /** Share of runs in %, BG below 54 mg/dl. */
    val veryLowPct: Double,
    /** Share of runs in %, BG below 70 mg/dl (includes [veryLowPct]). */
    val lowPct: Double,
    /** Share of runs in %, BG 70 - 180 mg/dl. */
    val inRangePct: Double,
    /** Share of runs in %, BG above 180 mg/dl. */
    val highPct: Double,
    val smbCount: Int,
    val smbUnits: Double,
    val smbAtMaxBolus: Int,
    val autoIsfMaxHits: Int,
    val evenTargetRuns: Int,
    val lowEpisodes: Int,
    val lowEpisodesAfterSmb: Int,
    val predictedLowsAfterSmb: Int
)

data class AutoIsfLogReport(
    val fromTime: Long,
    val toTime: Long,
    /** Set when AutoISF settings changed in the chosen period. Only runs after this time are used. */
    val settingsChangedAt: Long?,
    val enoughData: Boolean,
    val stats: AutoIsfLogStats,
    val events: List<AutoIsfSmbEvent>,
    val suggestions: List<AutoIsfSuggestion>
)

/**
 * Looks at AutoISF loop runs from the log and suggests careful changes to the AutoISF settings.
 *
 * The rules are simple and they are on purpose careful:
 * - Nothing is suggested from less than [MIN_HOURS] hours of runs with the same settings.
 * - When there are lows after SMBs, only changes that give **less** insulin are suggested.
 * - Changes that give **more** insulin are suggested only when there were no lows at all and BG was
 *   often high.
 * - Every step is small, and stays inside the limits of the setting.
 *
 * It does not know about carbs, exercise, sensor errors or a wrong basal or ISF in the profile.
 * The user must decide. Change one setting at a time and watch a few days.
 */
class AutoIsfLogAnalyzer {

    /**
     * @param runs runs from [AutoIsfLogParser], in any order
     * @param fromTime only runs at or after this time are used
     * @param current current values of the settings, as stored in preferences
     */
    fun analyze(runs: List<AutoIsfLogRun>, fromTime: Long, current: Map<AutoIsfParam, Double>): AutoIsfLogReport {
        val inPeriod = oneRunPerBg(runs.filter { it.timestamp >= fromTime }.sortedBy { it.timestamp })

        // Use only the runs after the last change of AutoISF settings. Runs before it say nothing
        // about the values in use now.
        var settingsChangedAt: Long? = null
        var startIndex = 0
        for (i in inPeriod.indices.reversed()) {
            val settings = inPeriod[i].settings ?: continue
            val newer = inPeriod.subList(i + 1, inPeriod.size).firstOrNull { it.settings != null }?.settings ?: continue
            if (settingsDiffer(settings, newer)) {
                startIndex = i + 1
                settingsChangedAt = inPeriod[startIndex].timestamp
                break
            }
        }
        val used = inPeriod.subList(startIndex, inPeriod.size)

        val events = smbEvents(used)
        val stats = stats(used, events)
        val enoughData = stats.hours >= MIN_HOURS
        val suggestions = if (enoughData) suggestions(used, stats, events, current) else emptyList()
        return AutoIsfLogReport(
            fromTime = used.firstOrNull()?.timestamp ?: fromTime,
            toTime = used.lastOrNull()?.timestamp ?: fromTime,
            settingsChangedAt = settingsChangedAt,
            enoughData = enoughData,
            stats = stats,
            events = events,
            suggestions = suggestions
        )
    }

    /**
     * The loop can run several times for one BG reading (new BG, pump event, pull to refresh). Keep
     * one run per reading, so each 5 minutes count once: the run that gave an SMB, or else the last.
     */
    internal fun oneRunPerBg(sorted: List<AutoIsfLogRun>): List<AutoIsfLogRun> {
        val result = mutableListOf<AutoIsfLogRun>()
        for (run in sorted) {
            val previous = result.lastOrNull()
            val sameReading = previous != null && run.bgTimestamp != null && run.bgTimestamp == previous.bgTimestamp
            if (!sameReading) {
                result.add(run)
                continue
            }
            if (previous.smbUnits > 0.0 && run.smbUnits > 0.0) result.add(run)     // two real SMBs, keep both
            else if (previous.smbUnits <= 0.0) result[result.lastIndex] = run
        }
        return result
    }

    private fun settingsDiffer(a: Map<String, Double>, b: Map<String, Double>): Boolean =
        AutoIsfLogParser.SETTING_NAMES.any { name ->
            val x = a[name]
            val y = b[name]
            x != null && y != null && abs(x - y) > 1e-6
        }

    private fun stats(runs: List<AutoIsfLogRun>, events: List<AutoIsfSmbEvent>): AutoIsfLogStats {
        val count = runs.size
        fun pct(n: Int) = if (count == 0) 0.0 else 100.0 * n / count
        val lowEpisodes = lowEpisodes(runs)
        return AutoIsfLogStats(
            runs = count,
            hours = count * RUN_MINUTES / 60.0,
            meanBg = if (count == 0) 0.0 else runs.sumOf { it.bg } / count,
            veryLowPct = pct(runs.count { it.bg < VERY_LOW_MGDL }),
            lowPct = pct(runs.count { it.bg < LOW_MGDL }),
            inRangePct = pct(runs.count { it.bg >= LOW_MGDL && it.bg <= HIGH_MGDL }),
            highPct = pct(runs.count { it.bg > HIGH_MGDL }),
            smbCount = runs.count { it.smbUnits > 0.0 },
            smbUnits = runs.sumOf { it.smbUnits },
            smbAtMaxBolus = runs.count { it.smbAtMaxBolus },
            autoIsfMaxHits = runs.count { it.limitedByAutoIsfMax },
            evenTargetRuns = runs.count { it.evenTargetSmb },
            lowEpisodes = lowEpisodes.size,
            lowEpisodesAfterSmb = events.count { it.outcome == AutoIsfOutcome.Low },
            predictedLowsAfterSmb = events.count { it.outcome == AutoIsfOutcome.PredictedLow }
        )
    }

    /** Index of the first run of each low (a low ends after BG is back above the limit). */
    private fun lowEpisodes(runs: List<AutoIsfLogRun>): List<Int> {
        val starts = mutableListOf<Int>()
        var inLow = false
        runs.forEachIndexed { index, run ->
            if (run.bg < LOW_MGDL) {
                if (!inLow) starts.add(index)
                inLow = true
            } else if (run.bg >= LOW_MGDL + LOW_HYSTERESIS_MGDL) inLow = false
        }
        return starts
    }

    /**
     * Each low, and each "SMB off because of a predicted low", is linked to the biggest SMB in the
     * [LOOK_BACK_MINUTES] before it. One SMB is linked to one outcome at most - a real low wins over
     * a predicted one.
     */
    internal fun smbEvents(runs: List<AutoIsfLogRun>): List<AutoIsfSmbEvent> {
        val events = mutableListOf<AutoIsfSmbEvent>()
        val usedSmbs = mutableSetOf<Long>()

        fun link(outcomeRun: AutoIsfLogRun, outcome: AutoIsfOutcome) {
            val window = runs.filter {
                it.smbUnits > 0.0 && it.timestamp < outcomeRun.timestamp &&
                    it.timestamp >= outcomeRun.timestamp - LOOK_BACK_MINUTES * 60_000L && it.timestamp !in usedSmbs
            }
            val smb = window.maxByOrNull { it.smbUnits } ?: return
            usedSmbs.add(smb.timestamp)
            events.add(
                AutoIsfSmbEvent(
                    smbTime = smb.timestamp,
                    smbUnits = smb.smbUnits,
                    bgAtSmb = smb.bg,
                    iobAtSmb = smb.iob,
                    finalIsfFactor = smb.finalIsfFactor,
                    driver = driver(smb),
                    outcome = outcome,
                    outcomeTime = outcomeRun.timestamp,
                    outcomeBg = outcomeRun.bg
                )
            )
        }

        lowEpisodes(runs).forEach { link(runs[it], AutoIsfOutcome.Low) }
        // A predicted low counts once per episode: skip runs right after the previous one.
        var lastPredicted: Long? = null
        runs.filter { it.smbStoppedByMinGuard && it.bg >= LOW_MGDL }.forEach {
            val previous = lastPredicted
            if (previous == null || it.timestamp - previous > PREDICTED_LOW_GAP_MINUTES * 60_000L) link(it, AutoIsfOutcome.PredictedLow)
            lastPredicted = it.timestamp
        }
        return events.sortedBy { it.smbTime }
    }

    internal fun driver(run: AutoIsfLogRun): AutoIsfDriver = when {
        run.limitedByAutoIsfMax -> AutoIsfDriver.AutoIsfMax
        run.acceDriven          -> AutoIsfDriver.Acce
        run.bgDriven            -> AutoIsfDriver.Bg
        (run.ppIsf ?: 0.0) > 1.0 && (run.ppIsf ?: 0.0) >= maxOf(run.bgIsf ?: 0.0, run.duraIsf ?: 0.0) -> AutoIsfDriver.Pp
        (run.duraIsf ?: 0.0) > 1.0 -> AutoIsfDriver.Dura
        else                    -> AutoIsfDriver.None
    }

    private fun suggestions(
        runs: List<AutoIsfLogRun>,
        stats: AutoIsfLogStats,
        events: List<AutoIsfSmbEvent>,
        current: Map<AutoIsfParam, Double>
    ): List<AutoIsfSuggestion> {
        val days = max(1.0, stats.hours / 24.0)
        val lows = events.filter { it.outcome == AutoIsfOutcome.Low }
        val predicted = events.filter { it.outcome == AutoIsfOutcome.PredictedLow }
        val lowSignal = lows.size >= MIN_LOW_EVENTS || stats.lowPct >= LOW_PCT_LIMIT ||
            predicted.size >= max(MIN_PREDICTED_EVENTS.toDouble(), PREDICTED_PER_DAY * days)
        val result = mutableListOf<AutoIsfSuggestion>()

        fun add(param: AutoIsfParam, suggested: Double, reason: AutoIsfSuggestionReason, n: Int, of: Int) {
            val now = current[param] ?: return
            val value = param.roundValue(param.clamp(suggested))
            if (abs(value - now) < 1e-6) return
            result.add(AutoIsfSuggestion(param, now, value, reason, n, of))
        }

        if (lowSignal) {
            // Look at the SMBs that came before lows. When there are no real lows, use predicted ones.
            val bad = lows.ifEmpty { predicted }
            if (bad.isEmpty()) return result
            val badRuns = bad.mapNotNull { event -> runs.firstOrNull { it.timestamp == event.smbTime } }
            val total = badRuns.size
            fun share(n: Int) = total > 0 && n * 2 >= total

            val maxLimited = badRuns.count { it.limitedByAutoIsfMax }
            if (share(maxLimited)) current[AutoIsfParam.AutoIsfMax]?.let {
                add(AutoIsfParam.AutoIsfMax, it - 0.1, AutoIsfSuggestionReason.LowsAfterMaxLimitedSmb, maxLimited, total)
            }
            val acce = badRuns.count { it.acceDriven }
            if (share(acce)) current[AutoIsfParam.BgAccelWeight]?.let {
                if (it > 0.0) add(AutoIsfParam.BgAccelWeight, floorTo(it * 0.8, 2), AutoIsfSuggestionReason.LowsAfterAcceDrivenSmb, acce, total)
            }
            val bgDriven = badRuns.count { it.bgDriven }
            if (share(bgDriven)) current[AutoIsfParam.HighBgWeight]?.let {
                if (it > 0.0) add(AutoIsfParam.HighBgWeight, floorTo(it * 0.8, 2), AutoIsfSuggestionReason.LowsAfterBgDrivenSmb, bgDriven, total)
            }
            val ratio = badRuns.count { it.smbRatioAtMax }
            if (share(ratio)) current[AutoIsfParam.SmbRatioMax]?.let {
                add(AutoIsfParam.SmbRatioMax, it - 0.1, AutoIsfSuggestionReason.LowsAfterMaxRatioSmb, ratio, total)
            }
            val atMax = badRuns.count { it.smbAtMaxBolus }
            if (share(atMax)) current[AutoIsfParam.SmbRangeExtension]?.let {
                if (it > 1.0) add(AutoIsfParam.SmbRangeExtension, it - 1.0, AutoIsfSuggestionReason.LowsAfterMaxBolusSmb, atMax, total)
            }
            return result.sortedByDescending { it.events.toDouble() / max(1, it.ofEvents) }
        }

        // More insulin only when there was no sign of too much insulin at all.
        val noLows = stats.lowEpisodes == 0 && events.isEmpty() && stats.lowPct < 1.0
        if (!noLows || stats.highPct < HIGH_PCT_LIMIT) return result
        val highRuns = runs.filter { it.bg > HIGH_MGDL && (it.eventualBg ?: 0.0) > (it.targetBg ?: Double.MAX_VALUE) }
        if (highRuns.size < MIN_HIGH_RUNS) return result
        val maxLimited = highRuns.count { it.limitedByAutoIsfMax }
        if (maxLimited >= highRuns.size * HIGH_SHARE) {
            current[AutoIsfParam.AutoIsfMax]?.let {
                add(AutoIsfParam.AutoIsfMax, it + 0.1, AutoIsfSuggestionReason.HighsLimitedByAutoIsfMax, maxLimited, highRuns.size)
            }
            return result
        }
        val bgDriven = highRuns.count { it.bgDriven && !it.limitedByAutoIsfMax }
        if (bgDriven >= highRuns.size * HIGH_SHARE) current[AutoIsfParam.HighBgWeight]?.let {
            if (it > 0.0) add(AutoIsfParam.HighBgWeight, it * 1.1, AutoIsfSuggestionReason.HighsWithWeakBgIsf, bgDriven, highRuns.size)
        }
        return result
    }

    private fun floorTo(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        return floor(value * factor) / factor
    }

    companion object {

        const val LOW_MGDL = 70.0
        const val VERY_LOW_MGDL = 54.0
        const val HIGH_MGDL = 180.0
        private const val LOW_HYSTERESIS_MGDL = 10.0

        /** A run stands for one 5 minute BG reading. */
        private const val RUN_MINUTES = 5.0

        /** Less than this, and only the facts are shown, no suggestions. */
        const val MIN_HOURS = 12.0

        /** How far back a low is linked to an SMB. */
        const val LOOK_BACK_MINUTES = 180L
        private const val PREDICTED_LOW_GAP_MINUTES = 30L

        private const val MIN_LOW_EVENTS = 2
        private const val LOW_PCT_LIMIT = 4.0
        private const val MIN_PREDICTED_EVENTS = 3
        private const val PREDICTED_PER_DAY = 2.0

        private const val HIGH_PCT_LIMIT = 25.0
        private const val MIN_HIGH_RUNS = 24
        private const val HIGH_SHARE = 0.3
    }
}
