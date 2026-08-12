package li.songe.gkd.sdp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import li.songe.gkd.sdp.ui.component.InterceptionSourceCard
import li.songe.gkd.sdp.ui.component.InterceptionSourcePresentation
import li.songe.gkd.sdp.ui.component.UsageDurationRatioFeedback
import li.songe.gkd.sdp.ui.component.UsageRequestRhythmPresentation

@PreviewTest
@Preview(name = "Interception source missing", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotInterceptionSourceMissing() {
    MaterialTheme {
        InterceptionSourceCard(
            presentation = InterceptionSourcePresentation.unknown(),
        )
    }
}

@PreviewTest
@Preview(name = "Interception source full", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotInterceptionSourceFull() {
    MaterialTheme {
        InterceptionSourceCard(
            presentation = InterceptionSourcePresentation.url(1L, "Sample URL rule"),
        )
    }
}

@PreviewTest
@Preview(name = "Usage request dense", showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
fun ScreenshotUsageRequestDense() {
    MaterialTheme {
        UsageDurationRatioFeedback(
            presentation = UsageRequestRhythmPresentation.from(
                data = null,
                nowEpochMs = 1_000L,
                requestedDurationMinutes = 15,
            ),
        )
    }
}

@PreviewTest
@Preview(
    name = "Usage request dark large en",
    showBackground = true,
    widthDp = 700,
    fontScale = 2f,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "en",
)
@Composable
fun ScreenshotUsageRequestDarkLargeEn() {
    MaterialTheme {
        li.songe.gkd.sdp.service.UsageGuardRequestContent(
            appName = "Reader",
            tags = listOf(
                li.songe.gkd.sdp.data.UsageGuardTag(id = 1, name = "Work", isPreset = true, createdAt = 0L),
                li.songe.gkd.sdp.data.UsageGuardTag(id = 2, name = "Other", isPreset = true, createdAt = 0L),
            ),
            grantMode = li.songe.gkd.sdp.util.UsageGuardPolicy.GRANT_MODE_RESUMABLE,
            minReasonLength = 8,
            elapsedState = li.songe.gkd.sdp.util.SelfControlElapsedPolicy.ElapsedState.NoHistory,
            rhythmData = null,
            samples = emptyList(),
            insightAnchorAt = null,
            selectedWindow = li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            onWindowSelected = {},
            selectedMetric = li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy.Metric.INTERVAL,
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
