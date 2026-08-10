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

/**
 * Rules: two second-level tabs — subscription rules and app rules — keeping
 * the full subscription management and app list capabilities.
 */
@Composable
fun RulesScreen(initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val page = if (tab == 0) useSubsManagePage() else useAppListPage()
    Column(modifier = Modifier) {
        page.topBar()
        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.subscription_rules_tab)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.s_da6a6dc1af)) },
            )
        }
        page.content(PaddingValues())
    }
}
