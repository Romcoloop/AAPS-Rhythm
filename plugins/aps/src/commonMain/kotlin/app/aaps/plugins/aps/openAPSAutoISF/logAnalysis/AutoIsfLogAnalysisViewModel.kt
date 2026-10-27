package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.compose.KeyValueRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AutoIsfSuggestionUi(
    val suggestion: AutoIsfSuggestion,
    val title: String,
    val change: String,
    val reason: String,
    val applied: Boolean = false
)

@Immutable
data class AutoIsfLogUiState(
    val days: Int = AutoIsfLogAnalysis.DEFAULT_DAYS,
    val running: Boolean = false,
    val hasResult: Boolean = false,
    val summary: List<KeyValueRow> = emptyList(),
    /** Plain notes shown above the suggestions: too little data, settings changed, nothing found. */
    val notes: List<String> = emptyList(),
    val events: List<String> = emptyList(),
    val suggestions: List<AutoIsfSuggestionUi> = emptyList(),
    /** Suggestion waiting for the user to confirm it in a dialog. */
    val pendingApply: AutoIsfSuggestionUi? = null,
    val confirmText: String = "",
    val message: String? = null
)

class AutoIsfLogAnalysisViewModel(
    private val analysis: AutoIsfLogAnalysis,
    private val rh: TextResolver,
    private val scope: CoroutineScope
) {

    private val _uiState = MutableStateFlow(AutoIsfLogUiState())
    val uiState: StateFlow<AutoIsfLogUiState> = _uiState.asStateFlow()

    fun onDaysChange(days: Int) {
        if (_uiState.value.running) return
        _uiState.update { it.copy(days = days) }
    }

    fun onAnalyze() {
        if (_uiState.value.running) return
        val days = _uiState.value.days
        _uiState.update { AutoIsfLogUiState(days = days, running = true) }
        scope.launch {
            val newState = try {
                buildState(analysis.analyze(days))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AutoIsfLogUiState(days = days, hasResult = true, message = rh.gs(ApsStrings.autoisf_log_read_error, e.message ?: e.toString()))
            }
            _uiState.value = newState
        }
    }

    fun onApplyClick(item: AutoIsfSuggestionUi) {
        val confirm = rh.gs(
            ApsStrings.autoisf_log_apply_confirm,
            item.title,
            value(item.suggestion.param, item.suggestion.current),
            value(item.suggestion.param, item.suggestion.suggested)
        )
        _uiState.update { it.copy(pendingApply = item, confirmText = confirm) }
    }

    fun onApplyDismiss() {
        _uiState.update { it.copy(pendingApply = null) }
    }

    fun onApplyConfirm() {
        val item = _uiState.value.pendingApply ?: return
        when (analysis.apply(item.suggestion)) {
            AutoIsfApplyResult.Applied          -> _uiState.update { state ->
                state.copy(
                    pendingApply = null,
                    message = rh.gs(ApsStrings.autoisf_log_saved),
                    // Other suggestions for the same setting are no longer valid.
                    suggestions = state.suggestions.map { if (it.suggestion == item.suggestion) it.copy(applied = true) else it }
                        .filter { it.applied || it.suggestion.param != item.suggestion.param }
                )
            }

            AutoIsfApplyResult.ChangedMeanwhile -> _uiState.update {
                it.copy(pendingApply = null, message = rh.gs(ApsStrings.autoisf_log_changed_meanwhile))
            }
        }
    }

    private fun buildState(result: AutoIsfLogAnalysisResult): AutoIsfLogUiState {
        val report = result.report
        val stats = report.stats
        if (stats.runs == 0)
            return AutoIsfLogUiState(days = result.days, hasResult = true, notes = listOf(rh.gs(ApsStrings.autoisf_log_no_runs)))

        val df = analysis.decimalFormatter
        val dateUtil = analysis.dateUtil
        fun pct(value: Double) = rh.gs(CoreUiStrings.units_format_percent_value, df.to1Decimal(value))

        val summary = listOf(
            KeyValueRow(
                rh.gs(ApsStrings.autoisf_log_period),
                rh.gs(ApsStrings.autoisf_log_period_value, dateUtil.dateAndTimeString(report.fromTime), dateUtil.dateAndTimeString(report.toTime))
            ),
            KeyValueRow(
                rh.gs(ApsStrings.autoisf_log_runs),
                rh.gs(ApsStrings.autoisf_log_runs_value, stats.runs, rh.gs(CoreUiStrings.format_hours, stats.hours), result.filesRead)
            ),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_mean_bg), analysis.profileUtil.fromMgdlToStringWithUnits(stats.meanBg)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_in_range), pct(stats.inRangePct)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_low), pct(stats.lowPct)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_very_low), pct(stats.veryLowPct)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_high), pct(stats.highPct)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_smbs), rh.gs(ApsStrings.autoisf_log_smbs_value, stats.smbCount, df.to2Decimal(stats.smbUnits))),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_smbs_at_max_bolus), stats.smbAtMaxBolus.toString()),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_max_hits), stats.autoIsfMaxHits.toString()),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_even_target), stats.evenTargetRuns.toString()),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_lows), rh.gs(ApsStrings.autoisf_log_lows_value, stats.lowEpisodes, stats.lowEpisodesAfterSmb)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_predicted_lows), stats.predictedLowsAfterSmb.toString())
        )

        val notes = buildList {
            report.settingsChangedAt?.let { add(rh.gs(ApsStrings.autoisf_log_settings_changed, dateUtil.dateAndTimeString(it))) }
            if (!report.enoughData)
                add(rh.gs(ApsStrings.autoisf_log_not_enough_data, rh.gs(CoreUiStrings.format_hours, stats.hours), rh.gs(CoreUiStrings.format_hours, AutoIsfLogAnalyzer.MIN_HOURS)))
            else if (report.suggestions.isEmpty()) add(rh.gs(ApsStrings.autoisf_log_no_suggestions))
        }

        val events = report.events.map { event ->
            rh.gs(
                if (event.outcome == AutoIsfOutcome.Low) ApsStrings.autoisf_log_event_low else ApsStrings.autoisf_log_event_predicted,
                dateUtil.timeString(event.smbTime),
                df.to2Decimal(event.smbUnits),
                analysis.profileUtil.fromMgdlToStringWithUnits(event.bgAtSmb),
                event.finalIsfFactor?.let { df.to2Decimal(it) } ?: "?",
                driverName(event.driver),
                analysis.profileUtil.fromMgdlToStringWithUnits(event.outcomeBg),
                dateUtil.timeString(event.outcomeTime)
            )
        }

        val suggestions = report.suggestions.map { suggestion ->
            AutoIsfSuggestionUi(
                suggestion = suggestion,
                title = rh.gs(suggestion.param.key.title),
                change = rh.gs(ApsStrings.autoisf_log_change, value(suggestion.param, suggestion.current), value(suggestion.param, suggestion.suggested)),
                reason = rh.gs(reasonText(suggestion.reason), suggestion.events, suggestion.ofEvents)
            )
        }

        return AutoIsfLogUiState(
            days = result.days,
            hasResult = true,
            summary = summary,
            notes = notes,
            events = events,
            suggestions = suggestions
        )
    }

    private fun value(param: AutoIsfParam, value: Double): String =
        if (param.decimals <= 1) analysis.decimalFormatter.to1Decimal(value) else analysis.decimalFormatter.to2Decimal(value)

    private fun driverName(driver: AutoIsfDriver): String = when (driver) {
        AutoIsfDriver.AutoIsfMax -> rh.gs(ApsStrings.autoisf_log_driver_max)
        AutoIsfDriver.Acce       -> "acce_ISF"
        AutoIsfDriver.Bg         -> "bg_ISF"
        AutoIsfDriver.Pp         -> "pp_ISF"
        AutoIsfDriver.Dura       -> "dura_ISF"
        AutoIsfDriver.None       -> rh.gs(ApsStrings.autoisf_log_driver_none)
    }

    private fun reasonText(reason: AutoIsfSuggestionReason) = when (reason) {
        AutoIsfSuggestionReason.LowsAfterMaxLimitedSmb   -> ApsStrings.autoisf_log_reason_max_limited
        AutoIsfSuggestionReason.LowsAfterAcceDrivenSmb   -> ApsStrings.autoisf_log_reason_acce
        AutoIsfSuggestionReason.LowsAfterBgDrivenSmb     -> ApsStrings.autoisf_log_reason_bg
        AutoIsfSuggestionReason.LowsAfterMaxRatioSmb     -> ApsStrings.autoisf_log_reason_ratio
        AutoIsfSuggestionReason.LowsAfterMaxBolusSmb     -> ApsStrings.autoisf_log_reason_max_bolus
        AutoIsfSuggestionReason.HighsLimitedByAutoIsfMax -> ApsStrings.autoisf_log_reason_highs_max
        AutoIsfSuggestionReason.HighsWithWeakBgIsf       -> ApsStrings.autoisf_log_reason_highs_bg
    }
}
