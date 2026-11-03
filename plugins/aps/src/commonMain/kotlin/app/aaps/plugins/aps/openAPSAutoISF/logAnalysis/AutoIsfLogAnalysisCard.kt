package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import kotlin.math.roundToInt

/** The "AutoISF log analysis" card on the AutoISF plugin screen. */
@Composable
fun AutoIsfLogAnalysisCard(
    state: AutoIsfLogUiState,
    onHoursChange: (Int) -> Unit,
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
                Text(
                    text = stringResource(ApsStrings.autoisf_log_hours, state.hours),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                AutoIsfLogAnalysis.HOURS_CHOICES.forEach { hours ->
                    FilterChip(
                        selected = state.hours == hours,
                        onClick = { onHoursChange(hours) },
                        enabled = !state.running,
                        label = { Text(stringResource(ApsStrings.autoisf_log_hours, hours)) }
                    )
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onAnalyze, enabled = !state.running) {
                        Text(stringResource(ApsStrings.autoisf_log_analyze))
                    }
                }
            }
            Slider(
                value = state.hours.toFloat(),
                onValueChange = { onHoursChange(it.roundToInt()) },
                valueRange = 1f..AutoIsfLogAnalysis.MAX_HOURS.toFloat(),
                steps = AutoIsfLogAnalysis.MAX_HOURS - 2,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth()
            )

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
                if (state.mealTitle.isNotEmpty()) {
                    SectionTitle(state.mealTitle)
                }
                state.summary.forEachIndexed { index, row ->
                    KeyValueRowItem(row)
                    if (index < state.summary.lastIndex) ItemDivider()
                }

                state.notes.forEach { note ->
                    Text(text = note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }

                if (state.parameters.isNotEmpty()) {
                    SectionTitle(state.parametersTitle)
                    state.parameters.forEachIndexed { index, row ->
                        KeyValueRowItem(row)
                        if (index < state.parameters.lastIndex) ItemDivider()
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
