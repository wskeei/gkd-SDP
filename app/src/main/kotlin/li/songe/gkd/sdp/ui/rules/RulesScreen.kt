package li.songe.gkd.sdp.ui.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.ui.home.useAppListPage
import li.songe.gkd.sdp.ui.home.useSubsManagePage

enum class RulesTab {
    SUBSCRIPTIONS,
    APPS,
}

/**
 * Rules: two second-level tabs — subscription rules and app rules — keeping
 * the full subscription management and app list capabilities.
 */
@Composable
fun RulesScreen(initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val selectedTab = if (tab == 0) RulesTab.SUBSCRIPTIONS else RulesTab.APPS
    val page = when (selectedTab) {
        RulesTab.SUBSCRIPTIONS -> useSubsManagePage()
        RulesTab.APPS -> useAppListPage()
    }
    RulesHubContent(
        selectedTab = selectedTab,
        onTabSelected = { target ->
            tab = if (target == RulesTab.SUBSCRIPTIONS) 0 else 1
        },
        topBar = page.topBar,
        content = page.content,
    )
}

@Composable
internal fun RulesHubContent(
    selectedTab: RulesTab,
    onTabSelected: (RulesTab) -> Unit,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier = Modifier) {
        topBar()
        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Tab(
                selected = selectedTab == RulesTab.SUBSCRIPTIONS,
                onClick = { onTabSelected(RulesTab.SUBSCRIPTIONS) },
                text = { Text(stringResource(R.string.subscription_rules_tab)) },
            )
            Tab(
                selected = selectedTab == RulesTab.APPS,
                onClick = { onTabSelected(RulesTab.APPS) },
                text = { Text(stringResource(R.string.s_da6a6dc1af)) },
            )
        }
        content(PaddingValues())
    }
}
