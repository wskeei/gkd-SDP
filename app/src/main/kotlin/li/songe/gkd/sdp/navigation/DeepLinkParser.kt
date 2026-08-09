package li.songe.gkd.sdp.navigation

import java.net.URI

/** Strict parser for app-owned gkd:// links. */
object DeepLinkParser {
    fun parse(value: String): DeepLinkParseResult {
        val uri = runCatching { URI(value) }.getOrNull() ?: return DeepLinkParseResult.Invalid
        if (
            !uri.scheme.equals("gkd", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null ||
            uri.port != -1
        ) return DeepLinkParseResult.Invalid

        val path = uri.path.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery
        val destination = when (uri.host?.lowercase()) {
            "overview" -> AppDestination.OVERVIEW.takeIf { path == "/" && query == null }
            "self-control" -> AppDestination.SELF_CONTROL.takeIf { path == "/" && query == null }
            "settings" -> when (path) {
                "/" -> AppDestination.SETTINGS.takeIf { query == null }
                "/capabilities" -> AppDestination.SETTINGS_CAPABILITIES.takeIf { query == null }
                "/privacy-data" -> AppDestination.SETTINGS_PRIVACY_DATA.takeIf { query == null }
                else -> null
            }
            "snapshots" -> AppDestination.SNAPSHOTS.takeIf { path == "/" && query == null }
            "usage-guard" -> AppDestination.USAGE_GUARD.takeIf { path == "/" && query == null }
            "usage-review" -> AppDestination.USAGE_REVIEW.takeIf { path == "/" && query == null }
            "action-log" -> AppDestination.ACTION_LOG.takeIf { path == "/" && query == null }
            "rules" -> when (path) {
                "/subscriptions" -> AppDestination.RULES_SUBSCRIPTIONS.takeIf { query == null }
                "/apps" -> AppDestination.RULES_APPS.takeIf { query == null }
                else -> null
            }
            // Compatibility for links emitted before 2.2.0. The result is immediately
            // converted to a semantic destination and is never exposed to the UI.
            "page" -> parseLegacyPage(path, query)
            "invoke" -> null
            else -> null
        }
        return destination?.let(DeepLinkParseResult::Destination)
            ?: DeepLinkParseResult.Invalid
    }

    private fun parseLegacyPage(path: String, query: String?): AppDestination? = when {
        path == "/" && query == null -> AppDestination.OVERVIEW
        path == "/" && query == "tab=0" -> AppDestination.OVERVIEW
        path == "/" && query == "tab=1" -> AppDestination.RULES_SUBSCRIPTIONS
        path == "/" && query == "tab=2" -> AppDestination.RULES_APPS
        path == "/" && query == "tab=3" -> AppDestination.SETTINGS
        path == "/0" && query == null -> AppDestination.OVERVIEW
        path == "/1" && query == null -> AppDestination.SETTINGS_PRIVACY_DATA
        path == "/2" && query == null -> AppDestination.SNAPSHOTS
        path == "/3" && query == null -> AppDestination.SETTINGS_CAPABILITIES
        path == "/4" && query == null -> AppDestination.SELF_CONTROL
        else -> null
    }
}
