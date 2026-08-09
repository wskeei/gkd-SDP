package li.songe.gkd.sdp.navigation

import androidx.navigation3.runtime.NavKey
import li.songe.gkd.sdp.ui.ActionLogRoute
import li.songe.gkd.sdp.ui.AdvancedPageRoute
import li.songe.gkd.sdp.ui.AuthA11yRoute
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.UsageGuardReviewRoute
import li.songe.gkd.sdp.ui.UsageGuardRoute
import li.songe.gkd.sdp.ui.home.HomeRoute
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
    AppDestination.OVERVIEW,
    AppDestination.RULES_SUBSCRIPTIONS,
    AppDestination.RULES_APPS,
    AppDestination.SETTINGS,
    -> HomeRoute

    AppDestination.SELF_CONTROL -> FocusLockRoute
    AppDestination.SETTINGS_CAPABILITIES -> AuthA11yRoute
    AppDestination.SETTINGS_PRIVACY_DATA -> AdvancedPageRoute
    AppDestination.USAGE_GUARD -> UsageGuardRoute
    AppDestination.USAGE_REVIEW -> UsageGuardReviewRoute
    AppDestination.ACTION_LOG -> ActionLogRoute()
}
