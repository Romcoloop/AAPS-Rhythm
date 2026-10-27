package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Result of [AutoIsfLogAnalysis.analyze]. */
data class AutoIsfLogAnalysisResult(
    val days: Int,
    val filesRead: Int,
    val report: AutoIsfLogReport
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

    suspend fun analyze(days: Int): AutoIsfLogAnalysisResult {
        val since = dateUtil.now() - days * DAY_MILLIS
        val parser = AutoIsfLogParser()
        val files = source.readLines(since) { parser.accept(it) }
        val report = withContext(Dispatchers.Default) {
            AutoIsfLogAnalyzer().analyze(parser.runs, since, currentValues())
        }
        aapsLogger.debug(LTag.APS, "AutoISF log analysis: $days days, $files files, ${report.stats.runs} runs, ${report.suggestions.size} suggestions")
        return AutoIsfLogAnalysisResult(days, files, report)
    }

    fun apply(suggestion: AutoIsfSuggestion): AutoIsfApplyResult {
        val key = suggestion.param.key
        val now = preferences.get(key)
        if (abs(now - suggestion.current) > 1e-6) return AutoIsfApplyResult.ChangedMeanwhile
        val value = suggestion.param.clamp(suggestion.suggested)
        preferences.put(key, value)
        aapsLogger.info(LTag.APS, "AutoISF log analysis: ${key.key} changed by user from $now to $value (${suggestion.reason})")
        return AutoIsfApplyResult.Applied
    }

    private fun currentValues(): Map<AutoIsfParam, Double> =
        AutoIsfParam.entries.associateWith { preferences.get(it.key) }

    companion object {

        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        val DAY_CHOICES = listOf(1, 3, 7)
        const val DEFAULT_DAYS = 3
    }
}
