package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy

data class UsageRequestRhythmPresentation(
    val status: Status,
    val currentGapMs: Long?,
    val currentRatio: Double?,
    val averageRatioByWindow: Map<SelfControlInsightWindowPolicy.Window, Double?>,
    val ratioSampleCountByWindow: Map<SelfControlInsightWindowPolicy.Window, Int>,
    val comparisonText: String?,
    val statusText: String,
) {
    enum class Status {
        FIRST,
        AVAILABLE,
        MISSING_ACTUAL_END,
        UNAVAILABLE,
    }

    companion object {
        fun from(
            data: SelfControlIntervalRepository.UsageRequestOverlayData?,
            nowEpochMs: Long,
            requestedDurationMinutes: Int,
            selectedWindow: SelfControlInsightWindowPolicy.Window =
                SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
        ): UsageRequestRhythmPresentation {
            val status = when {
                data == null -> Status.UNAVAILABLE
                data.anchorStatus == SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest -> Status.FIRST
                data.anchorStatus == SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd -> Status.MISSING_ACTUAL_END
                else -> Status.AVAILABLE
            }
            val currentGap = data?.previousLastUsageEndedAt?.let {
                UsageRequestRhythmPolicy.gapMs(it, nowEpochMs)
            }
            val averages = SelfControlInsightWindowPolicy.Window.entries.associateWith { window ->
                SelfControlInsightWindowPolicy.aggregate(
                    samples = data?.samples.orEmpty(),
                    nowEpochMs = data?.insightAnchorAt ?: nowEpochMs,
                    window = window,
                    metric = SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
                ).stats.averageRatio
            }
            val counts = SelfControlInsightWindowPolicy.Window.entries.associateWith { window ->
                SelfControlInsightWindowPolicy.aggregate(
                    samples = data?.samples.orEmpty(),
                    nowEpochMs = data?.insightAnchorAt ?: nowEpochMs,
                    window = window,
                    metric = SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
                ).stats.sampleCount
            }
            val currentRatio = UsageRequestRhythmPolicy.currentRatio(
                currentGap,
                requestedDurationMinutes,
            )
            val baseline = averages[selectedWindow]
            val comparison = if (currentRatio != null && baseline != null) {
                val delta = currentRatio - baseline
                when {
                    delta > 0.0 -> "本次比${selectedWindow.label}平均高 ${formatRatioDelta(delta)}"
                    delta < 0.0 -> "本次比${selectedWindow.label}平均低 ${formatRatioDelta(-delta)}"
                    else -> "本次与${selectedWindow.label}平均相同"
                }
            } else {
                null
            }
            val statusText = when (status) {
                Status.FIRST -> "此前没有成功的使用申请"
                Status.AVAILABLE -> if (data?.samples.orEmpty().none { it.gapMs != null }) {
                    "间用比新口径从本版本开始积累"
                } else {
                    "未使用间隔从上次结束使用开始计算"
                }
                Status.MISSING_ACTUAL_END -> "暂无可确认的上次结束时间"
                Status.UNAVAILABLE -> "暂时无法读取上次结束时间"
            }
            return UsageRequestRhythmPresentation(
                status = status,
                currentGapMs = currentGap,
                currentRatio = currentRatio,
                averageRatioByWindow = averages,
                ratioSampleCountByWindow = counts,
                comparisonText = comparison,
                statusText = statusText,
            )
        }

        private fun formatRatioDelta(value: Double): String =
            "${UsageRequestRhythmPolicy.formatRatio(value) ?: "暂无"}×"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
fun UsageRequestRhythmSummary(
    presentation: UsageRequestRhythmPresentation,
    selectedWindow: SelfControlInsightWindowPolicy.Window,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalDivider()
        Text("本次间用比", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "本次选择时长：${presentation.currentRatio?.let { "${UsageRequestRhythmPolicy.formatRatio(it)}×" } ?: "—"}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "距离上次结束使用：${presentation.currentGapMs?.let(SelfControlIntervalPolicy::formatDurationCompact) ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "间用比 = 未使用间隔 ÷ 本次申请时长",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            presentation.averageRatioByWindow.forEach { (window, average) ->
                Text(
                    text = "${window.label}平均：${average?.let { "${UsageRequestRhythmPolicy.formatRatio(it)}×" } ?: "—"}（${presentation.ratioSampleCountByWindow[window] ?: 0} 条）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        presentation.comparisonText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = presentation.statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
