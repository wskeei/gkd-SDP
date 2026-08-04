package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardUsageEndPolicyTest {
    @Test
    fun strictLeaveMarksAndClosesWhileResumableLeaveOnlyMarks() {
        assertEquals(
            UsageGuardUsageEndPolicy.LeaveDecision.MARK_AND_CLOSE,
            UsageGuardUsageEndPolicy.onLeave(UsageGuardPolicy.GRANT_MODE_STRICT),
        )
        assertEquals(
            UsageGuardUsageEndPolicy.LeaveDecision.MARK_ONLY,
            UsageGuardUsageEndPolicy.onLeave(UsageGuardPolicy.GRANT_MODE_RESUMABLE),
        )
    }

    @Test
    fun returningToResumableUsageClearsPreviousCandidate() {
        assertTrue(
            UsageGuardUsageEndPolicy.shouldClearCandidateOnReturn(
                UsageGuardPolicy.GRANT_MODE_RESUMABLE,
            )
        )
        assertFalse(
            UsageGuardUsageEndPolicy.shouldClearCandidateOnReturn(
                UsageGuardPolicy.GRANT_MODE_STRICT,
            )
        )
    }

    @Test
    fun foregroundExpiryAndExplicitTerminateMarkAndClose() {
        assertEquals(
            UsageGuardUsageEndPolicy.LeaveDecision.MARK_AND_CLOSE,
            UsageGuardUsageEndPolicy.onForegroundExpiry(),
        )
        assertEquals(
            UsageGuardUsageEndPolicy.LeaveDecision.MARK_AND_CLOSE,
            UsageGuardUsageEndPolicy.onExplicitTerminate(),
        )
    }

    @Test
    fun backgroundExpiryClosesWithoutReplacingKnownEnd() {
        assertEquals(
            UsageGuardUsageEndPolicy.LeaveDecision.CLOSE_ONLY,
            UsageGuardUsageEndPolicy.onBackgroundExpiry(),
        )
    }

    @Test
    fun staleOrLowerEndDoesNotMoveCandidateBackwards() {
        assertEquals(
            100L,
            UsageGuardUsageEndPolicy.monotonicEnd(existing = 100L, candidate = 50L),
        )
        assertEquals(
            150L,
            UsageGuardUsageEndPolicy.monotonicEnd(existing = 100L, candidate = 150L),
        )
        assertEquals(
            150L,
            UsageGuardUsageEndPolicy.monotonicEnd(existing = null, candidate = 150L),
        )
    }

    @Test
    fun anomalousActiveRecordForcesUnknownNewRequestGap() {
        assertEquals(
            null,
            UsageGuardUsageEndPolicy.newRequestGapMs(
                activeRecordPresent = true,
                previousEndAt = 1_000L,
                requestedAt = 2_000L,
            ),
        )
        assertEquals(
            1_000L,
            UsageGuardUsageEndPolicy.newRequestGapMs(
                activeRecordPresent = false,
                previousEndAt = 1_000L,
                requestedAt = 2_000L,
            ),
        )
    }
}
