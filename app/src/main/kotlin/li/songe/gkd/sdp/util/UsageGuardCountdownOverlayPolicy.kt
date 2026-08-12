package li.songe.gkd.sdp.util

import android.content.Context
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.UsageGuardRecord
import java.util.Locale

object UsageGuardCountdownOverlayPolicy {
    // i18n-ignore: legacy fallback or non-display heuristic data
    const val MISSING_REASON_TEXT = "未填写申请理由"

    fun displayReasonText(reasonText: String, context: Context? = null): String {
        return reasonText.trim().ifEmpty {
            context?.getString(R.string.usage_countdown_missing_reason) ?: MISSING_REASON_TEXT
        }
    }

    fun formatRemainingDuration(remainingMillis: Long): String {
        val remainingSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val hours = remainingSeconds / 3600L
        val minutes = (remainingSeconds % 3600L) / 60L
        val seconds = remainingSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
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
