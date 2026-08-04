package li.songe.gkd.sdp.util

import java.math.BigDecimal
import li.songe.gkd.sdp.data.UsageGuardRecord

/** Pure lifecycle decisions for recording the last real foreground-use end. */
object UsageGuardUsageEndPolicy {
    enum class LeaveDecision {
        MARK_AND_CLOSE,
        MARK_ONLY,
        CLOSE_ONLY,
    }

    fun onLeave(grantMode: Int): LeaveDecision = if (
        grantMode == UsageGuardPolicy.GRANT_MODE_STRICT
    ) {
        LeaveDecision.MARK_AND_CLOSE
    } else {
        LeaveDecision.MARK_ONLY
    }

    fun shouldClearCandidateOnReturn(grantMode: Int): Boolean =
        grantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE

    fun onForegroundExpiry(): LeaveDecision = LeaveDecision.MARK_AND_CLOSE

    fun onExplicitTerminate(): LeaveDecision = LeaveDecision.MARK_AND_CLOSE

    fun onBackgroundExpiry(): LeaveDecision = LeaveDecision.CLOSE_ONLY

    fun monotonicEnd(existing: Long?, candidate: Long): Long {
        if (candidate < 0L) return existing ?: 0L
        return maxOf(existing ?: candidate, candidate)
    }

    fun newRequestGapMs(
        activeRecordPresent: Boolean,
        previousEndAt: Long?,
        requestedAt: Long,
    ): Long? {
        if (activeRecordPresent || previousEndAt == null) return null
        if (previousEndAt < 0L || requestedAt < previousEndAt) return null
        return BigDecimal.valueOf(requestedAt)
            .subtract(BigDecimal.valueOf(previousEndAt))
            .min(BigDecimal.valueOf(Long.MAX_VALUE))
            .toLong()
    }

    fun shouldMarkUsageEnded(record: UsageGuardRecord): Boolean = record.endedAt == 0L
}
