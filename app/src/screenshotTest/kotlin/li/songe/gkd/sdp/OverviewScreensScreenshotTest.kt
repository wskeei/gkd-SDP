package li.songe.gkd.sdp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.ui.home.OverviewContent
import li.songe.gkd.sdp.ui.home.OverviewContentState

@PreviewTest
@Preview(name = "Overview ready", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotOverviewReady() {
    MaterialTheme {
        OverviewContent(
            state = OverviewContentState(
                serviceTitle = stringResource(R.string.overview_service_title),
                serviceSubtitle = stringResource(R.string.overview_service_accessibility_off),
                serviceChecked = false,
                notificationTitle = stringResource(R.string.overview_notification_title),
                notificationSubtitle = stringResource(R.string.overview_notification_subtitle),
                reviewSubtitle = stringResource(R.string.usage_guard_widget_hint_empty_today),
                usedSubsItemCount = 2,
                serverStatus = "2 个订阅可用",
                latestRecordText = "示例规则",
            ),
        )
    }
}

@PreviewTest
@Preview(name = "Overview expanded", showBackground = true, widthDp = 700)
@Composable
fun ScreenshotOverviewExpanded() {
    MaterialTheme {
        OverviewContent(
            state = OverviewContentState(
                appOpsRestricted = true,
                serviceTitle = stringResource(R.string.overview_service_title),
                serviceSubtitle = stringResource(R.string.overview_service_accessibility_running),
                serviceChecked = true,
                notificationTitle = stringResource(R.string.overview_notification_title),
                notificationSubtitle = stringResource(R.string.overview_notification_subtitle),
                notificationChecked = true,
                inspectorVisible = true,
                inspectorSubtitle = "仅本机监听｜等待配对｜授权 2/4",
                reviewSubtitle = "今日 0 次申请 · 0 次拦截｜还没有新的使用申请。",
                activityLogVisible = true,
                activityLogSubtitle = stringResource(R.string.overview_activity_log_subtitle),
                usedSubsItemCount = 4,
                serverStatus = "4 个订阅可用",
                latestRecordText = "示例规则",
            ),
        )
    }
}

@PreviewTest
@Preview(
    name = "Overview action required dark en",
    showBackground = true,
    widthDp = 700,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "en",
)
@Composable
fun ScreenshotOverviewActionRequiredDarkEn() {
    MaterialTheme {
        OverviewContent(
            state = OverviewContentState(
                appOpsRestricted = true,
                serviceTitle = stringResource(R.string.overview_service_title),
                serviceSubtitle = stringResource(R.string.overview_service_accessibility_fault),
                serviceChecked = false,
                notificationTitle = stringResource(R.string.overview_notification_title),
                notificationSubtitle = stringResource(R.string.overview_notification_subtitle),
                notificationChecked = false,
                inspectorVisible = true,
                inspectorSubtitle = "Local only | Waiting to pair | 2/4 authorized",
                reviewSubtitle = "No new usage requests yet.",
                activityLogVisible = true,
                activityLogSubtitle = stringResource(R.string.overview_activity_log_subtitle),
                usedSubsItemCount = 0,
                serverStatus = "2 subscriptions available",
                latestRecordText = "Sample rule",
            ),
        )
    }
}
