package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import java.time.format.DateTimeFormatter

@Composable
fun SelfControlReviewChart(
    summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
    modifier: Modifier = Modifier,
) {
    val points = if (summary.range == DigitalSelfDisciplineReviewPolicy.Range.Today) {
        summary.recentIntervals.mapIndexed { index, item ->
            SelfControlIntervalPresentation.ChartPoint(
                label = "${index + 1}",
                valueMs = item.intervalMs,
                isCurrent = false,
            )
        }
    } else {
        summary.dailyBuckets.map { bucket ->
            SelfControlIntervalPresentation.ChartPoint(
                label = bucket.date.format(DateTimeFormatter.ofPattern("MM-dd")),
                valueMs = bucket.medianMs,
                isCurrent = false,
            )
        }
    }
    if (points.isEmpty()) {
        Text("暂无可绘制的有效间隔", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val summaryText = buildString {
        append("${summary.reviewType.label}间隔图表，共 ${summary.stats.sampleCount} 个有效样本")
        summary.stats.averageMs?.let {
            append("，平均 ")
            append(SelfControlIntervalPolicy.formatDurationCompact(it))
        }
        summary.stats.medianMs?.let {
            append("，中位数 ")
            append(SelfControlIntervalPolicy.formatDurationCompact(it))
        }
    }
    SelfControlIntervalChart(
        points = points,
        semanticSummary = summaryText,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp),
    )
}
