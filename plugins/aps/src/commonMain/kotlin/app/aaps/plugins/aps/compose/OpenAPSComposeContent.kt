package app.aaps.plugins.aps.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.aps.openAPSAutoISF.logAnalysis.AutoIsfLogAnalysis
import app.aaps.plugins.aps.openAPSAutoISF.logAnalysis.AutoIsfLogAnalysisCard
import app.aaps.plugins.aps.openAPSAutoISF.logAnalysis.AutoIsfLogAnalysisViewModel

class OpenAPSComposeContent(
    private val apsPlugin: APS,
    private val rxBus: RxBus,
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    /** Only AutoISF has it. Null hides the log analysis card. */
    private val logAnalysis: AutoIsfLogAnalysis? = null
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val scope = rememberCoroutineScope()
        val viewModel = remember {
            OpenAPSViewModel(
                apsPlugin = apsPlugin,
                rxBus = rxBus,
                rh = rh,
                dateUtil = dateUtil,
                scope = scope
            )
        }

        val state = viewModel.uiState.collectAsStateWithLifecycle()

        val logViewModel = logAnalysis?.let { analysis ->
            remember { AutoIsfLogAnalysisViewModel(analysis = analysis, rh = rh, scope = scope) }
        }
        val logState = logViewModel?.uiState?.collectAsStateWithLifecycle()

        OpenAPSScreen(
            state = state.value,
            onRefresh = viewModel::onRefresh,
            header = if (logViewModel != null && logState != null) {
                {
                    AutoIsfLogAnalysisCard(
                        state = logState.value,
                        onDaysChange = logViewModel::onDaysChange,
                        onAnalyze = logViewModel::onAnalyze,
                        onApplyClick = logViewModel::onApplyClick,
                        onApplyConfirm = logViewModel::onApplyConfirm,
                        onApplyDismiss = logViewModel::onApplyDismiss
                    )
                }
            } else null
        )
    }
}
