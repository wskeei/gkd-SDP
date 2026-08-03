package li.songe.gkd.sdp.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardBlockingOverlayStateTest {
    @Test
    fun matchingRequestStopClearsStateEvenWithoutRuntimeContext() {
        val state = UsageGuardBlockingOverlayState()
        state.markRequestStarted("com.example.video")

        assertTrue(state.clearRequest("com.example.video"))
        assertFalse(state.hasBlockingOverlay)
        assertNull(state.requestAppId)
    }

    @Test
    fun staleStopFromAnotherAppDoesNotClearCurrentRequest() {
        val state = UsageGuardBlockingOverlayState()
        state.markRequestStarted("com.example.reader")

        assertFalse(state.clearRequest("com.example.old"))
        assertEquals("com.example.reader", state.requestAppId)
    }

    @Test
    fun runtimeDisconnectClearsBlockingState() {
        val state = UsageGuardBlockingOverlayState()
        state.markTimeoutStarted("com.example.video")

        state.clearAll()

        assertFalse(state.hasBlockingOverlay)
        assertNull(state.requestAppId)
        assertNull(state.timeoutAppId)
    }

    @Test
    fun successfulGrantCanClearRequestBeforeServiceDestroyCallback() {
        val state = UsageGuardBlockingOverlayState()
        state.markRequestStarted("com.example.reader")

        assertTrue(state.clearRequest("com.example.reader"))
        assertFalse(state.hasBlockingOverlay)
    }

    @Test
    fun startingTimeoutReplacesAnyRequestState() {
        val state = UsageGuardBlockingOverlayState()
        state.markRequestStarted("com.example.reader")

        state.markTimeoutStarted("com.example.video")

        assertNull(state.requestAppId)
        assertEquals("com.example.video", state.timeoutAppId)
    }
}
