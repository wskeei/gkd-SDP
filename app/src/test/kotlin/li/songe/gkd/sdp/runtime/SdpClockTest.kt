package li.songe.gkd.sdp.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdpClockTest {
    @Test
    fun `wall and monotonic time are independently injectable`() {
        val clock = FakeSdpClock(epochMillis = 1_000L, elapsedMillis = 40L)

        clock.advanceEpochMillis(-5_000L)
        clock.advanceElapsedRealtimeMillis(2_000L)

        assertEquals(-4_000L, clock.nowEpochMillis())
        assertEquals(2_040L, clock.elapsedRealtimeMillis())
        assertTrue(clock.elapsedRealtimeMillis() >= 0L)
    }
}
