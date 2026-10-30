package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.MidnightUtils
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots
import app.aaps.plugins.aps.openAPSAutoISF.MealWindows
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Result of [AutoIsfLogAnalysis.analyze]. */
data class AutoIsfLogAnalysisResult(
    val days: Int,
    /** The meal slot that was analyzed, null for the whole day. */
    val slot: AutoIsfSlots.Slot?,
    val filesRead: Int,
    val report: AutoIsfLogReport,
    /** Meal by meal, only for a slot. */
    val meals: List<AutoIsfMealEpisode> = emptyList(),
    val mealStats: AutoIsfMealStats? = null,
    /** The slot has no valid meal time, so nothing could be analyzed. */
    val noMealTime: Boolean = false
)

/** Result of [AutoIsfLogAnalysis.apply]. */
enum class AutoIsfApplyResult {

    Applied,

    /** The setting was changed after the analysis. Nothing was written, run the analysis again. */
    ChangedMeanwhile
}

/**
 * Runs the AutoISF log analysis and writes a suggested value when the user asks for it.
 *
 * With a meal slot, only the runs of that meal time (plus the tail after it) are used, the
 * current values are read from the slot keys and [apply] writes to the slot keys.
 *
 * Nothing is ever changed without the user: [apply] is only called from a button, after a
 * confirmation dialog.
 */
@Inject
class AutoIsfLogAnalysis(
    private val source: AutoIsfLogSource,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    val dateUtil: DateUtil,
    /** For the screen: BG in the units the user chose. */
    val profileUtil: ProfileUtil,
    /** For the screen: numbers in the user's locale. */
    val decimalFormatter: DecimalFormatter
) {

    /** False where there are no log files to read. Then the card must not be shown. */
    val available: Boolean get() = source.available

    /** The meal time text of a slot, as set in the preferences. */
    fun mealTime(slot: AutoIsfSlots.Slot): String = preferences.get(slot.windowKey)

    suspend fun analyze(days: Int, slot: AutoIsfSlots.Slot? = null): AutoIsfLogAnalysisResult {
        val since = dateUtil.now() - days * DAY_MILLIS
        val parser = AutoIsfLogParser()
        val files = source.readLines(since) { parser.accept(it) }
        val result = withContext(Dispatchers.Default) {
            if (slot == null) wholeDay(days, since, parser.runs, files) else oneSlot(days, since, slot, parser.runs, files)
        }
        aapsLogger.debug(
            LTag.APS,
            "AutoISF log analysis: $days days, slot ${slot?.label ?: "all"}, $files files, ${result.report.stats.runs} runs, " +
                "${result.meals.size} meals, ${result.report.suggestions.size} suggestions"
        )
        return result
    }

    private fun wholeDay(days: Int, since: Long, runs: List<AutoIsfLogRun>, files: Int): AutoIsfLogAnalysisResult {
        val report = AutoIsfLogAnalyzer().analyze(runs, since, currentValues(null))
        return AutoIsfLogAnalysisResult(days, null, files, report)
    }

    private fun oneSlot(days: Int, since: Long, slot: AutoIsfSlots.Slot, runs: List<AutoIsfLogRun>, files: Int): AutoIsfLogAnalysisResult {
        val window = MealWindows.parse(mealTime(slot))
            ?: return AutoIsfLogAnalysisResult(days, slot, files, emptyReport(since), noMealTime = true)
        val analyzer = AutoIsfLogAnalyzer()
        val mealAnalyzer = AutoIsfMealAnalyzer { MidnightUtils.secondsFromMidnight(it) / 60 }
        val inPeriod = analyzer.oneRunPerBg(runs.filter { it.timestamp >= since }.sortedBy { it.timestamp })
        val meals = mealAnalyzer.episodes(inPeriod, window, AutoIsfMealAnalyzer.DEFAULT_TAIL_MINUTES)
        val mealRuns = meals.filter { !it.hadTempTarget }.flatMap { meal -> inPeriod.filter { it.timestamp in meal.start..meal.end } }
        // Inside the meal time the log shows the slot values, in the tail the global ones. Only the
        // meal time counts when looking for a change of the settings. A run without the slot line
        // (older build) is placed by the clock.
        val inMealTime: (AutoIsfLogRun) -> Boolean = { run ->
            run.slotName?.let { it == slot.label } ?: window.contains(MidnightUtils.secondsFromMidnight(run.timestamp) / 60)
        }
        val report = analyzer.analyze(mealRuns, since, currentValues(slot), MIN_HOURS_SLOT, inMealTime)
        val used = meals.filter { it.start >= report.fromTime }
        return AutoIsfLogAnalysisResult(days, slot, files, report, used, mealAnalyzer.stats(used))
    }

    private fun emptyReport(since: Long) = AutoIsfLogAnalyzer().analyze(emptyList(), since, emptyMap())

    /**
     * Writes the suggested value. With a slot, it goes to the slot copy of the setting.
     */
    fun apply(suggestion: AutoIsfSuggestion, slot: AutoIsfSlots.Slot?): AutoIsfApplyResult {
        val key = keyFor(suggestion.param, slot)
        val now = preferences.get(key)
        if (abs(now - suggestion.current) > 1e-6) return AutoIsfApplyResult.ChangedMeanwhile
        val value = suggestion.param.clamp(suggestion.suggested)
        preferences.put(key, value)
        aapsLogger.info(LTag.APS, "AutoISF log analysis: ${key.key} changed by user from $now to $value (${suggestion.reason})")
        return AutoIsfApplyResult.Applied
    }

    private fun keyFor(param: AutoIsfParam, slot: AutoIsfSlots.Slot?): DoubleKey =
        slot?.doubles?.get(param.key) ?: param.key

    private fun currentValues(slot: AutoIsfSlots.Slot?): Map<AutoIsfParam, Double> =
        AutoIsfParam.entries.associateWith { preferences.get(keyFor(it, slot)) }

    companion object {

        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        val DAY_CHOICES = listOf(1, 3, 7)
        const val DEFAULT_DAYS = 3

        /** For one slot: about two meals with their tails. */
        const val MIN_HOURS_SLOT = 6.0
    }
}
