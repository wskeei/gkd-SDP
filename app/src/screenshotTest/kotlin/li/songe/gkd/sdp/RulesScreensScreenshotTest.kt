package li.songe.gkd.sdp

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import li.songe.gkd.sdp.ui.component.ContentState
import li.songe.gkd.sdp.ui.component.ContentStateBox
import li.songe.gkd.sdp.ui.rules.RulesHubContent
import li.songe.gkd.sdp.ui.rules.RulesTab

@PreviewTest
@Preview(name = "Rules subscriptions compact", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotRulesSubscriptionsCompact() {
    MaterialTheme {
        RulesHubContent(
            selectedTab = RulesTab.SUBSCRIPTIONS,
            onTabSelected = {},
            topBar = {
                Text(
                    text = stringResource(R.string.nav_subscriptions),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            },
            content = {
                ContentStateBox(
                    state = ContentState.Empty(
                        title = stringResource(R.string.subscription_rules_tab),
                        description = stringResource(R.string.common_empty),
                    ),
                    content = {},
                )
            },
        )
    }
}

@PreviewTest
@Preview(
    name = "Rules subscriptions dark en",
    showBackground = true,
    widthDp = 360,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "en",
)
@Composable
fun ScreenshotRulesSubscriptionsDarkEn() {
    MaterialTheme {
        RulesHubContent(
            selectedTab = RulesTab.SUBSCRIPTIONS,
            onTabSelected = {},
            topBar = {
                Text(
                    text = stringResource(R.string.nav_subscriptions),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            },
            content = {
                ContentStateBox(
                    state = ContentState.Empty(
                        title = stringResource(R.string.subscription_rules_tab),
                        description = stringResource(R.string.common_empty),
                    ),
                    content = {},
                )
            },
        )
    }
}

@PreviewTest
@Preview(
    name = "Rules app rules expanded dark",
    showBackground = true,
    widthDp = 700,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "en",
)
@Composable
fun ScreenshotRulesAppRulesExpandedDark() {
    MaterialTheme {
        RulesHubContent(
            selectedTab = RulesTab.APPS,
            onTabSelected = {},
            topBar = {
                Text(
                    text = stringResource(R.string.nav_apps),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            },
            content = {
                ContentStateBox(
                    state = ContentState.Empty(
                        title = stringResource(R.string.s_da6a6dc1af),
                        description = stringResource(R.string.common_empty),
                    ),
                    content = {},
                )
            },
        )
    }
}
