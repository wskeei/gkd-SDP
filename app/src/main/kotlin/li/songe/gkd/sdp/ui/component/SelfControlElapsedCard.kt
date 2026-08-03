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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy

@Composable
fun SelfControlElapsedCard(
    context: SelfControlElapsedPolicy.Context,
    state: SelfControlElapsedPolicy.ElapsedState,
    recentCompletedIntervalsMs: List<Long> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val copy = remember(context) { SelfControlElapsedPolicy.copyFor(context) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = copy.title,
                style = MaterialTheme.typography.titleMedium,
            )
            when (state) {
                SelfControlElapsedPolicy.ElapsedState.Loading -> {
                    Text(
                        text = "正在读取上次记录…",
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
                        text = "暂时无法读取上次记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                is SelfControlElapsedPolicy.ElapsedState.Running -> {
                    RunningElapsedContent(
                        copy = copy,
                        state = state,
                        recentCompletedIntervalsMs = recentCompletedIntervalsMs,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningElapsedContent(
    copy: SelfControlElapsedPolicy.Copy,
    state: SelfControlElapsedPolicy.ElapsedState.Running,
    recentCompletedIntervalsMs: List<Long>,
) {
    var nowEpochMs by remember(state.anchorAtEpochMs) {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(state.anchorAtEpochMs) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Text(
        text = SelfControlElapsedPolicy.formatElapsed(
            anchorAtEpochMs = state.anchorAtEpochMs,
            nowEpochMs = nowEpochMs,
        ),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        text = "${if (state.firstOccurrence) copy.firstTimeLabel else copy.previousTimeLabel}：" +
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

    SelfControlIntervalInsightCard(
        insight = SelfControlIntervalPolicy.overlayInsight(
            anchorAtEpochMs = state.anchorAtEpochMs,
            firstOccurrence = state.firstOccurrence,
            recentCompletedIntervalsMs = recentCompletedIntervalsMs,
            nowEpochMs = nowEpochMs,
        ),
    )
}
