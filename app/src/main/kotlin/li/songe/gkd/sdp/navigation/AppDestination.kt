package li.songe.gkd.sdp.navigation

import androidx.navigation3.runtime.NavKey
import li.songe.gkd.sdp.ui.ActionLogRoute
import li.songe.gkd.sdp.ui.AdvancedPageRoute
import li.songe.gkd.sdp.ui.AppOpsAllowRoute
import li.songe.gkd.sdp.ui.SnapshotPageRoute
import li.songe.gkd.sdp.ui.UsageGuardReviewRoute
import li.songe.gkd.sdp.ui.UsageGuardRoute
import li.songe.gkd.sdp.ui.home.HomeDestination
import li.songe.gkd.sdp.ui.home.HomeRoute
import li.songe.gkd.sdp.ui.privacy.PrivacyDataRoute
import kotlinx.serialization.Serializable

/** Stable, user-facing destinations used by notifications, widgets and deep links. */
@Serializable
enum class AppDestination {
    OVERVIEW,
    SELF_CONTROL,
    RULES_SUBSCRIPTIONS,
    RULES_APPS,
    SETTINGS,
    SETTINGS_CAPABILITIES,
    SETTINGS_PRIVACY_DATA,
    SNAPSHOTS,
    USAGE_GUARD,
    USAGE_REVIEW,
    ACTION_LOG,
}

sealed interface DeepLinkParseResult {
    data class Destination(val value: AppDestination) : DeepLinkParseResult
    data object Invalid : DeepLinkParseResult
}

/** Maps a stable destination to the existing Navigation 3 page key. */
fun AppDestination.toNavKey(): NavKey = when (this) {
    AppDestination.OVERVIEW -> HomeRoute(HomeDestination.OVERVIEW.key)
    AppDestination.RULES_SUBSCRIPTIONS -> HomeRoute(HomeDestination.RULES.key, rulesTab = 0)
    AppDestination.RULES_APPS -> HomeRoute(HomeDestination.RULES.key, rulesTab = 1)
    AppDestination.SETTINGS -> HomeRoute(HomeDestination.SETTINGS.key)

    AppDestination.SELF_CONTROL -> HomeRoute(HomeDestination.SELF_CONTROL.key)
    AppDestination.SETTINGS_CAPABILITIES -> AppOpsAllowRoute
    AppDestination.SETTINGS_PRIVACY_DATA -> PrivacyDataRoute
    AppDestination.SNAPSHOTS -> SnapshotPageRoute
    AppDestination.USAGE_GUARD -> UsageGuardRoute
    AppDestination.USAGE_REVIEW -> UsageGuardReviewRoute
    AppDestination.ACTION_LOG -> ActionLogRoute()
}
