package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.MidnightUtils
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots
import app.aaps.plugins.aps.openAPSAutoISF.MealWindows
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Result of [AutoIsfLogAnalysis.analyzeLastMeal]. */
data class AutoIsfLogAnalysisResult(
    val filesRead: Int,
    /** The meal slot of the analyzed meal, null when the log has no finished meal. */
    val slot: AutoIsfSlots.Slot?,
    /** The last finished meal, null when there is none. */
    val meal: AutoIsfMealEpisode?,
    /** Facts and the suggestion of this one meal. */
    val report: AutoIsfLogReport,
    /**
     * The meal slots are switched off, so the algorithm uses the global values and a change of
     * the slot values would do nothing. Suggestions are then not offered.
     */
    val slotsOff: Boolean = false,
    /**
     * No run of the meal names a slot, so the loop used the global values then (the slots were off,
     * or the build was older). A suggestion for the slot would change something that took no part.
     */
    val globalValues: Boolean = false
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
 * It looks at one meal only: the last meal that is over. Only the runs of that meal time (plus the
 * tail after it) are used, the current values are read from the keys of the slot of that meal and
 * [apply] writes to the same slot keys.
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

    /**
     * Finds the last meal that is over (its meal time plus the tail) in the log of the last
     * [LOOK_BACK_DAYS] days and looks at it. The meal slots are tried all, the weekday and the
     * weekend ones; the runs name the slot they were run with, so a meal is placed in one slot only.
     */
    suspend fun analyzeLastMeal(): AutoIsfLogAnalysisResult {
        val now = dateUtil.now()
        val since = now - LOOK_BACK_DAYS * DAY_MILLIS
        val parser = AutoIsfLogParser()
        val files = source.readLines(since) { parser.accept(it) }
        val result = withContext(Dispatchers.Default) { lastMeal(now, since, parser.runs, files) }
        aapsLogger.debug(
            LTag.APS,
            "AutoISF log analysis: last meal ${result.slot?.label ?: "none"}, $files files, ${result.report.stats.runs} runs, " +
                "${result.report.suggestions.size} suggestions"
        )
        return result
    }

    private fun lastMeal(now: Long, since: Long, runs: List<AutoIsfLogRun>, files: Int): AutoIsfLogAnalysisResult {
        val analyzer = AutoIsfLogAnalyzer()
        val mealAnalyzer = AutoIsfMealAnalyzer { MidnightUtils.secondsFromMidnight(it) / 60 }
        val inPeriod = analyzer.oneRunPerBg(runs.filter { it.timestamp >= since }.sortedBy { it.timestamp })
        val found = MEAL_SLOTS.flatMap { slot ->
            val window = MealWindows.parse(mealTime(slot)) ?: return@flatMap emptyList()
            mealAnalyzer.episodes(inPeriod, window, tailMinutes(slot, window))
                .filter { meal -> isOver(meal, now) && belongsToSlot(slot, inPeriod.filter { it.timestamp in meal.start..meal.end }) }
                .map { slot to it }
        }
        val (slot, meal) = found.maxByOrNull { it.second.end }
            ?: return AutoIsfLogAnalysisResult(files, null, null, emptyReport(since))
        val window = MealWindows.parse(mealTime(slot))
        val mealRuns = inPeriod.filter { it.timestamp in meal.start..meal.end }
        // Inside the meal time the log shows the slot values, in the tail the global ones. Only the
        // meal time counts when looking for a change of the settings. A run without the slot line
        // (older build) is placed by the clock.
        val inMealTime: (AutoIsfLogRun) -> Boolean = { run ->
            run.slotName?.let { it == slot.label } ?: (window?.contains(MidnightUtils.secondsFromMidnight(run.timestamp) / 60) ?: true)
        }
        // A weekend slot is also dead while "Separate weekend slots" is off: the weekday slots run then.
        val slotsOff = !preferences.get(BooleanKey.ApsAutoIsfSlotsEnabled) ||
            (slot.dayType == AutoIsfSlots.DayType.WEEKEND && !preferences.get(BooleanKey.ApsAutoIsfWeekendSlotsEnabled))
        val full = analyzer.analyze(mealRuns, meal.start, currentValues(slot), MIN_HOURS_MEAL, inMealTime)
        // One meal is little data: a suggestion only for a complete meal without a temp target, and
        // only the strongest one.
        val globalValues = mealRuns.none { it.slotName != null }
        val report = if (slotsOff || globalValues || !meal.used) full.copy(suggestions = emptyList()) else full.copy(suggestions = full.suggestions.take(1))
        return AutoIsfLogAnalysisResult(files, slot, meal, report, slotsOff = slotsOff, globalValues = globalValues)
    }

    /** A meal is over when its meal time plus tail is over by the clock. A gap in the log does not change that. */
    private fun isOver(meal: AutoIsfMealEpisode, now: Long): Boolean = now >= meal.plannedEnd

    /**
     * How long a meal is followed after its meal time: the usual tail, but not into the next meal of
     * the same kind of day. Otherwise the rise after lunch would count for the breakfast.
     */
    private fun tailMinutes(slot: AutoIsfSlots.Slot, window: MealWindows.Window): Int {
        val untilNextMeal = MEAL_SLOTS
            .filter { it != slot && it.dayType == slot.dayType }
            .mapNotNull { MealWindows.parse(mealTime(it)) }
            .map { (it.startMinute - window.endMinute).mod(MINUTES_PER_DAY) }
            .minOrNull() ?: AutoIsfMealAnalyzer.DEFAULT_TAIL_MINUTES
        return minOf(AutoIsfMealAnalyzer.DEFAULT_TAIL_MINUTES, untilNextMeal)
    }

    private fun emptyReport(since: Long) = AutoIsfLogAnalyzer().analyze(emptyList(), since, emptyMap())

    /**
     * Writes the suggested value to the slot copy of the setting.
     */
    fun apply(suggestion: AutoIsfSuggestion, slot: AutoIsfSlots.Slot): AutoIsfApplyResult {
        val key = keyFor(suggestion.param, slot)
        val now = preferences.get(key)
        if (abs(now - suggestion.current) > 1e-6) return AutoIsfApplyResult.ChangedMeanwhile
        val value = suggestion.param.clamp(suggestion.suggested)
        preferences.put(key, value)
        aapsLogger.info(LTag.APS, "AutoISF log analysis: ${key.key} changed by user from $now to $value (${suggestion.reason})")
        return AutoIsfApplyResult.Applied
    }

    /** The key to read and write: the slot copy of the setting. */
    private fun keyFor(param: AutoIsfParam, slot: AutoIsfSlots.Slot): DoubleKey = slot.doubles.getValue(param.key)

    private fun currentValues(slot: AutoIsfSlots.Slot): Map<AutoIsfParam, Double> =
        AutoIsfParam.entries.associateWith { preferences.get(keyFor(it, slot)) }

    companion object {

        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /** How far back the log is read to find the last meal. Covers a weekend and a skipped meal. */
        const val LOOK_BACK_DAYS = 3

        private const val MINUTES_PER_DAY = 24 * 60

        /** One meal with its tail: about three hours of runs. */
        const val MIN_HOURS_MEAL = 3.0

        /** The slots that are meals. The night is not a meal. */
        private val MEAL_SLOTS = AutoIsfSlots.Slot.entries.filter { it != AutoIsfSlots.Slot.NIGHT }

        /**
         * True when a meal (the runs of one window plus tail) was run with [slot].
         *
         * The runs say which slot they used (the "AutoISF slot" line). A weekday breakfast window
         * on a Saturday is not a weekday breakfast: its runs name the weekend slot, or "none".
         * Runs from an older build have no slot line; they are kept for a weekday slot only,
         * because the weekend and night slots did not exist then.
         */
        fun belongsToSlot(slot: AutoIsfSlots.Slot, runs: List<AutoIsfLogRun>): Boolean {
            val named = runs.mapNotNull { it.slotName }
            if (named.isEmpty()) return slot.dayType == AutoIsfSlots.DayType.WEEKDAY
            return named.any { it == slot.label }
        }
    }
}
