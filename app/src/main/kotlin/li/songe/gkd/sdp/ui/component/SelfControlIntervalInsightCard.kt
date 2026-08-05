package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy

/** A request-only value which is intentionally kept out of historical samples and statistics. */
data class SelfControlInsightCurrentReference(
    val gapMs: Long?,
    val durationMinutes: Int? = null,
    val eventId: Long? = null,
)

data class SelfControlInsightChartPoint(
    val label: String,
    val value: Double,
    val sampleCount: Int,
    val isCurrent: Boolean,
    val bucketStartAt: Long = 0L,
)

data class SelfControlInsightTextRow(
    val label: String,
    val valueText: String,
    val sampleCount: Int,
    val isCurrent: Boolean,
)

/** Pure presentation model. It never reads Room or starts a clock. */
data class SelfControlInsightPresentation(
    val selectedWindow: SelfControlInsightWindowPolicy.Window,
    val selectedMetric: SelfControlInsightWindowPolicy.Metric,
    val selectedSeries: SelfControlInsightWindowPolicy.Series,
    val intervalSeriesByWindow: Map<SelfControlInsightWindowPolicy.Window, SelfControlInsightWindowPolicy.Series>,
    val ratioSeriesByWindow: Map<SelfControlInsightWindowPolicy.Window, SelfControlInsightWindowPolicy.Series>,
    val chartPoints: List<SelfControlInsightChartPoint>,
    val textRows: List<SelfControlInsightTextRow>,
    val semanticSummary: String,
    val comparisonText: String?,
    val supportingText: String,
) {
    companion object {
        fun from(
            samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
            insightAnchorAt: Long,
            selectedWindow: SelfControlInsightWindowPolicy.Window =
                SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            selectedMetric: SelfControlInsightWindowPolicy.Metric =
                SelfControlInsightWindowPolicy.Metric.INTERVAL,
            supportsUsageRatio: Boolean = false,
            currentReference: SelfControlInsightCurrentReference? = null,
            zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        ): SelfControlInsightPresentation {
            val safeMetric = if (supportsUsageRatio) selectedMetric else {
                SelfControlInsightWindowPolicy.Metric.INTERVAL
            }
            val intervalSeries = SelfControlInsightWindowPolicy.Window.entries.associateWith { window ->
                SelfControlInsightWindowPolicy.aggregate(
                    samples = samples,
                    nowEpochMs = insightAnchorAt,
                    window = window,
                    metric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
                    zoneId = zoneId,
                )
            }
            val ratioSeries = if (supportsUsageRatio) {
                SelfControlInsightWindowPolicy.Window.entries.associateWith { window ->
                    SelfControlInsightWindowPolicy.aggregate(
                        samples = samples,
                        nowEpochMs = insightAnchorAt,
                        window = window,
                        metric = SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
                        zoneId = zoneId,
                    )
                }
            } else {
                emptyMap()
            }
            val selectedSeries = (if (safeMetric == SelfControlInsightWindowPolicy.Metric.INTERVAL) {
                intervalSeries
            } else {
                ratioSeries
            })[selectedWindow] ?: SelfControlInsightWindowPolicy.aggregate(
                samples = emptyList(),
                nowEpochMs = insightAnchorAt,
                window = selectedWindow,
                metric = safeMetric,
                zoneId = zoneId,
            )
            val currentEventId = currentReference?.eventId
            val chartPoints = selectedSeries.points.map { point ->
                SelfControlInsightChartPoint(
                    label = point.label,
                    value = point.value,
                    sampleCount = point.sampleCount,
                    isCurrent = currentEventId != null && point.sourceIds.contains(currentEventId),
                    bucketStartAt = point.bucketStartAt,
                )
            }
            val textRows = chartPoints.map { point ->
                SelfControlInsightTextRow(
                    label = point.label,
                    valueText = formatValue(point.value, safeMetric),
                    sampleCount = point.sampleCount,
                    isCurrent = point.isCurrent,
                )
            }
            val comparisonText = comparisonText(currentReference, selectedSeries, safeMetric)
            val semanticSummary = semanticSummary(selectedSeries, selectedWindow, safeMetric)
            val coverageText = coverageText(selectedSeries)
            val supportingText = when {
                selectedSeries.stats.sampleCount == 0 ->
                    "所选范围暂无可用样本；$coverageText；已加载的近 30 天数据会在切换范围时继续复用。"
                selectedSeries.aggregationApplied ->
                    "${selectedSeries.aggregationLabel}，每个时间桶显示桶内样本平均值；$coverageText。"
                else ->
                    "图表逐条显示所选范围的有效样本；$coverageText；文字明细与图表一一对应。"
            }
            return SelfControlInsightPresentation(
                selectedWindow = selectedWindow,
                selectedMetric = safeMetric,
                selectedSeries = selectedSeries,
                intervalSeriesByWindow = intervalSeries,
                ratioSeriesByWindow = ratioSeries,
                chartPoints = chartPoints,
                textRows = textRows,
                semanticSummary = semanticSummary,
                comparisonText = comparisonText,
                supportingText = supportingText,
            )
        }

        fun withCurrentReference(
            base: SelfControlInsightPresentation,
            samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
            insightAnchorAt: Long,
            selectedWindow: SelfControlInsightWindowPolicy.Window,
            currentReference: SelfControlInsightCurrentReference?,
        ): SelfControlInsightPresentation {
            val currentEventId = currentReference?.eventId
            val chartPoints = base.chartPoints.mapIndexed { index, point ->
                point.copy(
                    isCurrent = currentEventId != null &&
                        base.selectedSeries.points.getOrNull(index)
                            ?.sourceIds
                            ?.contains(currentEventId) == true,
                )
            }
            val textRows = base.textRows.mapIndexed { index, row ->
                row.copy(isCurrent = chartPoints.getOrNull(index)?.isCurrent == true)
            }
            return base.copy(
                chartPoints = chartPoints,
                textRows = textRows,
                comparisonText = comparisonText(
                    currentReference,
                    base.selectedSeries,
                    base.selectedMetric,
                ),
            )
        }

        private fun semanticSummary(
            series: SelfControlInsightWindowPolicy.Series,
            window: SelfControlInsightWindowPolicy.Window,
            metric: SelfControlInsightWindowPolicy.Metric,
        ): String {
            val stats = series.stats
            val coverage = coverageText(series)
            if (stats.sampleCount == 0) return "${window.label}暂无${metric.label()}样本 · $coverage。"
            val average = when (metric) {
                SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                    stats.averageMs?.let { SelfControlIntervalPolicy.formatDurationCompact(it.toLong()) }
                SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                    formatValue(stats.averageRatio ?: 0.0, metric)
            } ?: "暂无"
            val median = when (metric) {
                SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                    stats.medianMs?.let { SelfControlIntervalPolicy.formatDurationCompact(it.toLong()) }
                SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                    stats.medianRatio?.let { formatValue(it, metric) }
            } ?: "暂无"
            return "${window.label}${metric.label()}平均 $average · 中位 $median · $coverage。"
        }

        private fun coverageText(series: SelfControlInsightWindowPolicy.Series): String {
            val excluded = if (series.excludedSampleCount > 0) {
                " · 未纳入 ${series.excludedSampleCount} 条"
            } else {
                ""
            }
            return "总记录 ${series.rawSampleCount} 条 · 有效样本 ${series.stats.sampleCount} 条 · 图形点 ${series.points.size} 个$excluded"
        }

        private fun comparisonText(
            current: SelfControlInsightCurrentReference?,
            series: SelfControlInsightWindowPolicy.Series,
            metric: SelfControlInsightWindowPolicy.Metric,
        ): String? {
            val currentValue = current?.let { valueFor(it, metric) } ?: return null
            val baseline = when (metric) {
                SelfControlInsightWindowPolicy.Metric.INTERVAL -> series.stats.averageMs?.toDouble()
                SelfControlInsightWindowPolicy.Metric.USAGE_RATIO -> series.stats.averageRatio
            } ?: return null
            val delta = currentValue - baseline
            if (!delta.isFinite()) return null
            return when {
                delta > 0.0 -> "本次比所选范围平均高 ${formatValue(kotlin.math.abs(delta), metric)}"
                delta < 0.0 -> "本次比所选范围平均低 ${formatValue(kotlin.math.abs(delta), metric)}"
                else -> "本次与所选范围平均相同"
            }
        }

        private fun valueFor(
            current: SelfControlInsightCurrentReference,
            metric: SelfControlInsightWindowPolicy.Metric,
        ): Double? = when (metric) {
            SelfControlInsightWindowPolicy.Metric.INTERVAL -> current.gapMs
                ?.takeIf { it >= 0L }
                ?.toDouble()
            SelfControlInsightWindowPolicy.Metric.USAGE_RATIO -> UsageRequestRhythmPolicy.ratio(
                current.gapMs,
                current.durationMinutes ?: 0,
            )
        }

        fun formatValue(
            value: Double,
            metric: SelfControlInsightWindowPolicy.Metric,
        ): String = when (metric) {
            SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                SelfControlIntervalPolicy.formatDurationCompact(value.toLong())
            SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                "${UsageRequestRhythmPolicy.formatRatio(value) ?: "暂无"}×"
        }

        private fun SelfControlInsightWindowPolicy.Metric.label(): String = when (this) {
            SelfControlInsightWindowPolicy.Metric.INTERVAL -> "间隔"
            SelfControlInsightWindowPolicy.Metric.USAGE_RATIO -> "间用比"
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelfControlIntervalInsightCard(
    samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
    insightAnchorAt: Long,
    selectedWindow: SelfControlInsightWindowPolicy.Window,
    onWindowSelected: (SelfControlInsightWindowPolicy.Window) -> Unit,
    selectedMetric: SelfControlInsightWindowPolicy.Metric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
    onMetricSelected: (SelfControlInsightWindowPolicy.Metric) -> Unit = {},
    supportsUsageRatio: Boolean = false,
    currentReference: SelfControlInsightCurrentReference? = null,
    modifier: Modifier = Modifier,
) {
    val basePresentation = remember(
        samples,
        insightAnchorAt,
        selectedWindow,
        selectedMetric,
        supportsUsageRatio,
    ) {
        SelfControlInsightPresentation.from(
            samples = samples,
            insightAnchorAt = insightAnchorAt,
            selectedWindow = selectedWindow,
            selectedMetric = selectedMetric,
            supportsUsageRatio = supportsUsageRatio,
        )
    }
    val presentation = SelfControlInsightPresentation.withCurrentReference(
        base = basePresentation,
        samples = samples,
        insightAnchorAt = insightAnchorAt,
        selectedWindow = selectedWindow,
        currentReference = currentReference,
    )
    var menuExpanded by remember(selectedWindow) { mutableStateOf(false) }
    var detailsExpanded by remember(selectedWindow, presentation.selectedMetric) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(
            text = if (supportsUsageRatio) "最近间隔与间用比" else "最近间隔",
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column {
                TextButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.semantics {
                        contentDescription = "选择统计范围，当前 ${selectedWindow.label}"
                    },
                ) {
                    Text(selectedWindow.label)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    SelfControlInsightWindowPolicy.Window.entries.forEach { window ->
                        DropdownMenuItem(
                            text = { Text(window.label) },
                            onClick = {
                                menuExpanded = false
                                onWindowSelected(window)
                            },
                        )
                    }
                }
            }
            if (supportsUsageRatio) {
                FilterChip(
                    selected = presentation.selectedMetric == SelfControlInsightWindowPolicy.Metric.INTERVAL,
                    onClick = { onMetricSelected(SelfControlInsightWindowPolicy.Metric.INTERVAL) },
                    label = { Text("间隔") },
                    modifier = Modifier.semantics { contentDescription = "统计间隔" },
                )
                FilterChip(
                    selected = presentation.selectedMetric == SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
                    onClick = { onMetricSelected(SelfControlInsightWindowPolicy.Metric.USAGE_RATIO) },
                    label = { Text("间用比") },
                    modifier = Modifier.semantics { contentDescription = "统计间用比" },
                )
            }
        }

        Text(
            text = presentation.semanticSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (supportsUsageRatio) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presentation.ratioSeriesByWindow.forEach { (window, series) ->
                    val average = series.stats.averageRatio?.let { SelfControlInsightPresentation.formatValue(it, SelfControlInsightWindowPolicy.Metric.USAGE_RATIO) }
                        ?: "暂无"
                    Text(
                        text = "${window.label}平均 $average（${series.stats.sampleCount} 条）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        currentReference?.let { current ->
            val currentPoint = presentation.chartPoints.firstOrNull { it.isCurrent }
            val value = when (presentation.selectedMetric) {
                SelfControlInsightWindowPolicy.Metric.INTERVAL -> current.gapMs?.let(SelfControlIntervalPolicy::formatDurationCompact)
                SelfControlInsightWindowPolicy.Metric.USAGE_RATIO -> UsageRequestRhythmPolicy.ratio(
                    current.gapMs,
                    current.durationMinutes ?: 0,
                )?.let { SelfControlInsightPresentation.formatValue(it, SelfControlInsightWindowPolicy.Metric.USAGE_RATIO) }
            }
            Text(
                text = when {
                    value != null && currentPoint != null ->
                        "本次：$value · 所在时段：${currentPoint.label}"
                    value != null -> "本次：$value"
                    currentPoint != null -> "本次所在时段：${currentPoint.label}"
                    else -> "本次：已记录，暂无可比较的间隔值"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        presentation.comparisonText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (presentation.chartPoints.isNotEmpty()) {
            val currentPointValue = currentReference?.let { current ->
                when (presentation.selectedMetric) {
                    SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                        current.gapMs?.takeIf { it >= 0L }
                            ?.let(SelfControlIntervalPolicy::formatDurationCompact)
                    SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                        UsageRequestRhythmPolicy.ratio(
                            current.gapMs,
                            current.durationMinutes ?: 0,
                        )?.let {
                            SelfControlInsightPresentation.formatValue(
                                it,
                                SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
                            )
                        }
                } ?: "暂无可用值"
            }
            SelfControlWindowChart(
                points = presentation.chartPoints,
                metric = presentation.selectedMetric,
                semanticSummary = presentation.semanticSummary,
                currentPointLabel = presentation.chartPoints.firstOrNull { it.isCurrent }?.label,
                currentPointValue = currentPointValue,
                aggregationLabel = presentation.selectedSeries.aggregationLabel,
            )
            TextButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.semantics {
                    contentDescription = if (detailsExpanded) "收起图表文字明细" else "查看图表文字明细"
                },
            ) {
                Text(if (detailsExpanded) "收起图表文字明细" else "查看图表文字明细")
            }
            if (detailsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    presentation.textRows.forEach { row ->
                        val currentLabel = if (row.isCurrent) "，本次" else ""
                        val bucketLabel = if (row.sampleCount > 1) "，时间桶平均 ${row.sampleCount} 条" else ""
                        Text(
                            text = "${row.label}：${row.valueText}$currentLabel$bucketLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
