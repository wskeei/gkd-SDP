@file:JvmName("UsageReviewPreview")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.ui.component.localizedReviewPage
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.data.UsageReviewRow
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineTrendChart
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import java.time.LocalDate
import java.time.ZoneId

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun UsageGuardReviewPagePreviewUsageData() {
    AppTheme { ReviewPreviewContent(previewSummary(DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest, hasData = true)) }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun UsageGuardReviewPagePreviewUsageEmpty() {
    AppTheme { ReviewPreviewContent(previewSummary(DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest, hasData = false)) }
}

@Preview(showBackground = true, widthDp = 600)
@Composable
internal fun UsageGuardReviewPagePreviewInterceptData() {
    AppTheme { ReviewPreviewContent(previewSummary(DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt, hasData = true)) }
}

@Preview(showBackground = true, widthDp = 600, fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun UsageGuardReviewPagePreviewWideDarkLargeText() {
    AppTheme(invertedTheme = true) {
        ReviewPreviewContent(previewSummary(DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest, hasData = true))
    }
}

@Composable
internal fun ReviewPreviewContent(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary) {
    val page = localizedReviewPage(summary)
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { OverviewCard(page) }
        item {
            ReviewSectionCard(
                stringResource(R.string.usage_review_trend_title, stringResource(page.trend.metricRes)),
                stringResource(R.string.usage_review_preview_subtitle),
            ) {
                DigitalSelfDisciplineTrendChart(page.trend)
            }
        }
        page.distributions.forEachIndexed { index, distribution ->
            item { ReviewRankedBarList(distribution.titleRes, distribution.bars) }
        }
        item { RecentRowsCard(page.recentRows) }
    }
}

internal fun previewSummary(
    type: DigitalSelfDisciplineReviewPolicy.ReviewType,
    hasData: Boolean,
): DigitalSelfDisciplineReviewPolicy.ReviewSummary {
    val zone = ZoneId.of("Asia/Shanghai")
    val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
        DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
        LocalDate.of(2026, 8, 4).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli(),
        zone,
    )
    val rows = if (type == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest && hasData) {
        listOf(
            // i18n-ignore: legacy fallback or non-display heuristic data
            UsageReviewRow(1L, "demo.reader", "阅读应用", listOf("学习"), 30, bounds.startAt + 1_000L, 5, 90L * 60_000L),
            // i18n-ignore: legacy fallback or non-display heuristic data
            UsageReviewRow(2L, "demo.reader", "阅读应用", listOf("学习", "其他"), 45, bounds.startAt + 2_000L, 1, 150L * 60_000L),
        )
    } else {
        emptyList()
    }
    val events = if (type == DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt && hasData) {
        listOf(
            // i18n-ignore: legacy fallback or non-display heuristic data
            SelfControlAttemptEvent(1L, "demo.rule", SelfControlAttempt.KIND_SELECTOR_INTERCEPT, "demo.rule", "示例规则", bounds.startAt + 1_000L, 30L * 60_000L),
            // i18n-ignore: legacy fallback or non-display heuristic data
            SelfControlAttemptEvent(2L, "demo.rule", SelfControlAttempt.KIND_SELECTOR_INTERCEPT, "demo.rule", "示例规则", bounds.startAt + 2_000L, 45L * 60_000L),
        )
    } else {
        emptyList()
    }
    return DigitalSelfDisciplineReviewPolicy.summarize(
        usageRows = rows,
        events = events,
        bounds = bounds,
        reviewType = type,
        interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
        zoneId = zone,
    )
}

internal fun previewSummaryDense30Day(): DigitalSelfDisciplineReviewPolicy.ReviewSummary {
    val zone = ZoneId.of("Asia/Shanghai")
    val now = LocalDate.of(2026, 8, 4).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
    val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
        DigitalSelfDisciplineReviewPolicy.Range.LAST_30_DAYS,
        now,
        zone,
    )
    val rows = (0 until 90).map { index ->
        val occurredAt = bounds.startAt + index * ((bounds.endAt - bounds.startAt) / 90)
        UsageReviewRow(
            id = index.toLong(),
            appId = if (index % 3 == 0) "demo.work" else "demo.reader",
            // i18n-ignore: legacy fallback or non-display heuristic data
            appName = if (index % 3 == 0) "工作应用" else "阅读应用",
            // i18n-ignore: legacy fallback or non-display heuristic data
            tagNames = listOf(if (index % 2 == 0) "学习" else "工作", "其他"),
            requestedDurationMinutes = 15 + (index % 5) * 10,
            requestedAt = occurredAt,
            endReason = UsageGuardRecord.END_REASON_USER_TERMINATED,
            requestGapMs = if (index == 0) {
                120L * 60_000L
            } else {
                (6L * 60L + (index % 6) * 10L) * 60_000L
            },
        )
    }
    return DigitalSelfDisciplineReviewPolicy.summarize(
        usageRows = rows,
        events = emptyList(),
        bounds = bounds,
        reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
        interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
        zoneId = zone,
    )
}
