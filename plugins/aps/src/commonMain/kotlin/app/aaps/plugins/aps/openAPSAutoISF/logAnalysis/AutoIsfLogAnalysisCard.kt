package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.compose.KeyValueRowItem
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfSlots

/** The "AutoISF log analysis" card on the AutoISF plugin screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoIsfLogAnalysisCard(
    state: AutoIsfLogUiState,
    onDaysChange: (Int) -> Unit,
    onSlotChange: (AutoIsfSlots.Slot?) -> Unit,
    onAnalyze: () -> Unit,
    onApplyClick: (AutoIsfSuggestionUi) -> Unit,
    onApplyConfirm: () -> Unit,
    onApplyDismiss: () -> Unit
) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Text(
                text = stringResource(ApsStrings.autoisf_log_analysis),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(ApsStrings.autoisf_log_analysis_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoIsfLogAnalysis.dayChoices(state.slot).forEach { days ->
                    FilterChip(
                        selected = state.days == days,
                        onClick = { onDaysChange(days) },
                        enabled = !state.running,
                        label = { Text(stringResource(ApsStrings.autoisf_log_days, days)) }
                    )
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onAnalyze, enabled = !state.running) {
                        Text(stringResource(ApsStrings.autoisf_log_analyze))
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                FilterChip(
                    selected = state.slot == null,
                    onClick = { onSlotChange(null) },
                    enabled = !state.running,
                    label = { Text(stringResource(ApsStrings.autoisf_slot_all)) }
                )
                AutoIsfSlots.Slot.entries.forEach { slot ->
                    FilterChip(
                        selected = state.slot == slot,
                        onClick = { onSlotChange(slot) },
                        enabled = !state.running,
                        label = { Text(stringResource(slot.titleRef())) }
                    )
                }
            }

            if (state.running) {
                Text(
                    text = stringResource(ApsStrings.autoisf_log_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.message?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            if (state.hasResult) {
                if (state.summary.isNotEmpty()) {
                    SectionTitle(stringResource(ApsStrings.autoisf_log_summary))
                    state.summary.forEachIndexed { index, row ->
                        KeyValueRowItem(row)
                        if (index < state.summary.lastIndex) ItemDivider()
                    }
                }

                if (state.meals.isNotEmpty()) {
                    SectionTitle(stringResource(ApsStrings.autoisf_log_meals_section))
                    state.meals.forEachIndexed { index, row ->
                        KeyValueRowItem(row)
                        if (index < state.meals.lastIndex) ItemDivider()
                    }
                }

                state.notes.forEach { note ->
                    Text(text = note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }

                if (state.mealLines.isNotEmpty()) {
                    SectionTitle(stringResource(ApsStrings.autoisf_log_meal_lines))
                    state.mealLines.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (state.events.isNotEmpty()) {
                    SectionTitle(stringResource(ApsStrings.autoisf_log_events))
                    state.events.forEach { event ->
                        Text(text = event, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (state.suggestions.isNotEmpty()) {
                    SectionTitle(stringResource(ApsStrings.autoisf_log_suggestions))
                    state.suggestions.forEachIndexed { index, item ->
                        SuggestionItem(item = item, onApplyClick = { onApplyClick(item) })
                        if (index < state.suggestions.lastIndex) ItemDivider()
                    }
                }
            }
        }
    }

    state.pendingApply?.let {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = state.confirmText,
            onConfirm = onApplyConfirm,
            onDismiss = onApplyDismiss
        )
    }
}

@Composable
private fun SuggestionItem(item: AutoIsfSuggestionUi, onApplyClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.change,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedButton(onClick = onApplyClick, enabled = !item.applied) {
                Text(stringResource(if (item.applied) ApsStrings.autoisf_log_applied else ApsStrings.autoisf_log_apply))
            }
        }
        Text(
            text = item.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(top = AapsSpacing.medium)
            .semantics { heading() }
    )
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = AapsSpacing.small),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
