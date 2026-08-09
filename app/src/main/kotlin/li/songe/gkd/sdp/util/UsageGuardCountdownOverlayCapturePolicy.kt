package li.songe.gkd.sdp.util

object UsageGuardCountdownOverlayCapturePolicy {
    const val HIDE_DURATION_MS = 10_000L

    fun shouldRestore(
        hiddenAppId: String,
        hiddenRecordId: Long,
        currentAppId: String,
        currentRecordId: Long,
        expiresAt: Long,
        now: Long,
    ): Boolean {
        return hiddenAppId.isNotBlank() &&
            hiddenRecordId > 0L &&
            hiddenAppId == currentAppId &&
            hiddenRecordId == currentRecordId &&
            expiresAt > now
    }
}
