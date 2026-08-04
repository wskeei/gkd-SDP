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

/**
 * A deliberately small chart: text remains the source of truth and this chart is an
 * at-a-glance enhancement. No database work or animation is performed here.
 */
@Composable
fun SelfControlIntervalChart(
    points: List<SelfControlIntervalPresentation.ChartPoint>,
    semanticSummary: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val axisUnit = remember(points) {
        SelfControlIntervalPolicy.chooseAxisUnit(points.maxOf { it.valueMs })
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points, axisUnit) {
        runCatching {
            modelProducer.runTransaction {
                columnSeries {
                    series(points.map { point ->
                        point.valueMs.toDouble() / axisUnit.divisorMs.toDouble()
                    })
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        color = MaterialTheme.colorScheme.primary,
                        thickness = 14.dp,
                    )
                )
            ),
            startAxis = rememberStartAxis(
                valueFormatter = { value, _, _ ->
                    SelfControlIntervalPolicy.formatAxisValue(
                        (value.toDouble() * axisUnit.divisorMs.toDouble()).toLong(),
                        axisUnit,
                    )
                },
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { x, _, _ ->
                    points.getOrNull(x.toInt())?.label.orEmpty()
                }
            ),
        ),
        modelProducer = modelProducer,
        animationSpec = null,
        runInitialAnimation = false,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .semantics {
                contentDescription = "$semanticSummary，纵轴单位 ${axisUnit.suffix}"
            },
    )
}
