package li.songe.gkd.sdp.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class MonotonicDeadlinePolicyTest {
    @Test
    fun `wall clock rollback does not extend an active deadline`() {
        val deadline = MonotonicDeadlinePolicy.deadlineFromWallClock(
            nowEpochMillis = 10_000L,
            nowElapsedMillis = 500L,
            wallDeadlineMillis = 20_000L,
        )

        assertEquals(10_500L, deadline)
        assertEquals(5_000L, MonotonicDeadlinePolicy.remainingMillis(5_500L, deadline))
        assertEquals(0L, MonotonicDeadlinePolicy.remainingMillis(10_500L, deadline))
        assertEquals(0L, MonotonicDeadlinePolicy.remainingMillis(11_000L, deadline))
    }

    @Test
    fun `a deadline already passed after restart expires immediately`() {
        assertEquals(
            5_000L,
            MonotonicDeadlinePolicy.deadlineFromWallClock(
                nowEpochMillis = 20_000L,
                nowElapsedMillis = 5_000L,
                wallDeadlineMillis = 10_000L,
            ),
        )
        assertEquals(0L, MonotonicDeadlinePolicy.remainingMillis(5_000L, 5_000L))
    }
}
