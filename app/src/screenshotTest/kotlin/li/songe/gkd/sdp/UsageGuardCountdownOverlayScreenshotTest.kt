package li.songe.gkd.sdp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import li.songe.gkd.sdp.service.UsageGuardCountdownOverlayContent

private const val PREVIEW_EXPIRES_AT = 60_000L
private const val PREVIEW_MAX_PILL_WIDTH_PX = 720

@PreviewTest
@Preview(name = "Countdown pill compact", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotUsageGuardCountdownPillCompact() {
    MaterialTheme {
        UsageGuardCountdownOverlayContent(
            expiresAt = PREVIEW_EXPIRES_AT,
            reasonText = "完成工作后使用阅读应用",
            maxPillWidthPx = PREVIEW_MAX_PILL_WIDTH_PX,
            showTerminateConfirm = false,
            onPillTap = {},
            onDrag = { _, _ -> },
            onExpired = {},
            onDismissTerminate = {},
            onHideForScreenshot = {},
            onConfirmTerminate = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Countdown control screenshot mode",
    showBackground = true,
    widthDp = 360,
    heightDp = 640,
)
@Composable
fun ScreenshotUsageGuardCountdownControlScreen() {
    MaterialTheme {
        UsageGuardCountdownOverlayContent(
            expiresAt = PREVIEW_EXPIRES_AT,
            reasonText = "完成工作后使用阅读应用",
            maxPillWidthPx = PREVIEW_MAX_PILL_WIDTH_PX,
            showTerminateConfirm = true,
            onPillTap = {},
            onDrag = { _, _ -> },
            onExpired = {},
            onDismissTerminate = {},
            onHideForScreenshot = {},
            onConfirmTerminate = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Countdown control dark large text",
    showBackground = true,
    widthDp = 700,
    heightDp = 900,
    fontScale = 2f,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "en",
)
@Composable
fun ScreenshotUsageGuardCountdownControlDarkLargeText() {
    MaterialTheme {
        UsageGuardCountdownOverlayContent(
            expiresAt = PREVIEW_EXPIRES_AT,
            reasonText = "Complete the task before opening the reader",
            maxPillWidthPx = PREVIEW_MAX_PILL_WIDTH_PX,
            showTerminateConfirm = true,
            onPillTap = {},
            onDrag = { _, _ -> },
            onExpired = {},
            onDismissTerminate = {},
            onHideForScreenshot = {},
            onConfirmTerminate = {},
        )
    }
}
