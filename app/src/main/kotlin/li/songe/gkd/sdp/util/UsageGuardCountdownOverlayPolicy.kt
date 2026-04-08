package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import java.util.Locale

object UsageGuardCountdownOverlayPolicy {
    fun formatRemainingText(
        expiresAt: Long,
        now: Long = System.currentTimeMillis(),
    ): String {
        val remainingSeconds = ((expiresAt - now).coerceAtLeast(0L) + 999L) / 1000L
        val hours = remainingSeconds / 3600L
        val minutes = (remainingSeconds % 3600L) / 60L
        val seconds = remainingSeconds % 60L

        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun shouldDisplay(
        activeRecord: UsageGuardRecord?,
        foregroundAppId: String,
        requestOverlayAppId: String?,
        timeoutOverlayAppId: String?,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (activeRecord == null) return false
        if (foregroundAppId != activeRecord.appId) return false
        if (activeRecord.endedAt != 0L) return false
        if (activeRecord.expiresAt <= now) return false
        if (requestOverlayAppId == activeRecord.appId) return false
        if (timeoutOverlayAppId == activeRecord.appId) return false
        return true
    }
}
