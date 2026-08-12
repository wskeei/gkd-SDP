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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

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
    val valueMs: Long? = null,
    val valueRatio: Double? = null,
)

/** Pure presentation model. It never reads Room or starts a clock. */
@Immutable
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
    val semanticSummaryRes: Int,
    val semanticSummaryArgs: List<Any>,
    val supportingTextRes: Int,
    val supportingTextArgs: List<Any>,
    val comparisonTextRes: Int? = null,
    val comparisonTextArgs: List<Any> = emptyList(),
    val aggregationLabelRes: Int? = null,
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
                    valueMs = if (safeMetric == SelfControlInsightWindowPolicy.Metric.INTERVAL) {
                        point.value.toLong()
                    } else {
                        null
                    },
                    valueRatio = if (safeMetric == SelfControlInsightWindowPolicy.Metric.USAGE_RATIO) {
                        point.value
                    } else {
                        null
                    },
                )
            }
            val comparisonText = comparisonText(currentReference, selectedSeries, safeMetric)
            val semanticSummary = semanticSummary(selectedSeries, selectedWindow, safeMetric)
            val coverageText = coverageText(selectedSeries)
            val supportingText = when {
                selectedSeries.stats.sampleCount == 0 ->
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "所选范围暂无可用样本；$coverageText；已加载的近 30 天数据会在切换范围时继续复用。"
                selectedSeries.aggregationApplied ->
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "${selectedSeries.aggregationLabel}，每个时间桶显示桶内样本平均值；$coverageText。"
                else ->
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "图表逐条显示所选范围的有效样本；$coverageText；文字明细与图表一一对应。"
            }
            val coverage = if (selectedSeries.excludedSampleCount > 0) {
                LocalizedValue.Text(
                    R.string.insight_coverage_excluded,
                    listOf(
                        selectedSeries.rawSampleCount,
                        selectedSeries.stats.sampleCount,
                        selectedSeries.points.size,
                        selectedSeries.excludedSampleCount,
                    ),
                )
            } else {
                LocalizedValue.Text(
                    R.string.insight_coverage,
                    listOf(
                        selectedSeries.rawSampleCount,
                        selectedSeries.stats.sampleCount,
                        selectedSeries.points.size,
                    ),
                )
            }
            val windowLabel = LocalizedValue.Text(selectedWindow.labelRes())
            val metricLabel = LocalizedValue.Text(safeMetric.labelRes())
            val semanticSummaryRes = if (selectedSeries.stats.sampleCount == 0) {
                R.string.insight_semantic_empty
            } else {
                R.string.insight_semantic_data
            }
            val semanticSummaryArgs = if (selectedSeries.stats.sampleCount == 0) {
                listOf(windowLabel, metricLabel, coverage)
            } else {
                val average = when (safeMetric) {
                    SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                        LocalizedValue.Duration(selectedSeries.stats.averageMs?.toLong())
                    SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                        LocalizedValue.Ratio(selectedSeries.stats.averageRatio)
                }
                val median = when (safeMetric) {
                    SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                        LocalizedValue.Duration(selectedSeries.stats.medianMs?.toLong())
                    SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                        LocalizedValue.Ratio(selectedSeries.stats.medianRatio)
                }
                listOf(windowLabel, metricLabel, average, median, coverage)
            }
            val supportingTextRes = when {
                selectedSeries.stats.sampleCount == 0 -> R.string.insight_support_empty
                selectedSeries.aggregationApplied -> R.string.insight_support_aggregated
                else -> R.string.insight_support_detail
            }
            val supportingTextArgs = when {
                selectedSeries.stats.sampleCount == 0 -> listOf(coverage)
                selectedSeries.aggregationApplied -> listOf(
                    LocalizedValue.Text(selectedWindow.aggregationLabelRes()),
                    coverage,
                )
                else -> listOf(coverage)
            }
            val comparison = comparisonText(currentReference, selectedSeries, safeMetric)
            val comparisonTextRes = comparison?.let {
                val currentValue = currentReference?.let { valueFor(it, safeMetric) }
                val baseline = when (safeMetric) {
                    SelfControlInsightWindowPolicy.Metric.INTERVAL -> selectedSeries.stats.averageMs?.toDouble()
                    SelfControlInsightWindowPolicy.Metric.USAGE_RATIO -> selectedSeries.stats.averageRatio
                }
                if (currentValue == null || baseline == null || !currentValue.isFinite()) {
                    null
                } else {
                    when {
                        currentValue > baseline -> R.string.insight_comparison_high
                        currentValue < baseline -> R.string.insight_comparison_low
                        else -> R.string.insight_comparison_same
                    }
                }
            }
            val comparisonTextArgs = when (comparisonTextRes) {
                R.string.insight_comparison_high,
                R.string.insight_comparison_low,
                -> {
                    val delta = when (safeMetric) {
                        SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                            LocalizedValue.Duration(
                                (currentReference?.gapMs ?: 0L) - (selectedSeries.stats.averageMs?.toLong() ?: 0L),
                            )
                        SelfControlInsightWindowPolicy.Metric.USAGE_RATIO -> LocalizedValue.Ratio(
                            UsageRequestRhythmPolicy.ratio(
                                currentReference?.gapMs,
                                currentReference?.durationMinutes ?: 0,
                            )!! - (selectedSeries.stats.averageRatio ?: 0.0),
                        )
                    }
                    listOf(delta)
                }
                else -> emptyList()
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
                semanticSummaryRes = semanticSummaryRes,
                semanticSummaryArgs = semanticSummaryArgs,
                supportingTextRes = supportingTextRes,
                supportingTextArgs = supportingTextArgs,
                comparisonTextRes = comparisonTextRes,
                comparisonTextArgs = comparisonTextArgs,
                aggregationLabelRes = if (selectedSeries.aggregationApplied) {
                    selectedWindow.aggregationLabelRes()
                } else {
                    null
                },
            )
        }

        fun withCurrentReference(
            base: SelfControlInsightPresentation,
            samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
            insightAnchorAt: Long,
            selectedWindow: SelfControlInsightWindowPolicy.Window,
            currentReference: SelfControlInsightCurrentReference?,
        ): SelfControlInsightPresentation {
            return from(
                samples = samples,
                insightAnchorAt = insightAnchorAt,
                selectedWindow = selectedWindow,
                selectedMetric = base.selectedMetric,
                supportsUsageRatio = base.ratioSeriesByWindow.isNotEmpty(),
                currentReference = currentReference,
            )
        }

        private fun semanticSummary(
            series: SelfControlInsightWindowPolicy.Series,
            window: SelfControlInsightWindowPolicy.Window,
            metric: SelfControlInsightWindowPolicy.Metric,
        ): String {
            val stats = series.stats
            val coverage = coverageText(series)
            // i18n-ignore: legacy fallback or non-display heuristic data
            if (stats.sampleCount == 0) return "${window.label}暂无${metric.label()}样本 · $coverage。"
            val average = when (metric) {
                SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                    stats.averageMs?.let { SelfControlIntervalPolicy.formatDurationCompact(it.toLong()) }
                SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                    formatValue(stats.averageRatio ?: 0.0, metric)
            // i18n-ignore: legacy fallback or non-display heuristic data
            } ?: "暂无"
            val median = when (metric) {
                SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                    stats.medianMs?.let { SelfControlIntervalPolicy.formatDurationCompact(it.toLong()) }
                SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                    stats.medianRatio?.let { formatValue(it, metric) }
            // i18n-ignore: legacy fallback or non-display heuristic data
            } ?: "暂无"
            // i18n-ignore: legacy fallback or non-display heuristic data
            return "${window.label}${metric.label()}平均 $average · 中位 $median · $coverage。"
        }

        private fun coverageText(series: SelfControlInsightWindowPolicy.Series): String {
            val excluded = if (series.excludedSampleCount > 0) {
                // i18n-ignore: legacy fallback or non-display heuristic data
                " · 未纳入 ${series.excludedSampleCount} 条"
            } else {
                ""
            }
            // i18n-ignore: legacy fallback or non-display heuristic data
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
                // i18n-ignore: legacy fallback or non-display heuristic data
                delta > 0.0 -> "本次比所选范围平均高 ${formatValue(kotlin.math.abs(delta), metric)}"
                // i18n-ignore: legacy fallback or non-display heuristic data
                delta < 0.0 -> "本次比所选范围平均低 ${formatValue(kotlin.math.abs(delta), metric)}"
                // i18n-ignore: legacy fallback or non-display heuristic data
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
                "${UsageRequestRhythmPolicy.formatRatio(value) ?: "—"}×"
        }

        private fun SelfControlInsightWindowPolicy.Metric.label(): String = when (this) {
            // i18n-ignore: legacy fallback or non-display heuristic data
            SelfControlInsightWindowPolicy.Metric.INTERVAL -> "间隔"
            // i18n-ignore: legacy fallback or non-display heuristic data
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
            text = if (supportsUsageRatio) stringResource(R.string.s_b28678dc48) else stringResource(R.string.s_f52a834cc7),
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
                        contentDescription = li.songe.gkd.sdp.app.getString(
                            R.string.s_086fa44431,
                            li.songe.gkd.sdp.app.getString(selectedWindow.labelRes()),
                        )
                    },
                ) {
                    Text(stringResource(selectedWindow.labelRes()))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    SelfControlInsightWindowPolicy.Window.entries.forEach { window ->
                        DropdownMenuItem(
                            text = { Text(stringResource(window.labelRes())) },
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
                    label = { Text(stringResource(R.string.s_940c88657e)) },
                    modifier = Modifier.semantics {
                        contentDescription = li.songe.gkd.sdp.app.getString(R.string.insight_interval_description)
                    },
                )
                FilterChip(
                    selected = presentation.selectedMetric == SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
                    onClick = { onMetricSelected(SelfControlInsightWindowPolicy.Metric.USAGE_RATIO) },
                    label = { Text(stringResource(R.string.s_4cec547cf2)) },
                    modifier = Modifier.semantics {
                        contentDescription = li.songe.gkd.sdp.app.getString(R.string.insight_ratio_description)
                    },
                )
            }
        }

        Text(
            text = LocalizedValue.Text(
                presentation.semanticSummaryRes,
                presentation.semanticSummaryArgs,
            ).render(),
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
                    val average = series.stats.averageRatio?.let {
                        LocalizedValue.Ratio(it).render()
                    } ?: stringResource(R.string.insight_average_missing)
                    Text(
                        text = li.songe.gkd.sdp.app.getString(
                            R.string.s_233ee6a4c8,
                            li.songe.gkd.sdp.app.getString(window.labelRes()),
                            average,
                            series.stats.sampleCount.toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        currentReference?.let { current ->
            val currentPoint = presentation.chartPoints.firstOrNull { it.isCurrent }
            val value = when (presentation.selectedMetric) {
                SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                    LocalizedValue.Duration(current.gapMs).render()
                SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                    LocalizedValue.Ratio(
                        UsageRequestRhythmPolicy.ratio(
                            current.gapMs,
                            current.durationMinutes ?: 0,
                        ),
                    ).render()
            }
            Text(
                text = when {
                    value != "—" && currentPoint != null ->
                        stringResource(R.string.insight_current_with_point, value, currentPoint.label)
                    value != "—" -> stringResource(R.string.insight_current_value, value)
                    currentPoint != null ->
                        stringResource(R.string.insight_current_point, currentPoint.label)
                    else -> stringResource(R.string.insight_no_value)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        presentation.comparisonTextRes?.let { res ->
            Text(
                text = stringResource(
                    res,
                    *presentation.comparisonTextArgs.map { renderLocalizedArg(it) }.toTypedArray(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (presentation.chartPoints.isNotEmpty()) {
            val currentPointValue = currentReference?.let { current ->
                when (presentation.selectedMetric) {
                    SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                        LocalizedValue.Duration(current.gapMs?.takeIf { it >= 0L }).render()
                    SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                        LocalizedValue.Ratio(
                            UsageRequestRhythmPolicy.ratio(
                                current.gapMs,
                                current.durationMinutes ?: 0,
                            ),
                        ).render()
                }?.takeIf { it != "—" } ?: stringResource(R.string.insight_no_value)
            }
            SelfControlWindowChart(
                points = presentation.chartPoints,
                metric = presentation.selectedMetric,
                semanticSummary = LocalizedValue.Text(
                    presentation.semanticSummaryRes,
                    presentation.semanticSummaryArgs,
                ).render(),
                currentPointLabel = presentation.chartPoints.firstOrNull { it.isCurrent }?.label,
                currentPointValue = currentPointValue,
                aggregationLabel = presentation.aggregationLabelRes?.let { stringResource(it) },
            )
            TextButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.semantics {
                    contentDescription = if (detailsExpanded) li.songe.gkd.sdp.app.getString(R.string.s_bb9dd3221c) else li.songe.gkd.sdp.app.getString(R.string.s_ce7a8986ad)
                },
            ) {
                Text(if (detailsExpanded) stringResource(R.string.s_bb9dd3221c) else stringResource(R.string.s_ce7a8986ad))
            }
            if (detailsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    presentation.textRows.forEach { row ->
                        val value = when (presentation.selectedMetric) {
                            SelfControlInsightWindowPolicy.Metric.INTERVAL ->
                                LocalizedValue.Duration(row.valueMs)
                            SelfControlInsightWindowPolicy.Metric.USAGE_RATIO ->
                                LocalizedValue.Ratio(row.valueRatio)
                        }
                        val res = when {
                            row.isCurrent && row.sampleCount > 1 ->
                                R.string.insight_text_row_current_bucket
                            row.isCurrent -> R.string.insight_text_row_current
                            row.sampleCount > 1 -> R.string.insight_text_row_bucket
                            else -> R.string.insight_text_row
                        }
                        Text(
                            text = stringResource(
                                res,
                                row.label,
                                value.render(),
                                row.sampleCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            text = LocalizedValue.Text(
                presentation.supportingTextRes,
                presentation.supportingTextArgs,
            ).render(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
