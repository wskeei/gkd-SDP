package li.songe.gkd.sdp.ui.home

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

sealed class BottomNavItem(
    val key: Int,
    val label: String,
    val icon: ImageVector,
) {
    object Control : BottomNavItem(
        key = 0,
        label = app.getString(R.string.s_ff93ad0e4e),
        icon = PerfIcon.Home,
    )

    object SubsManage : BottomNavItem(
        key = 1,
        label = app.getString(R.string.s_5319af762d),
        icon = PerfIcon.FormatListBulleted,
    )

    object AppList : BottomNavItem(
        key = 2,
        label = app.getString(R.string.s_4562024dde),
        icon = PerfIcon.Apps,
    )

    object Settings : BottomNavItem(
        key = 3,
        label = app.getString(R.string.s_7debf9cb03),
        icon = PerfIcon.Settings,
    )

    companion object {
        val allSubObjects by lazy { arrayOf(Control, SubsManage, AppList, Settings) }
    }
}

@Serializable
data class HomeRoute(val tabKey: Int = BottomNavItem.Control.key) : NavKey

@Composable
fun HomePage(route: HomeRoute) {
    val mainVm = LocalMainViewModel.current
    viewModel<HomeVm>() // init state
    val tab = route.tabKey
    val pages = arrayOf(useControlPage(), useSubsManagePage(), useAppListPage(), useSettingsPage())
    val page = pages.find { p -> p.navItem.key == tab } ?: pages.first()

    Scaffold(
        modifier = page.modifier,
        topBar = page.topBar,
        floatingActionButton = page.floatingActionButton,
        bottomBar = {
            NavigationBar {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = page.navItem.key == tab,
                        modifier = Modifier,
                        onClick = { mainVm.handleClickTab(page.navItem) },
                        icon = {
                            PerfIcon(
                                imageVector = page.navItem.icon,
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(text = page.navItem.label)
                        })
                }
            }
        },
        content = page.content
    )
}
