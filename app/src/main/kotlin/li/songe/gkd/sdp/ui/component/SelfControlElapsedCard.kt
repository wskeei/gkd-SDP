package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun SelfControlElapsedCard(
    context: SelfControlElapsedPolicy.Context,
    state: SelfControlElapsedPolicy.ElapsedState,
    modifier: Modifier = Modifier,
    samples: List<SelfControlInsightWindowPolicy.IntervalSample> = emptyList(),
    insightAnchorAt: Long? = null,
    selectedWindow: SelfControlInsightWindowPolicy.Window =
        SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
    onWindowSelected: (SelfControlInsightWindowPolicy.Window) -> Unit = {},
    selectedMetric: SelfControlInsightWindowPolicy.Metric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
    onMetricSelected: (SelfControlInsightWindowPolicy.Metric) -> Unit = {},
    supportsUsageRatio: Boolean = false,
    currentReference: SelfControlInsightCurrentReference? = null,
    nowEpochMs: Long? = null,
) {
    val copy = remember(context) { SelfControlElapsedPolicy.copyFor(context) }

    Card(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = copy.title,
                style = MaterialTheme.typography.titleMedium,
            )
            when (state) {
                SelfControlElapsedPolicy.ElapsedState.Loading -> {
                    Text(
                        text = stringResource(R.string.s_5bf8fdca8e),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                SelfControlElapsedPolicy.ElapsedState.NoHistory -> {
                    Text(
                        text = copy.noHistoryText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = copy.firstSupportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                SelfControlElapsedPolicy.ElapsedState.Unavailable -> {
                    Text(
                        text = stringResource(R.string.s_9e99477b43),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                SelfControlElapsedPolicy.ElapsedState.MissingActualEnd -> {
                    Text(
                        text = stringResource(R.string.s_196c9660e0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                is SelfControlElapsedPolicy.ElapsedState.Running -> {
                    RunningElapsedContent(
                        copy = copy,
                        state = state,
                        samples = samples,
                        insightAnchorAt = insightAnchorAt,
                        selectedWindow = selectedWindow,
                        onWindowSelected = onWindowSelected,
                        selectedMetric = selectedMetric,
                        onMetricSelected = onMetricSelected,
                        supportsUsageRatio = supportsUsageRatio,
                        currentReference = currentReference,
                        nowEpochMsOverride = nowEpochMs,
                    )
                }
            }
            if (state !is SelfControlElapsedPolicy.ElapsedState.Running && samples.isNotEmpty()) {
        SelfControlIntervalInsightCard(
            samples = samples,
            insightAnchorAt = insightAnchorAt ?: nowEpochMs ?: System.currentTimeMillis(),
            selectedWindow = selectedWindow,
            onWindowSelected = onWindowSelected,
            selectedMetric = selectedMetric,
            onMetricSelected = onMetricSelected,
            supportsUsageRatio = supportsUsageRatio,
            currentReference = currentReference,
        )
            }
        }
    }
}

@Composable
private fun RunningElapsedContent(
    copy: SelfControlElapsedPolicy.Copy,
    state: SelfControlElapsedPolicy.ElapsedState.Running,
    samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
    insightAnchorAt: Long?,
    selectedWindow: SelfControlInsightWindowPolicy.Window,
    onWindowSelected: (SelfControlInsightWindowPolicy.Window) -> Unit,
    selectedMetric: SelfControlInsightWindowPolicy.Metric,
    onMetricSelected: (SelfControlInsightWindowPolicy.Metric) -> Unit,
    supportsUsageRatio: Boolean,
    currentReference: SelfControlInsightCurrentReference?,
    nowEpochMsOverride: Long?,
) {
    var tickerNowEpochMs by remember(state.anchorAtEpochMs) {
        mutableLongStateOf(System.currentTimeMillis())
    }

    if (nowEpochMsOverride == null) {
        LaunchedEffect(state.anchorAtEpochMs) {
            while (true) {
                tickerNowEpochMs = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }
    val nowEpochMs = nowEpochMsOverride ?: tickerNowEpochMs

    Text(
        text = SelfControlElapsedPolicy.formatElapsed(
            anchorAtEpochMs = state.anchorAtEpochMs,
            nowEpochMs = nowEpochMs,
        ),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        text = stringResource(R.string.s_de27714146, (if (state.firstOccurrence) copy.firstTimeLabel else copy.previousTimeLabel).toString()) +
            SelfControlElapsedPolicy.formatAbsolute(state.anchorAtEpochMs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
    Text(
        text = if (state.firstOccurrence) copy.firstSupportingText else copy.supportingText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )

    if (samples.isNotEmpty()) {
        SelfControlIntervalInsightCard(
            samples = samples,
            insightAnchorAt = insightAnchorAt ?: nowEpochMs,
            selectedWindow = selectedWindow,
            onWindowSelected = onWindowSelected,
            selectedMetric = selectedMetric,
            onMetricSelected = onMetricSelected,
            supportsUsageRatio = supportsUsageRatio,
            currentReference = currentReference,
        )
    }
}
