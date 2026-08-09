package li.songe.gkd.sdp.remote

import java.net.URI

enum class WebNavigationDecision {
    ALLOW,
    INTERNAL,
    EXTERNAL,
    BLOCK,
}

enum class SemanticDeepLinkTarget {
    OVERVIEW,
    SELF_CONTROL,
    SETTINGS,
    USAGE_GUARD,
    USAGE_REVIEW,
    ACTION_LOG,
    RULE_SUBSCRIPTIONS,
    RULE_APPS,
}

enum class LegacyDeepLinkTarget {
    OVERVIEW,
    SUBSCRIPTIONS,
    APPS,
    SETTINGS,
    ADVANCED,
    SNAPSHOT,
    APP_OPS,
    SELF_CONTROL,
    WECHAT_SCANNER,
}

object WebOriginPolicy {
    private const val DOCUMENT_HOST = "gkd.li"
    private const val MIRROR_HOST = "registry.npmmirror.com"
    private const val MIRROR_PATH_PREFIX = "/@gkd-kit/docs/"

    fun decide(url: String): WebNavigationDecision {
        val uri = runCatching { URI(url) }.getOrNull() ?: return WebNavigationDecision.BLOCK
        val scheme = uri.scheme?.lowercase() ?: return WebNavigationDecision.BLOCK
        return when {
            scheme == "gkd" && isAllowedInternalDeepLink(url) -> WebNavigationDecision.INTERNAL
            scheme == "https" && uri.host.equals(DOCUMENT_HOST, ignoreCase = true) -> {
                if (uri.rawUserInfo == null && uri.effectivePort() == 443) {
                    WebNavigationDecision.ALLOW
                } else {
                    WebNavigationDecision.BLOCK
                }
            }
            scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null ->
                WebNavigationDecision.EXTERNAL
            else -> WebNavigationDecision.BLOCK
        }
    }

    fun isAllowedMirror(url: String): Boolean {
        val uri = runCatching { URI(url).normalize() }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(MIRROR_HOST, ignoreCase = true) &&
            uri.rawUserInfo == null &&
            uri.effectivePort() == 443 &&
            uri.rawPath?.startsWith(MIRROR_PATH_PREFIX) == true &&
            uri.rawFragment == null
    }

    fun isAllowedInternalDeepLink(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (
            !uri.scheme.equals("gkd", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null ||
            uri.port != -1
        ) return false
        if (semanticDeepLinkTarget(uri) != null) return true
        // Removed by the semantic navigation migration in Task 11; kept only for 2.2.0 upgrades.
        return legacyDeepLinkTarget(uri) != null
    }

    fun semanticDeepLinkTarget(url: String): SemanticDeepLinkTarget? =
        runCatching { URI(url) }.getOrNull()?.let(::semanticDeepLinkTarget)

    fun legacyDeepLinkTarget(url: String): LegacyDeepLinkTarget? =
        runCatching { URI(url) }.getOrNull()?.let(::legacyDeepLinkTarget)

    private fun semanticDeepLinkTarget(uri: URI): SemanticDeepLinkTarget? {
        if (
            !uri.scheme.equals("gkd", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null ||
            uri.rawQuery != null ||
            uri.port != -1
        ) return null
        val path = uri.path.orEmpty().ifEmpty { "/" }
        return when (uri.host?.lowercase()) {
            "overview" -> SemanticDeepLinkTarget.OVERVIEW.takeIf { path == "/" }
            "self-control" -> SemanticDeepLinkTarget.SELF_CONTROL.takeIf { path == "/" }
            "settings" -> SemanticDeepLinkTarget.SETTINGS.takeIf { path == "/" }
            "usage-guard" -> SemanticDeepLinkTarget.USAGE_GUARD.takeIf { path == "/" }
            "usage-review" -> SemanticDeepLinkTarget.USAGE_REVIEW.takeIf { path == "/" }
            "action-log" -> SemanticDeepLinkTarget.ACTION_LOG.takeIf { path == "/" }
            "rules" -> when (path) {
                "/subscriptions" -> SemanticDeepLinkTarget.RULE_SUBSCRIPTIONS
                "/apps" -> SemanticDeepLinkTarget.RULE_APPS
                else -> null
            }
            else -> null
        }
    }

    private fun legacyDeepLinkTarget(uri: URI): LegacyDeepLinkTarget? {
        if (
            !uri.scheme.equals("gkd", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null ||
            uri.port != -1
        ) return null
        val path = uri.path.orEmpty().ifEmpty { "/" }
        return when (uri.host?.lowercase()) {
            "page" -> when {
                path == "/" && uri.rawQuery == null -> LegacyDeepLinkTarget.OVERVIEW
                path == "/" && uri.rawQuery == "tab=0" -> LegacyDeepLinkTarget.OVERVIEW
                path == "/" && uri.rawQuery == "tab=1" -> LegacyDeepLinkTarget.SUBSCRIPTIONS
                path == "/" && uri.rawQuery == "tab=2" -> LegacyDeepLinkTarget.APPS
                path == "/" && uri.rawQuery == "tab=3" -> LegacyDeepLinkTarget.SETTINGS
                path == "/0" && uri.rawQuery == null -> LegacyDeepLinkTarget.OVERVIEW
                path == "/1" && uri.rawQuery == null -> LegacyDeepLinkTarget.ADVANCED
                path == "/2" && uri.rawQuery == null -> LegacyDeepLinkTarget.SNAPSHOT
                path == "/3" && uri.rawQuery == null -> LegacyDeepLinkTarget.APP_OPS
                path == "/4" && uri.rawQuery == null -> LegacyDeepLinkTarget.SELF_CONTROL
                else -> null
            }
            "invoke" -> LegacyDeepLinkTarget.WECHAT_SCANNER
                .takeIf { path == "/1" && uri.rawQuery == null }
            else -> null
        }
    }

    private fun URI.effectivePort(): Int = if (port >= 0) port else 443
}
