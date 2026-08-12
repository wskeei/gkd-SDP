package li.songe.gkd.sdp.ui.home

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.ui.overview.OverviewScreen
import li.songe.gkd.sdp.ui.rules.RulesScreen
import li.songe.gkd.sdp.ui.selfcontrol.SelfControlHubScreen

@Composable
fun HomePage(route: HomeRoute) {
    viewModel<HomeVm>() // init state
    val destination = HomeDestination.fromKey(route.tabKey)
    AdaptiveHomeScaffold(destination = destination) { current ->
        when (current) {
            HomeDestination.OVERVIEW -> OverviewScreen()
            HomeDestination.SELF_CONTROL -> SelfControlHubScreen()
            HomeDestination.RULES -> RulesScreen(initialTab = route.rulesTab)
            HomeDestination.SETTINGS -> SettingsScreen()
        }
    }
}
