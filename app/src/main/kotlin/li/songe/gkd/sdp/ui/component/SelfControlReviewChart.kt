package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import java.time.temporal.ChronoUnit

@Composable
fun SelfControlReviewChart(
    summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
    modifier: Modifier = Modifier,
) {
    val rawPoints = DigitalSelfDisciplineReviewPresentation.chartPoints(summary)
    val firstBucketDate = summary.dailyBuckets.firstOrNull()?.date
    val points = rawPoints.mapIndexed { index, point ->
        val shouldShowLabel = if (summary.range == DigitalSelfDisciplineReviewPolicy.Range.ThirtyDays) {
            val date = summary.dailyBuckets.getOrNull(index)?.date
            date != null && firstBucketDate != null &&
                ChronoUnit.DAYS.between(firstBucketDate, date) % 5L == 0L
        } else {
            true
        }
        SelfControlIntervalPresentation.ChartPoint(
            label = if (shouldShowLabel) point.label else "",
            valueMs = point.valueMs,
            isCurrent = false,
        )
    }
    if (points.isEmpty()) {
        Text(DigitalSelfDisciplineReviewPresentation.emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (summary.range != DigitalSelfDisciplineReviewPolicy.Range.Today) {
        Column(
            modifier = Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            summary.dailyBuckets.forEach { bucket ->
                Text(
                    text = "${bucket.date}：${bucket.sampleCount} 个，平均 " +
                        "${SelfControlIntervalPolicy.formatDurationCompact(bucket.averageMs)}，中位数 " +
                        SelfControlIntervalPolicy.formatDurationCompact(bucket.medianMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
