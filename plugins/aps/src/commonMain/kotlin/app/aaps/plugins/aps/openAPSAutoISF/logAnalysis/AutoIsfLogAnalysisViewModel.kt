package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.compose.KeyValueRow
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots
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
    /** How many hours before now are analyzed. */
    val hours: Int = AutoIsfLogAnalysis.DEFAULT_HOURS,
    /** The meal slot of the analyzed meal. "Apply" writes to this slot. */
    val slot: AutoIsfSlots.Slot? = null,
    val running: Boolean = false,
    val hasResult: Boolean = false,
    /** What was looked at: the slot, the day and the time. */
    val mealTitle: String = "",
    /** The facts of the last hours. */
    val summary: List<KeyValueRow> = emptyList(),
    /** Whose values the list shows: the slot in use now, or the global values. */
    val parametersTitle: String = "",
    /** All AutoISF and SMB delivery numbers in use now; a changed value shows the suggestion: 1.60 → 1.50. */
    val parameters: List<KeyValueRow> = emptyList(),
    /** Plain notes shown above the suggestions: too few runs, settings changed, nothing found. */
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

    /** Also drops the result on screen: it belongs to the hours that were analyzed. */
    fun onHoursChange(hours: Int) {
        if (_uiState.value.running || hours !in 1..AutoIsfLogAnalysis.MAX_HOURS) return
        _uiState.value = AutoIsfLogUiState(hours = hours)
    }

    fun onAnalyze() {
        val hours = _uiState.value.hours
        if (_uiState.value.running) return
        _uiState.update { AutoIsfLogUiState(hours = hours, running = true) }
        scope.launch {
            val newState = try {
                buildState(analysis.analyzeRecent(hours))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AutoIsfLogUiState(hours = hours, hasResult = true, message = rh.gs(ApsStrings.autoisf_log_read_error, e.message ?: e.toString()))
            }
            _uiState.value = newState
        }
    }

    fun onApplyClick(item: AutoIsfSuggestionUi) {
        val slot = _uiState.value.slot ?: return
        val current = value(item.suggestion.param, item.suggestion.current)
        val suggested = value(item.suggestion.param, item.suggestion.suggested)
        val confirm = rh.gs(ApsStrings.autoisf_log_apply_confirm_slot, item.title, current, suggested, rh.gs(slot.titleRef()))
        _uiState.update { it.copy(pendingApply = item, confirmText = confirm) }
    }

    fun onApplyDismiss() {
        _uiState.update { it.copy(pendingApply = null) }
    }

    fun onApplyConfirm() {
        val item = _uiState.value.pendingApply ?: return
        val slot = _uiState.value.slot ?: return
        when (analysis.apply(item.suggestion, slot)) {
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
        val recent = result.recent
        val slot = result.slot
        if (recent == null)
            return AutoIsfLogUiState(hasResult = true, hours = result.hours, notes = listOf(rh.gs(ApsStrings.autoisf_log_no_recent_runs, result.hours)))

        val df = analysis.decimalFormatter
        val dateUtil = analysis.dateUtil
        val profileUtil = analysis.profileUtil
        fun pct(value: Double) = rh.gs(CoreUiStrings.units_format_percent_value, df.to1Decimal(value))
        fun delta(mgdl: Double): String {
            val value = profileUtil.fromMgdlToUnits(mgdl)
            val text = if (profileUtil.units == GlucoseUnit.MMOL) df.to1Decimal(value) else df.to0Decimal(value)
            return if (mgdl >= 0) "+$text" else text
        }
        fun minutes(value: Int) = rh.gs(CoreUiStrings.format_mins, value)

        val mealTitle = rh.gs(
            ApsStrings.autoisf_log_meal_title,
            slot?.let { rh.gs(it.titleRef()) } ?: rh.gs(ApsStrings.autoisf_log_title_recent, result.hours),
            dateUtil.dateStringShort(recent.start),
            dateUtil.timeString(recent.start),
            dateUtil.timeString(recent.end)
        )

        val summary = listOf(
            KeyValueRow(
                rh.gs(ApsStrings.autoisf_log_meal_peak),
                rh.gs(ApsStrings.autoisf_log_meal_peak_value, profileUtil.fromMgdlToStringWithUnits(recent.peakBg), dateUtil.timeString(recent.peakTime), delta(recent.peakAboveTarget))
            ),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_above, delta(AutoIsfRecentAnalyzer.ABOVE_1_MGDL)), minutes(recent.minutesAbove1)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_above, delta(AutoIsfRecentAnalyzer.ABOVE_2_MGDL)), minutes(recent.minutesAbove2)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_low_time, profileUtil.fromMgdlToStringWithUnits(AutoIsfLogAnalyzer.LOW_MGDL)), minutes(recent.minutesLow)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_end), delta(recent.endAboveTarget)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_smbs), rh.gs(ApsStrings.autoisf_log_smbs_value, recent.smbCount, df.to2Decimal(recent.smbUnits))),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_max_limited), pct(recent.maxLimitedPct)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_smb_at_max), pct(recent.smbAtMaxBolusPct)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_driver), driverName(recent.dominantDriver))
        )

        val notes = buildList {
            if (slot == null) add(rh.gs(ApsStrings.autoisf_log_mixed_values))
            if (result.slotsOff) add(rh.gs(ApsStrings.autoisf_log_slots_off))
            if (recent.hadTempTarget) add(rh.gs(ApsStrings.autoisf_log_meal_temp_target))
            if (recent.incomplete) add(rh.gs(ApsStrings.autoisf_log_meal_incomplete))
            report.settingsChangedAt?.let { add(rh.gs(ApsStrings.autoisf_log_settings_changed, dateUtil.dateAndTimeString(it))) }
            if (slot != null && recent.used && !result.slotsOff) {
                if (!report.enoughData)
                    add(rh.gs(ApsStrings.autoisf_log_not_enough_data, rh.gs(CoreUiStrings.format_hours, stats.hours), rh.gs(CoreUiStrings.format_hours, AutoIsfLogAnalysis.MIN_HOURS_RECENT)))
                else if (report.suggestions.isEmpty()) add(rh.gs(ApsStrings.autoisf_log_no_suggestions))
            }
        }

        val events = report.events.map { event ->
            rh.gs(
                if (event.outcome == AutoIsfOutcome.Low) ApsStrings.autoisf_log_event_low else ApsStrings.autoisf_log_event_predicted,
                dateUtil.timeString(event.smbTime),
                df.to2Decimal(event.smbUnits),
                profileUtil.fromMgdlToStringWithUnits(event.bgAtSmb),
                event.finalIsfFactor?.let { df.to2Decimal(it) } ?: "?",
                driverName(event.driver),
                profileUtil.fromMgdlToStringWithUnits(event.outcomeBg),
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

        val parameters = result.values.map { item ->
            val suggestion = item.param?.let { param -> report.suggestions.firstOrNull { it.param == param } }
            val now = if (item.whole) df.to0Decimal(item.value) else item.param?.let { value(it, item.value) } ?: df.to2Decimal(item.value)
            KeyValueRow(
                rh.gs(item.title),
                if (suggestion == null) now else rh.gs(ApsStrings.autoisf_log_change, now, value(suggestion.param, suggestion.suggested))
            )
        }

        return AutoIsfLogUiState(
            hours = result.hours,
            slot = slot,
            hasResult = true,
            mealTitle = mealTitle,
            summary = summary,
            parametersTitle = rh.gs(ApsStrings.autoisf_log_parameters_of, result.slotNow?.let { rh.gs(it.titleRef()) } ?: rh.gs(ApsStrings.autoisf_log_global_values)),
            parameters = parameters,
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

/** The name of a meal slot on the screen. */
fun AutoIsfSlots.Slot.titleRef(): TextRef = when (this) {
    AutoIsfSlots.Slot.BREAKFAST         -> ApsStrings.autoisf_slot_breakfast
    AutoIsfSlots.Slot.LUNCH             -> ApsStrings.autoisf_slot_lunch
    AutoIsfSlots.Slot.DINNER            -> ApsStrings.autoisf_slot_dinner
    AutoIsfSlots.Slot.BREAKFAST_WEEKEND -> ApsStrings.autoisf_slot_breakfast_weekend
    AutoIsfSlots.Slot.LUNCH_WEEKEND     -> ApsStrings.autoisf_slot_lunch_weekend
    AutoIsfSlots.Slot.DINNER_WEEKEND    -> ApsStrings.autoisf_slot_dinner_weekend
    AutoIsfSlots.Slot.NIGHT             -> ApsStrings.autoisf_slot_night
}
