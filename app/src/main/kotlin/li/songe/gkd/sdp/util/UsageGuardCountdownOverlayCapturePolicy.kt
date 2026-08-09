package li.songe.gkd.sdp.util

data class UsageGuardCountdownOverlaySession(
    val appId: String,
    val recordId: Long,
    val expiresAt: Long,
    val leaseId: Long,
    val runtimeGeneration: Long,
) {
    fun isValid(now: Long? = null): Boolean {
        return appId.isNotBlank() &&
            recordId > 0L &&
            expiresAt > 0L &&
            leaseId > 0L &&
            runtimeGeneration > 0L &&
            (now == null || expiresAt > now)
    }

    fun toLease() = UsageGuardCountdownOverlayLease(
        appId = appId,
        recordId = recordId,
        expiresAt = expiresAt,
        leaseId = leaseId,
        runtimeGeneration = runtimeGeneration,
    )
}

data class UsageGuardCountdownOverlayLease(
    val appId: String,
    val recordId: Long,
    val expiresAt: Long,
    val leaseId: Long,
    val runtimeGeneration: Long,
)

object UsageGuardCountdownOverlayCapturePolicy {
    const val HIDE_DURATION_MS = 10_000L

    fun shouldRestore(
        hidden: UsageGuardCountdownOverlaySession,
        current: UsageGuardCountdownOverlaySession,
        now: Long,
    ): Boolean {
        return hidden == current && hidden.isValid(now)
    }

    fun isLeaseActive(
        lease: UsageGuardCountdownOverlayLease?,
        session: UsageGuardCountdownOverlaySession,
        foregroundAppId: String,
        currentRuntimeGeneration: Long?,
    ): Boolean {
        return lease == session.toLease() &&
            foregroundAppId == session.appId &&
            currentRuntimeGeneration == session.runtimeGeneration
    }
}
