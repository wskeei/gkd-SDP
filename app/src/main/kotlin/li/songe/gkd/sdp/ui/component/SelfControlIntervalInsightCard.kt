package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy

data class SelfControlIntervalPresentation(
    val chartPoints: List<ChartPoint>,
    val stats: SelfControlIntervalPolicy.Stats,
    val comparisonText: String?,
    val supportingText: String,
    val semanticSummary: String,
) {
    data class ChartPoint(
        val label: String,
        val valueMs: Long,
        val isCurrent: Boolean,
    )

    companion object {
        fun from(insight: SelfControlIntervalPolicy.OverlayInsight): SelfControlIntervalPresentation {
            val history = insight.recentCompletedIntervalsMs
            val points = history.mapIndexed { index, value ->
                ChartPoint(
                    label = "第${index + 1}次",
                    valueMs = value,
                    isCurrent = false,
                )
            }.toMutableList()
            // With no completed interval, a zero-length current bar would imply a real
            // measurement. Keep the first-use state textual until a completed sample exists.
            if (history.isNotEmpty() && insight.currentElapsedMs != null) {
                points += ChartPoint("本次", insight.currentElapsedMs, isCurrent = true)
            }

            val stats = insight.stats
            val comparisonText = insight.comparison?.let { comparison ->
                val delta = comparison.deltaMs
                when {
                    delta > 0L -> "本次间隔比平均值多 ${SelfControlIntervalPolicy.formatDurationCompact(delta)}"
                    delta < 0L -> "本次间隔比平均值少 ${SelfControlIntervalPolicy.formatDurationCompact(-delta)}"
                    else -> "本次间隔与平均值相同"
                }
            }
            val supportingText = when {
                history.isEmpty() -> "完成下一次申请或拦截后，这里会显示最近的间隔。"
                stats.sampleCount == 1 -> "目前只有 1 个已完成间隔，继续记录后趋势会更稳定。"
                stats.minMs != null && stats.maxMs != null && stats.minMs > 0L &&
                    stats.maxMs / stats.minMs >= 30L -> "最近间隔跨度较大，平均值请结合中位数一起看。"
                else -> "历史柱按发生顺序排列，本次柱会随时间增长。"
            }
            val semanticSummary = if (stats.sampleCount == 0 || stats.averageMs == null || stats.medianMs == null) {
                "暂无已完成间隔；完成下一次后会开始统计。"
            } else {
                buildString {
                    append("最近 ${stats.sampleCount} 个间隔，平均 ")
                    append(SelfControlIntervalPolicy.formatDurationCompact(stats.averageMs))
                    append("，中位数 ")
                    append(SelfControlIntervalPolicy.formatDurationCompact(stats.medianMs))
                    comparisonText?.let {
                        append("；")
                        append(it)
                    }
                }
            }

            return SelfControlIntervalPresentation(
                chartPoints = points,
                stats = stats,
                comparisonText = comparisonText,
                supportingText = supportingText,
                semanticSummary = semanticSummary,
            )
        }
    }
}

@Composable
fun SelfControlIntervalInsightCard(
    insight: SelfControlIntervalPolicy.OverlayInsight,
    modifier: Modifier = Modifier,
) {
    val presentation = SelfControlIntervalPresentation.from(insight)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(
            text = "最近间隔",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = presentation.semanticSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        presentation.comparisonText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (presentation.chartPoints.isNotEmpty()) {
            SelfControlIntervalChart(
                points = presentation.chartPoints,
                semanticSummary = presentation.semanticSummary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                presentation.chartPoints.forEach { point ->
                    Text(
                        text = "${point.label}：${SelfControlIntervalPolicy.formatDurationCompact(point.valueMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = presentation.supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
