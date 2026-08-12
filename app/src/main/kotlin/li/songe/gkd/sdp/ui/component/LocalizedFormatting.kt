package li.songe.gkd.sdp.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy

@StringRes
fun SelfControlInsightWindowPolicy.Window.labelRes(): Int = when (this) {
    SelfControlInsightWindowPolicy.Window.LAST_24_HOURS -> R.string.review_range_24h
    SelfControlInsightWindowPolicy.Window.LAST_7_DAYS -> R.string.review_range_7d
    SelfControlInsightWindowPolicy.Window.LAST_30_DAYS -> R.string.review_range_30d
}

@StringRes
fun DigitalSelfDisciplineReviewPolicy.Range.labelRes(): Int = when (this) {
    DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS -> R.string.review_range_24h
    DigitalSelfDisciplineReviewPolicy.Range.LAST_7_DAYS -> R.string.review_range_7d
    DigitalSelfDisciplineReviewPolicy.Range.LAST_30_DAYS -> R.string.review_range_30d
}

@StringRes
fun DigitalSelfDisciplineReviewPolicy.ReviewType.labelRes(): Int = when (this) {
    DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest -> R.string.review_type_usage
    DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt -> R.string.review_type_intercept
}

@StringRes
fun DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.labelRes(): Int = when (this) {
    DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All -> R.string.review_filter_all
    DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.AppBlocker -> R.string.review_filter_app
    DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.Selector -> R.string.review_filter_selector
    DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.Url -> R.string.review_filter_url
}

@StringRes
fun DigitalSelfDisciplineReviewPolicy.ReviewMetric.labelRes(): Int = when (this) {
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO -> R.string.review_metric_ratio
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP -> R.string.review_metric_gap
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL -> R.string.review_metric_intercept
}

@StringRes
fun SelfControlInsightWindowPolicy.Metric.labelRes(): Int = when (this) {
    SelfControlInsightWindowPolicy.Metric.INTERVAL -> R.string.insight_metric_interval
    SelfControlInsightWindowPolicy.Metric.USAGE_RATIO -> R.string.insight_metric_ratio
}

@StringRes
fun SelfControlInsightWindowPolicy.Window.aggregationLabelRes(): Int = when (this) {
    SelfControlInsightWindowPolicy.Window.LAST_24_HOURS -> R.string.insight_aggregation_1h
    SelfControlInsightWindowPolicy.Window.LAST_7_DAYS -> R.string.insight_aggregation_6h
    SelfControlInsightWindowPolicy.Window.LAST_30_DAYS -> R.string.insight_aggregation_1d
}

@StringRes
fun UsageRequestRhythmPolicy.FormulaUnit.labelRes(): Int = when (this) {
    UsageRequestRhythmPolicy.FormulaUnit.SECONDS -> R.string.usage_request_unit_seconds
    UsageRequestRhythmPolicy.FormulaUnit.MINUTES -> R.string.usage_request_unit_minutes
    UsageRequestRhythmPolicy.FormulaUnit.HOURS -> R.string.usage_request_unit_hours
}

@Composable
fun formatDurationLocalized(durationMs: Long?): String {
    if (durationMs == null || durationMs < 0L) return "—"
    val totalSeconds = durationMs / 1_000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds / 3_600L) % 24L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return when {
        days > 0L -> stringResource(R.string.duration_days_hours, days, hours)
        hours > 0L -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0L -> stringResource(R.string.duration_minutes_seconds, minutes, seconds)
        else -> stringResource(R.string.duration_seconds, seconds)
    }
}

@Composable
fun formatRatioLocalized(value: Double?): String =
    UsageRequestRhythmPolicy.formatRatio(value)?.let { "${it}×" } ?: "—"
