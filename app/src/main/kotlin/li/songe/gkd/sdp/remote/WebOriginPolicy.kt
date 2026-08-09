package li.songe.gkd.sdp.remote

import java.net.URI

enum class WebNavigationDecision {
    ALLOW,
    INTERNAL,
    EXTERNAL,
    BLOCK,
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
            uri.rawFragment != null
        ) return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty().ifEmpty { "/" }
        val semantic = when (host) {
            "overview", "self-control", "settings", "usage-guard", "usage-review",
            "action-log" -> path == "/" && uri.rawQuery == null
            "rules" -> path in setOf("/subscriptions", "/apps") && uri.rawQuery == null
            else -> false
        }
        if (semantic) return true
        // Removed by the semantic navigation migration in Task 11; kept only for 2.2.0 upgrades.
        return when (host) {
            "page" -> {
                val tab = uri.rawQuery?.substringAfter("tab=", missingDelimiterValue = "")
                (path in setOf("/", "/0", "/1", "/2", "/3", "/4") && uri.rawQuery == null) ||
                    (path == "/" && tab in setOf("0", "1", "2", "3"))
            }
            "invoke" -> path == "/1" && uri.rawQuery == null
            else -> false
        }
    }

    private fun URI.effectivePort(): Int = if (port >= 0) port else 443
}
