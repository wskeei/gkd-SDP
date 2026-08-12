package li.songe.gkd.sdp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.service.UsageGuardRequestContent
import li.songe.gkd.sdp.ui.ReviewPreviewContent
import li.songe.gkd.sdp.ui.previewSummary
import li.songe.gkd.sdp.ui.previewSummaryDense30Day
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.UsageGuardPolicy

@PreviewTest
@Preview(name = "Usage request form compact", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotUsageRequestFormCompact() {
    MaterialTheme {
        UsageGuardRequestContent(
            appName = "阅读应用",
            tags = listOf(
                UsageGuardTag(id = 1, name = "工作", isPreset = true, createdAt = 0L),
                UsageGuardTag(id = 2, name = "其他", isPreset = true, createdAt = 0L),
            ),
            grantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
            minReasonLength = 8,
            elapsedState = SelfControlElapsedPolicy.ElapsedState.NoHistory,
            rhythmData = null,
            samples = emptyList(),
            insightAnchorAt = null,
            selectedWindow = SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            onWindowSelected = {},
            selectedMetric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
            onMetricSelected = {},
            nowEpochMs = 1_000L,
            supportsUsageRatio = true,
            durationOptions = listOf(10, 15, 30, 60),
            onAddTag = { _, _ -> },
            isSubmitting = false,
            submitError = null,
            onSubmit = { _, _, _ -> },
            onCancel = {},
        )
    }
}

@PreviewTest
@Preview(name = "Review dashboard data", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotReviewDashboardData() {
    MaterialTheme {
        ReviewPreviewContent(
            previewSummary(
                type = li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
                hasData = true,
            ),
        )
    }
}

@PreviewTest
@Preview(
    name = "Review dashboard empty dark en",
    showBackground = true,
    widthDp = 700,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "en",
)
@Composable
fun ScreenshotReviewDashboardEmptyDarkEn() {
    MaterialTheme {
        ReviewPreviewContent(
            previewSummary(
                type = li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
                hasData = false,
            ),
        )
    }
}

@PreviewTest
@Preview(
    name = "Review dashboard 30 day dense",
    showBackground = true,
    widthDp = 1000,
    fontScale = 2f,
)
@Composable
fun ScreenshotReviewDashboard30DayDense() {
    MaterialTheme {
        ReviewPreviewContent(
            previewSummaryDense30Day(),
        )
    }
}

@PreviewTest
@Preview(
    name = "Review dashboard dark large text",
    showBackground = true,
    widthDp = 700,
    fontScale = 2f,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ScreenshotReviewDashboardDarkLargeText() {
    MaterialTheme {
        ReviewPreviewContent(
            previewSummary(
                type = li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt,
                hasData = true,
            ),
        )
    }
}
