package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy
import java.math.BigDecimal
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

data class UsageRequestRhythmPresentation(
    val status: Status,
    val selectedWindow: SelfControlInsightWindowPolicy.Window,
    val currentGapMs: Long?,
    val requestedDurationMinutes: Int?,
    val currentRatio: Double?,
    val currentFormula: UsageRequestRhythmPolicy.Formula?,
    val currentRatioText: String,
    val selectedWindowAverageText: String,
    val equationText: String?,
    val gapText: String,
    val durationText: String,
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

    data class HistoricalStats(
        val averageRatioByWindow: Map<SelfControlInsightWindowPolicy.Window, Double?>,
        val ratioSampleCountByWindow: Map<SelfControlInsightWindowPolicy.Window, Int>,
    )

    companion object {
        fun historicalStats(
            data: SelfControlIntervalRepository.UsageRequestOverlayData?,
            fallbackNowEpochMs: Long,
        ): HistoricalStats {
            val samples = data?.samples.orEmpty()
            val anchor = data?.insightAnchorAt ?: fallbackNowEpochMs
            val series = SelfControlInsightWindowPolicy.Window.entries.associateWith { window ->
                SelfControlInsightWindowPolicy.aggregate(
                    samples = samples,
                    nowEpochMs = anchor,
                    window = window,
                    metric = SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
                )
            }
            return HistoricalStats(
                averageRatioByWindow = series.mapValues { it.value.stats.averageRatio },
                ratioSampleCountByWindow = series.mapValues { it.value.stats.sampleCount },
            )
        }

        fun from(
            data: SelfControlIntervalRepository.UsageRequestOverlayData?,
            nowEpochMs: Long,
            requestedDurationMinutes: Int,
            selectedWindow: SelfControlInsightWindowPolicy.Window =
                SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            cachedHistory: HistoricalStats? = null,
        ): UsageRequestRhythmPresentation {
            val currentGap = data?.previousLastUsageEndedAt?.let {
                UsageRequestRhythmPolicy.gapMs(it, nowEpochMs)
            }
            val status = when {
                data == null -> Status.UNAVAILABLE
                data.anchorStatus == SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest -> Status.FIRST
                data.anchorStatus == SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd -> Status.MISSING_ACTUAL_END
                currentGap == null -> Status.UNAVAILABLE
                else -> Status.AVAILABLE
            }
            val history = cachedHistory ?: historicalStats(data, nowEpochMs)
            val averages = history.averageRatioByWindow
            val counts = history.ratioSampleCountByWindow
            val currentRatio = UsageRequestRhythmPolicy.currentRatio(
                currentGap,
                requestedDurationMinutes,
            )
            val currentFormula = UsageRequestRhythmPolicy.formula(
                currentGap,
                requestedDurationMinutes,
            )
            val currentRatioText = currentRatio?.let {
                "${UsageRequestRhythmPolicy.formatRatio(it) ?: "—"}×"
            } ?: "—"
            val selectedWindowAverageText = averages[selectedWindow]?.let {
                "${UsageRequestRhythmPolicy.formatRatio(it) ?: "—"}×"
            } ?: "—"
            val equationText = currentFormula?.let(::formatEquation)
            val gapText = currentGap?.let(SelfControlIntervalPolicy::formatDurationCompact) ?: "—"
            val durationText = requestedDurationMinutes.takeIf { it > 0 }?.let { "$it 分钟" } ?: "—"
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
                selectedWindow = selectedWindow,
                currentGapMs = currentGap,
                requestedDurationMinutes = requestedDurationMinutes.takeIf { it > 0 },
                currentRatio = currentRatio,
                currentFormula = currentFormula,
                currentRatioText = currentRatioText,
                selectedWindowAverageText = selectedWindowAverageText,
                equationText = equationText,
                gapText = gapText,
                durationText = durationText,
                averageRatioByWindow = averages,
                ratioSampleCountByWindow = counts,
                comparisonText = comparison,
                statusText = statusText,
            )
        }

        private fun formatRatioDelta(value: Double): String =
            "${UsageRequestRhythmPolicy.formatRatio(value) ?: "暂无"}×"

        fun formatFormula(formula: UsageRequestRhythmPolicy.Formula?): String {
            return "公式：${formatEquation(formula)}"
        }

        private fun formatEquation(formula: UsageRequestRhythmPolicy.Formula?): String {
            if (formula == null) return "—"
            return "${formatOperand(formula.gapValue)} ${formula.unit.label} ÷ " +
                "${formatOperand(formula.durationValue)} ${formula.unit.label} = " +
                "${UsageRequestRhythmPolicy.formatRatio(formula.ratio) ?: "—"}×"
        }

        private fun formatOperand(value: BigDecimal): String = value
            .stripTrailingZeros()
            .toPlainString()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
fun UsageDurationRatioFeedback(
    presentation: UsageRequestRhythmPresentation,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(app.getString(R.string.s_4cec547cf2), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column {
                    Text(app.getString(R.string.s_7fd47e102e), style = MaterialTheme.typography.bodySmall)
                    Text(presentation.currentRatioText, style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text(app.getString(R.string.s_34e7ba7c70, presentation.selectedWindow.label), style = MaterialTheme.typography.bodySmall)
                    Text(presentation.selectedWindowAverageText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(app.getString(R.string.s_0db6dd2e7f, presentation.gapText), style = MaterialTheme.typography.bodyMedium)
                Text(app.getString(R.string.s_d11b352c3e, presentation.durationText), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = app.getString(R.string.s_aa41382aa4, )—app.getString(R.string.s_c2b7df6201),
                style = MaterialTheme.typography.bodyMedium,
            )
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
}
