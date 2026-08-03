package li.songe.gkd.sdp.a11y

/**
 * Tracks the blocking usage-guard overlay that is currently mounted.
 *
 * This state is deliberately independent from the runtime owner. Overlay
 * service callbacks can arrive after the accessibility runtime has detached,
 * so lifecycle callbacks must still be able to clear the state without first
 * requiring a live owner.
 */
internal class UsageGuardBlockingOverlayState {
    var requestAppId: String? = null
        private set

    var timeoutAppId: String? = null
        private set

    val hasBlockingOverlay: Boolean
        get() = requestAppId != null || timeoutAppId != null

    val activeKind: String?
        get() = when {
            requestAppId != null -> "request"
            timeoutAppId != null -> "timeout"
            else -> null
        }

    fun markRequestStarted(appId: String) {
        requestAppId = appId
        timeoutAppId = null
    }

    fun markTimeoutStarted(appId: String) {
        requestAppId = null
        timeoutAppId = appId
    }

    fun clearRequest(appId: String?): Boolean {
        if (appId != null && requestAppId != appId) return false
        val changed = requestAppId != null
        requestAppId = null
        return changed
    }

    fun clearTimeout(appId: String?): Boolean {
        if (appId != null && timeoutAppId != appId) return false
        val changed = timeoutAppId != null
        timeoutAppId = null
        return changed
    }

    fun clearAll() {
        requestAppId = null
        timeoutAppId = null
    }
}
