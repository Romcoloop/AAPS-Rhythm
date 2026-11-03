package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** One AutoISF or SMB delivery number as the loop uses it now. */
data class AutoIsfValue(
    val title: TextRef,
    val value: Double,
    /** Set when the analyzer can suggest a new value for it. */
    val param: AutoIsfParam?,
    /** True for a whole number (the IOB threshold). */
    val whole: Boolean
)

/** Result of [AutoIsfLogAnalysis.analyzeRecent]. */
data class AutoIsfLogAnalysisResult(
    val filesRead: Int,
    /** How many hours before now were looked at. */
    val hours: Int,
    /** The numbers of the last hours, null when the log has too few loop runs. */
    val recent: AutoIsfRecentHours?,
    /**
     * The slot that all runs of the last hours used. Null when they used more than one set of
     * values (several slots, or the global values): no suggestion is made then. "Apply" writes to
     * this slot.
     */
    val slot: AutoIsfSlots.Slot?,
    /** The slot the loop uses now, from the last run. Null: the global values. */
    val slotNow: AutoIsfSlots.Slot?,
    /** All 15 numbers the loop uses now (the values of [slotNow], or the global ones). */
    val values: List<AutoIsfValue>,
    /** Facts, events and the suggestions of the last hours. */
    val report: AutoIsfLogReport,
    /**
     * The slot is switched off now (meal slot settings or separate weekend slots), so the algorithm
     * uses the global values and a change of the slot values would do nothing. No suggestions then.
     */
    val slotsOff: Boolean = false
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
 * It looks at the last hours before now (as many as the user chooses), whatever happened in them: the numbers of the
 * runs, the SMBs before a low, and a suggestion. A suggestion is made only when all runs used the
 * values of one slot; [apply] writes to the keys of that slot.
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

    /** Looks at the last [hours] hours of the log, as many as the user chose. No meal times and no choice of a slot. */
    suspend fun analyzeRecent(hours: Int): AutoIsfLogAnalysisResult {
        val now = dateUtil.now()
        val since = now - (hours * 60 + LOG_MARGIN_MINUTES) * MINUTE_MILLIS
        val parser = AutoIsfLogParser()
        val files = source.readLines(since) { parser.accept(it) }
        val result = withContext(Dispatchers.Default) { recent(now, since, hours, parser.runs, files) }
        aapsLogger.debug(
            LTag.APS,
            "AutoISF log analysis: last $hours h, slot ${result.slot?.label ?: "none"}, $files files, ${result.report.stats.runs} runs, " +
                "${result.report.suggestions.size} suggestions"
        )
        return result
    }

    private fun recent(now: Long, since: Long, hours: Int, runs: List<AutoIsfLogRun>, files: Int): AutoIsfLogAnalysisResult {
        val analyzer = AutoIsfLogAnalyzer()
        val from = now - hours * 60 * MINUTE_MILLIS
        val inPeriod = analyzer.oneRunPerBg(runs.filter { it.timestamp >= since }.sortedBy { it.timestamp })
        val recent = AutoIsfRecentAnalyzer().look(inPeriod, now, hours * 60)
            ?: return AutoIsfLogAnalysisResult(files, hours, null, null, null, valuesOf(null), emptyReport(since))
        val inTime = inPeriod.filter { it.timestamp in from..now }
        val slotNow = slotForName(inTime.last().slotName)
        val slot = slotOfRuns(inTime)
        // A slot that is switched off now cannot be changed with any effect.
        val slotsOff = slot != null && (
            !preferences.get(BooleanKey.ApsAutoIsfSlotsEnabled) ||
                (slot.dayType == AutoIsfSlots.DayType.WEEKEND && !preferences.get(BooleanKey.ApsAutoIsfWeekendSlotsEnabled))
            )
        // Without one slot there is no setting to compare with, so no suggestion can be made.
        val full = analyzer.analyze(inTime, from, if (slot == null) emptyMap() else currentValues(slot), MIN_HOURS_RECENT)
        val report = if (slot == null || slotsOff || !recent.used) full.copy(suggestions = emptyList()) else full
        return AutoIsfLogAnalysisResult(files, hours, recent, slot, slotNow, valuesOf(slotNow), report, slotsOff = slotsOff)
    }

    /** All 15 numbers of a slot, or the global ones with null: what the loop uses there. */
    fun valuesOf(slot: AutoIsfSlots.Slot?): List<AutoIsfValue> {
        val keys = AutoIsfSlots.Slot.BREAKFAST
        val doubles = keys.doubles.keys.map { global ->
            AutoIsfValue(global.title, preferences.get(slot?.doubles?.getValue(global) ?: global), AutoIsfParam.entries.firstOrNull { it.key == global }, whole = false)
        }
        val ints = keys.ints.keys.map { global ->
            AutoIsfValue(global.title, preferences.get(slot?.ints?.getValue(global) ?: global).toDouble(), null, whole = true)
        }
        return doubles + ints
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

        private const val MINUTE_MILLIS = 60_000L

        /** Quick choices next to the slider. */
        val HOURS_CHOICES = listOf(3, 6)

        /** The most hours the user can choose: the slider goes from 1 to this. */
        const val MAX_HOURS = 24

        /** How many hours the analysis looks at when the user chooses nothing. */
        const val DEFAULT_HOURS = 3

        /** The log is read this many minutes further back, so the first run of the time looked at is not missed. */
        private const val LOG_MARGIN_MINUTES = 30

        /** Less than this much time of runs with the same settings, and only the facts are shown. */
        const val MIN_HOURS_RECENT = 2.0

        /**
         * The slot a run was run with, from the "AutoISF slot" line of the log: a slot label such as
         * "Lunch" or "Lunch-weekend". Null for "none" (outside all slot times), for no line (slots off
         * or an older build) and for a name that is not a slot: the global values were used then.
         */
        fun slotForName(name: String?): AutoIsfSlots.Slot? = AutoIsfSlots.Slot.entries.firstOrNull { it.label == name }

        /**
         * The slot that all [runs] used, or null when they used more than one set of values: two
         * slots, a slot and the global values, or the global values only.
         */
        fun slotOfRuns(runs: List<AutoIsfLogRun>): AutoIsfSlots.Slot? {
            val names = runs.map { it.slotName }.distinct()
            return if (names.size == 1) slotForName(names.single()) else null
        }
    }
}
