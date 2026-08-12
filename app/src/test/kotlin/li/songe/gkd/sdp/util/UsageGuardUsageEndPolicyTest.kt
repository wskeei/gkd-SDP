package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
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

    @Test
    fun negativeEndCandidatesNeverMoveStateBackwards() {
        assertEquals(0L, UsageGuardUsageEndPolicy.monotonicEnd(existing = null, candidate = -1L))
        assertEquals(100L, UsageGuardUsageEndPolicy.monotonicEnd(existing = 100L, candidate = -1L))
        assertEquals(null, UsageGuardUsageEndPolicy.newRequestGapMs(false, null, 2_000L))
        assertEquals(null, UsageGuardUsageEndPolicy.newRequestGapMs(false, -1L, 2_000L))
        assertEquals(null, UsageGuardUsageEndPolicy.newRequestGapMs(false, 3_000L, 2_000L))
    }

    @Test
    fun shouldMarkUsageEndedOnlyForOpenRecords() {
        val open = UsageGuardRecord(
            id = 1,
            appId = "app",
            appName = "应用",
            tagNames = emptyList(),
            reasonText = "synthetic",
            requestedDurationMinutes = 5,
            requestedAt = 1_000L,
            grantedAt = 1_000L,
            expiresAt = 2_000L,
        )

        assertTrue(UsageGuardUsageEndPolicy.shouldMarkUsageEnded(open))
        assertFalse(UsageGuardUsageEndPolicy.shouldMarkUsageEnded(open.copy(endedAt = 1_500L)))
    }
}
