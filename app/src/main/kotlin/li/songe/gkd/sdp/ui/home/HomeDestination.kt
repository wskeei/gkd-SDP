package li.songe.gkd.sdp.ui.home

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.ui.component.PerfIcon

/**
 * The four top-level product destinations.
 *
 * The old Control/SubsManage/AppList/Settings tabs map onto these targets;
 * legacy numeric deep links are converted by the DeepLinkParser.
 */
enum class HomeDestination(
    val key: Int,
    val labelRes: Int,
    val icon: ImageVector,
) {
    OVERVIEW(0, R.string.nav_overview, PerfIcon.Home),
    SELF_CONTROL(1, R.string.nav_self_control, PerfIcon.Eco),
    RULES(2, R.string.nav_rules, PerfIcon.FormatListBulleted),
    SETTINGS(3, R.string.nav_settings, PerfIcon.Settings),
    ;

    companion object {
        fun fromKey(key: Int): HomeDestination = entries.firstOrNull { it.key == key } ?: OVERVIEW

        val all: Array<HomeDestination> = entries.toTypedArray()
    }
}


@Serializable
data class HomeRoute(
    val tabKey: Int = HomeDestination.OVERVIEW.key,
    val rulesTab: Int = 0,
) : NavKey
