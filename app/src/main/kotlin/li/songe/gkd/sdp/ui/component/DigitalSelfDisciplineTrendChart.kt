package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.max
import li.songe.gkd.sdp.R

@Composable
fun DigitalSelfDisciplineTrendChart(
    presentation: DigitalSelfDisciplineReviewPresentation.TrendPresentation,
    modifier: Modifier = Modifier,
) {
    var detailsExpanded by remember(presentation) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrendHeadline("本期平均", presentation.currentAverageText, Modifier.weight(1f))
            TrendHeadline("上一周期", presentation.previousAverageText, Modifier.weight(1f))
            TrendHeadline("差值", presentation.deltaText, Modifier.weight(1f))
        }
        Text(
            text = presentation.coverageText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (presentation.empty) {
            Text(
                text = DigitalSelfDisciplineReviewPresentation.emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val primary = MaterialTheme.colorScheme.primary
            val minValue = presentation.points.minOf { it.value }
            val maxValue = presentation.points.maxOf { it.value }
            val axisUnit = DigitalSelfDisciplineReviewPresentation.axisUnitLabel(presentation.metric)
            Text(
                text = li.songe.gkd.sdp.app.getString(R.string.s_47d3fa79b4, (presentation.metricLabel).toString(), (axisUnit).toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_d49e418af8, (DigitalSelfDisciplineReviewPresentation.formatTrendValue(maxValue, presentation.metric)).toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_37feaa9b99, (DigitalSelfDisciplineReviewPresentation.formatTrendValue(minValue, presentation.metric)).toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .semantics { contentDescription = presentation.semanticSummary },
            ) {
                val values = presentation.points.map { it.value }
                val minValue = values.minOrNull() ?: 0.0
                val maxValue = values.maxOrNull() ?: 1.0
                val span = max(maxValue - minValue, 1.0)
                val left = 18f
                val right = (size.width - 12f).coerceAtLeast(left + 1f)
                val top = 12f
                val bottom = size.height - 20f
                val xStep = if (presentation.points.size <= 1) 0f else (right - left) / (presentation.points.lastIndex)
                val coords = presentation.points.mapIndexed { index, point ->
                    Offset(
                        x = left + xStep * index,
                        y = bottom - ((point.value - minValue) / span).toFloat() * (bottom - top),
                    )
                }
                if (coords.size > 1) {
                    val path = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        coords.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, color = primary, style = Stroke(width = 3f))
                }
                coords.forEach { drawCircle(color = primary, radius = 5f, center = it) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DigitalSelfDisciplineReviewPresentation.xAxisLabels(presentation.points).forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            TextButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.semantics {
                    contentDescription = if (detailsExpanded) li.songe.gkd.sdp.app.getString(R.string.s_e182c1f7ff) else li.songe.gkd.sdp.app.getString(R.string.s_55071d1cf5)
                },
            ) {
                Text(if (detailsExpanded) li.songe.gkd.sdp.app.getString(R.string.s_e182c1f7ff) else li.songe.gkd.sdp.app.getString(R.string.s_55071d1cf5))
            }
            if (detailsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    presentation.textRows.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun TrendHeadline(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
