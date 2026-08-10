package li.songe.gkd.sdp.ui.home

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.ui.component.PerfIcon

/**
 * The four top-level product destinations.
 *
 * The old Control/SubsManage/AppList/Settings tabs map onto these targets;
 * legacy numeric deep links are converted by the DeepLinkParser.
 */
enum class HomeDestination(
    val key: Int,
    val label: String,
    val icon: ImageVector,
) {
    OVERVIEW(0, "概览", PerfIcon.Home),
    SELF_CONTROL(1, "自律", PerfIcon.Eco),
    RULES(2, "规则", PerfIcon.FormatListBulleted),
    SETTINGS(3, "设置", PerfIcon.Settings),
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
