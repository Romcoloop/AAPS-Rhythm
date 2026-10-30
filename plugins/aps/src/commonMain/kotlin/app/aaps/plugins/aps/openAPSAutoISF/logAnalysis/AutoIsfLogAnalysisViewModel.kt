package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.InterfacesStrings
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
    val days: Int = AutoIsfLogAnalysis.DEFAULT_DAYS,
    /** Meal slot to analyze, null = whole day. */
    val slot: AutoIsfSlots.Slot? = null,
    val running: Boolean = false,
    val hasResult: Boolean = false,
    val summary: List<KeyValueRow> = emptyList(),
    /** Meal statistics of the slot, empty for the whole day. */
    val meals: List<KeyValueRow> = emptyList(),
    /** One line per meal. */
    val mealLines: List<String> = emptyList(),
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

    /**
     * Also drops the result on screen: its suggestions belong to the slot that was analyzed, and
     * "Apply" writes to the slot in the state. The two must never differ.
     */
    fun onSlotChange(slot: AutoIsfSlots.Slot?) {
        if (_uiState.value.running) return
        _uiState.update { AutoIsfLogUiState(days = it.days, slot = slot) }
    }

    fun onAnalyze() {
        if (_uiState.value.running) return
        val days = _uiState.value.days
        val slot = _uiState.value.slot
        _uiState.update { AutoIsfLogUiState(days = days, slot = slot, running = true) }
        scope.launch {
            val newState = try {
                buildState(analysis.analyze(days, slot))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AutoIsfLogUiState(days = days, slot = slot, hasResult = true, message = rh.gs(ApsStrings.autoisf_log_read_error, e.message ?: e.toString()))
            }
            _uiState.value = newState
        }
    }

    fun onApplyClick(item: AutoIsfSuggestionUi) {
        val slot = _uiState.value.slot
        val current = value(item.suggestion.param, item.suggestion.current)
        val suggested = value(item.suggestion.param, item.suggestion.suggested)
        val confirm =
            if (slot == null) rh.gs(ApsStrings.autoisf_log_apply_confirm, item.title, current, suggested)
            else rh.gs(ApsStrings.autoisf_log_apply_confirm_slot, item.title, current, suggested, rh.gs(slot.titleRef()))
        _uiState.update { it.copy(pendingApply = item, confirmText = confirm) }
    }

    fun onApplyDismiss() {
        _uiState.update { it.copy(pendingApply = null) }
    }

    fun onApplyConfirm() {
        val item = _uiState.value.pendingApply ?: return
        when (analysis.apply(item.suggestion, _uiState.value.slot)) {
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
        if (result.noMealTime)
            return AutoIsfLogUiState(days = result.days, slot = result.slot, hasResult = true, notes = listOf(rh.gs(ApsStrings.autoisf_log_no_meal_time)))

        val df = analysis.decimalFormatter
        val dateUtil = analysis.dateUtil
        val profileUtil = analysis.profileUtil
        fun pct(value: Double) = rh.gs(CoreUiStrings.units_format_percent_value, df.to1Decimal(value))
        fun delta(mgdl: Double): String {
            val value = profileUtil.fromMgdlToUnits(mgdl)
            val text = if (profileUtil.units == GlucoseUnit.MMOL) df.to1Decimal(value) else df.to0Decimal(value)
            return if (mgdl >= 0) "+$text" else text
        }
        fun minutes(value: Double) = rh.gs(CoreUiStrings.format_mins, value.toInt())

        val summary = if (stats.runs == 0) emptyList() else listOf(
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

        val mealStats = result.mealStats
        val meals = if (result.slot == null || mealStats == null) emptyList() else listOf(
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meals), rh.gs(ApsStrings.autoisf_log_meals_value, mealStats.meals, mealStats.excluded)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_peak), delta(mealStats.meanPeakAboveTarget)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_time_to_peak), rh.gs(CoreUiStrings.format_mins, mealStats.medianMinutesToPeak)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_above, delta(AutoIsfMealAnalyzer.ABOVE_1_MGDL)), minutes(mealStats.meanMinutesAbove1)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_above, delta(AutoIsfMealAnalyzer.ABOVE_2_MGDL)), minutes(mealStats.meanMinutesAbove2)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_lows), rh.gs(ApsStrings.autoisf_log_meal_lows_value, mealStats.mealsWithLow, mealStats.meals)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_end), delta(mealStats.meanEndAboveTarget)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_smb), rh.gs(InterfacesStrings.format_insulin_units, mealStats.meanSmbUnits)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_max_limited), pct(mealStats.maxLimitedPct)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_smb_at_max), pct(mealStats.smbAtMaxBolusPct)),
            KeyValueRow(rh.gs(ApsStrings.autoisf_log_meal_driver), driverName(mealStats.dominantDriver))
        )

        val mealLines = result.meals.map { meal ->
            val line = rh.gs(
                ApsStrings.autoisf_log_meal_line,
                dateUtil.dateStringShort(meal.mealStart),
                dateUtil.timeString(meal.mealStart),
                profileUtil.fromMgdlToStringWithUnits(meal.peakBg),
                dateUtil.timeString(meal.peakTime),
                profileUtil.fromMgdlToStringWithUnits(meal.endBg),
                rh.gs(InterfacesStrings.format_insulin_units, meal.smbUnits),
                rh.gs(if (meal.hadLow) CoreUiStrings.yes else CoreUiStrings.no)
            )
            when {
                meal.hadTempTarget -> rh.gs(ApsStrings.autoisf_log_meal_line_temp_target, line)
                meal.incomplete    -> rh.gs(ApsStrings.autoisf_log_meal_line_incomplete, line)
                else               -> line
            }
        }

        val minHours = if (result.slot == null) AutoIsfLogAnalyzer.MIN_HOURS else AutoIsfLogAnalysis.MIN_HOURS_SLOT
        val notes = buildList {
            if (stats.runs == 0) add(rh.gs(ApsStrings.autoisf_log_no_runs))
            if (result.slotsOff) add(rh.gs(ApsStrings.autoisf_log_slots_off))
            report.settingsChangedAt?.let { add(rh.gs(ApsStrings.autoisf_log_settings_changed, dateUtil.dateAndTimeString(it))) }
            if (stats.runs > 0 && !result.slotsOff) {
                if (!report.enoughData)
                    add(rh.gs(ApsStrings.autoisf_log_not_enough_data, rh.gs(CoreUiStrings.format_hours, stats.hours), rh.gs(CoreUiStrings.format_hours, minHours)))
                else if (report.suggestions.isEmpty()) add(rh.gs(ApsStrings.autoisf_log_no_suggestions))
            }
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
            slot = result.slot,
            hasResult = true,
            summary = summary,
            meals = meals,
            mealLines = mealLines,
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
    AutoIsfSlots.Slot.BREAKFAST -> ApsStrings.autoisf_slot_breakfast
    AutoIsfSlots.Slot.LUNCH     -> ApsStrings.autoisf_slot_lunch
    AutoIsfSlots.Slot.DINNER    -> ApsStrings.autoisf_slot_dinner
}
