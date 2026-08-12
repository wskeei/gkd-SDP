package li.songe.gkd.sdp.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfTopAppBar

sealed class BottomNavItem(
    val key: Int,
    val labelRes: Int,
    val icon: ImageVector,
) {
    object Control : BottomNavItem(
        key = 0,
        labelRes = R.string.nav_home,
        icon = PerfIcon.Home,
    )

    object SubsManage : BottomNavItem(
        key = 1,
        labelRes = R.string.nav_subscriptions,
        icon = PerfIcon.FormatListBulleted,
    )

    object AppList : BottomNavItem(
        key = 2,
        labelRes = R.string.nav_apps,
        icon = PerfIcon.Apps,
    )

    object Settings : BottomNavItem(
        key = 3,
        labelRes = R.string.nav_settings,
        icon = PerfIcon.Settings,
    )

    companion object {
        val allSubObjects by lazy { arrayOf(Control, SubsManage, AppList, Settings) }
    }
}

data class ScaffoldExt(
    val navItem: BottomNavItem,
    val modifier: Modifier = Modifier,
    val topBar: @Composable () -> Unit = {
        PerfTopAppBar(title = {
            Text(
                text = stringResource(navItem.labelRes),
            )
        })
    },
    val floatingActionButton: @Composable () -> Unit = {},
    val content: @Composable (PaddingValues) -> Unit
)
