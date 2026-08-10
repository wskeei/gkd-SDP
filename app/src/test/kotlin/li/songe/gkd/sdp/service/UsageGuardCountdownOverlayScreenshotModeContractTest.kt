package li.songe.gkd.sdp.service

import android.view.WindowManager
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCaptureController
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCapturePolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayScreenshotModeContractTest {
    @Test
    fun screenshotModeUnmountsThenRestoresOnlyTheCurrentSecureOverlay() {
        val controller = mountedController()
        val hidden = controller.snapshotForHide()!!

        assertTrue(controller.onHideResult(hidden, removed = true))
        assertFalse(controller.isMounted)
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.MOUNT,
            controller.restoreAction(
                hidden = hidden,
                now = hidden.expiresAt - 1L,
                leaseActive = true,
            ),
        )
        assertEquals(
            UsageGuardCountdownOverlayCapturePolicy.HIDE_DURATION_MS,
            10_000L,
        )
    }

    @Test
    fun terminalServiceRejectsAndRevokesTheIncomingReplacementLease() {
        val controller = UsageGuardCountdownOverlayCaptureController()
        val session = session()
        controller.onStart(session, hasView = false)
        controller.onMountFailed()

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.IGNORE_TERMINAL,
            controller.onStart(session, hasView = false),
        )
        assertFalse(controller.isMounted)
        assertTrue(controller.isTerminal)
    }

    @Test
    fun secureWindowFlagRemainsEnabledForTheCountdownOverlay() {
        assertEquals(
            WindowManager.LayoutParams.FLAG_SECURE,
            USAGE_GUARD_COUNTDOWN_OVERLAY_FLAGS and WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    private fun mountedController(): UsageGuardCountdownOverlayCaptureController {
        return UsageGuardCountdownOverlayCaptureController().apply {
            onStart(session(), hasView = false)
            onMountSucceeded()
        }
    }

    private fun session() = UsageGuardCountdownOverlaySession(
        appId = "com.example.target",
        recordId = 7L,
        expiresAt = 20_000L,
        leaseId = 11L,
        runtimeGeneration = 5L,
    )
}
