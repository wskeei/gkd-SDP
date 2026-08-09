package li.songe.gkd.sdp.util

/**
 * Pure state machine for temporarily removing the secure countdown overlay.
 * Window operations and scheduling stay in the Service; this class fences their
 * results to the exact usage/runtime lease that initiated the hide operation.
 */
class UsageGuardCountdownOverlayCaptureController {
    enum class StartAction {
        CREATE_AND_MOUNT,
        KEEP_MOUNTED,
        KEEP_HIDDEN,
        RESET_AND_MOUNT,
        IGNORE_TERMINAL,
    }

    enum class RestoreAction {
        MOUNT,
        STOP_EXPIRED,
        STOP_REVOKED,
        IGNORE,
    }

    private var currentSession: UsageGuardCountdownOverlaySession? = null

    var isMounted: Boolean = false
        private set

    var isTerminal: Boolean = false
        private set

    fun onStart(
        session: UsageGuardCountdownOverlaySession,
        hasView: Boolean,
    ): StartAction {
        if (isTerminal || !session.isValid()) return StartAction.IGNORE_TERMINAL
        val previousSession = currentSession
        currentSession = session
        if (!hasView) return StartAction.CREATE_AND_MOUNT
        if (previousSession != session) return StartAction.RESET_AND_MOUNT
        return if (isMounted) StartAction.KEEP_MOUNTED else StartAction.KEEP_HIDDEN
    }

    fun onMountSucceeded(): Boolean {
        if (isTerminal || currentSession == null) return false
        isMounted = true
        return true
    }

    fun onMountFailed() {
        isMounted = false
        isTerminal = true
    }

    fun snapshotForHide(): UsageGuardCountdownOverlaySession? {
        if (isTerminal || !isMounted) return null
        return currentSession
    }

    fun onHideResult(
        hidden: UsageGuardCountdownOverlaySession,
        removed: Boolean,
    ): Boolean {
        if (!removed) return false
        isMounted = false
        return !isTerminal && currentSession == hidden
    }

    fun restoreAction(
        hidden: UsageGuardCountdownOverlaySession,
        now: Long,
        leaseActive: Boolean,
    ): RestoreAction {
        if (isTerminal || isMounted) return RestoreAction.IGNORE
        val current = currentSession ?: return RestoreAction.IGNORE
        if (hidden != current) return RestoreAction.IGNORE
        if (current.expiresAt <= now) return RestoreAction.STOP_EXPIRED
        if (!leaseActive) return RestoreAction.STOP_REVOKED
        return if (
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(hidden, current, now)
        ) {
            RestoreAction.MOUNT
        } else {
            RestoreAction.IGNORE
        }
    }

    fun onDestroy() {
        currentSession = null
        isMounted = false
        isTerminal = true
    }
}
