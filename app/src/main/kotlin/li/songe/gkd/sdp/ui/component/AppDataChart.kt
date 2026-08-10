package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.style.DimensionTokens
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

/** One time bucket shown in a chart. */
data class ChartBucket(
    val label: String,
    val value: Double,
    val sampleCount: Int,
    val hasValue: Boolean,
)

/** One row of the semantic data table. */
data class ChartDataRow(
    val label: String,
    val valueText: String,
    val unit: String,
    val status: String,
)

object AppDataTablePolicy {
    /** Builds stable table rows from buckets. */
    fun rows(
        buckets: List<ChartBucket>,
        unit: String,
        formatValue: (Double) -> String,
    ): List<ChartDataRow> = buckets.map { bucket ->
        ChartDataRow(
            label = bucket.label,
            valueText = if (bucket.hasValue) formatValue(bucket.value) else "—",
            unit = if (bucket.hasValue) unit else "",
            status = if (bucket.hasValue) "样本 ${bucket.sampleCount}" else "无数据",
        )
    }

    /** The single string used by both the touch detail and TalkBack. */
    fun pointDetailText(row: ChartDataRow): String =
        "${row.label}：${row.valueText}${row.unit}，${row.status}"
}

/**
 * A chart block that always ships a visual, a summary line, a touch detail
 * and a semantic data table. [summaryText] is read by TalkBack at the parent
 * node; touch details and table rows share [AppDataTablePolicy.pointDetailText].
 */
@Composable
fun AppDataChart(
    title: String,
    summaryText: String,
    buckets: List<ChartBucket>,
    unit: String,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val rows = AppDataTablePolicy.rows(buckets, unit, formatValue)
    var selectedIndex by remember(buckets) { mutableStateOf(-1) }
    var showTable by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { showTable = !showTable }) {
                Text(if (showTable) stringResource(R.string.s_ca41e1bef4) else stringResource(R.string.s_8afda7e678))
            }
        }
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(DimensionTokens.SpacingBase))
        val maxValue = buckets.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 1.0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEachIndexed { index, bucket ->
                val heightFraction = if (bucket.hasValue) {
                    (bucket.value / maxValue).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            selectedIndex = if (selectedIndex == index) -1 else index
                        }
                        .padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (bucket.hasValue) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(if (heightFraction == 0.0) 0.02f else heightFraction.toFloat())
                                .background(
                                    color = if (selectedIndex == index) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    shape = RoundedCornerShape(3.dp),
                                ),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val detailRow = rows.getOrNull(selectedIndex)
        Text(
            text = if (detailRow != null) {
                AppDataTablePolicy.pointDetailText(detailRow)
            } else {
                stringResource(R.string.s_32d6755cac)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = DimensionTokens.SpacingSm),
        )
        if (showTable) {
            Spacer(modifier = Modifier.height(DimensionTokens.SpacingBase))
            HorizontalDivider()
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = li.songe.gkd.sdp.app.getString(R.string.s_896022e814, (row.valueText).toString(), (row.unit).toString()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(DimensionTokens.SpacingBase))
                    Text(
                        text = row.status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
