package li.songe.gkd.sdp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionTest {
    @Test
    fun isValidAtUsesExclusiveEndTime() {
        val session = FocusSession(
            isActive = true,
            startTime = 1_000L,
            endTime = 2_000L
        )
        assertTrue(session.isValidAt(1_999L))
        assertFalse(session.isValidAt(2_000L))
    }
}
