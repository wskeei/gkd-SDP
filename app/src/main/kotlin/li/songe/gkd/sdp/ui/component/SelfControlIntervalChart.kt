package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy
import li.songe.gkd.sdp.util.LogUtils

/** Dataset-backed chart used by request and interception overlays. */
@Composable
fun SelfControlWindowChart(
    points: List<SelfControlInsightChartPoint>,
    metric: SelfControlInsightWindowPolicy.Metric,
    semanticSummary: String,
    currentPointLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val intervalUnit = remember(points, metric) {
        if (metric == SelfControlInsightWindowPolicy.Metric.INTERVAL) {
            SelfControlIntervalPolicy.chooseAxisUnit(points.maxOf { it.value.toLong() })
        } else {
            SelfControlIntervalPolicy.AxisUnit.Seconds
        }
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points, metric, intervalUnit) {
        runCatching {
            modelProducer.runTransaction {
                columnSeries {
                    series(
                        points.map { point ->
                            if (metric == SelfControlInsightWindowPolicy.Metric.INTERVAL) {
                                point.value / intervalUnit.divisorMs.toDouble()
                            } else {
                                point.value
                            }
                        },
                    )
                }
            }
        }.onFailure { error ->
            LogUtils.d("self-control insight chart model update failed", error::class.java.simpleName)
        }
    }
    val visibleLabels = remember(points) {
        val step = ((points.size + 5) / 6).coerceAtLeast(1)
        points.indices.filter { it == 0 || it == points.lastIndex || it % step == 0 }.toSet()
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        color = MaterialTheme.colorScheme.primary,
                        thickness = 14.dp,
                    ),
                ),
            ),
            startAxis = rememberStartAxis(
                valueFormatter = { value, _, _ ->
                    if (metric == SelfControlInsightWindowPolicy.Metric.INTERVAL) {
                        SelfControlIntervalPolicy.formatAxisValue(
                            (value.toDouble() * intervalUnit.divisorMs.toDouble()).toLong(),
                            intervalUnit,
                        )
                    } else {
                        "${UsageRequestRhythmPolicy.formatRatio(value.toDouble()) ?: "暂无"}×"
                    }
                },
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { x, _, _ ->
                    val index = x.toInt()
                    if (index in visibleLabels) points.getOrNull(index)?.label.orEmpty() else ""
                },
            ),
        ),
        modelProducer = modelProducer,
        animationSpec = null,
        runInitialAnimation = false,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .semantics {
                contentDescription = listOfNotNull(
                    semanticSummary,
                    currentPointLabel?.let { "本次所在时段：$it" },
                ).joinToString("；")
            },
    )
}
