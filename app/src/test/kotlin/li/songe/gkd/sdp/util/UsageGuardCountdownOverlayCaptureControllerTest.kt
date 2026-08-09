package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayCaptureControllerTest {
    @Test
    fun removalFailureKeepsTheSecureOverlayMounted() {
        val controller = UsageGuardCountdownOverlayCaptureController()
        val session = session()

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.CREATE_AND_MOUNT,
            controller.onStart(session, hasView = false),
        )
        assertTrue(controller.onMountSucceeded())

        val hidden = controller.snapshotForHide()
        assertEquals(session, hidden)
        assertFalse(controller.onHideResult(hidden!!, removed = false))
        assertTrue(controller.isMounted)
    }

    @Test
    fun sameUnexpiredLeaseRestoresAfterSuccessfulRemoval() {
        val controller = mountedController()
        val hidden = controller.snapshotForHide()!!

        assertTrue(controller.onHideResult(hidden, removed = true))
        assertFalse(controller.isMounted)
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.MOUNT,
            controller.restoreAction(
                hidden = hidden,
                now = 19_999L,
                leaseActive = true,
            ),
        )
    }

    @Test
    fun revokedLeaseNeverRestores() {
        val controller = mountedController()
        val hidden = controller.snapshotForHide()!!
        assertTrue(controller.onHideResult(hidden, removed = true))

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.STOP_REVOKED,
            controller.restoreAction(
                hidden = hidden,
                now = 19_999L,
                leaseActive = false,
            ),
        )
        assertFalse(controller.isMounted)
    }

    @Test
    fun expiredCurrentLeaseStopsInsteadOfRestoring() {
        val controller = mountedController()
        val hidden = controller.snapshotForHide()!!
        assertTrue(controller.onHideResult(hidden, removed = true))

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.STOP_EXPIRED,
            controller.restoreAction(
                hidden = hidden,
                now = hidden.expiresAt,
                leaseActive = true,
            ),
        )
    }

    @Test
    fun replacementLeaseMountsImmediatelyAndInvalidatesOldRestore() {
        val controller = mountedController()
        val hidden = controller.snapshotForHide()!!
        assertTrue(controller.onHideResult(hidden, removed = true))
        val replacement = session(leaseId = hidden.leaseId + 1L)

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.RESET_AND_MOUNT,
            controller.onStart(replacement, hasView = true),
        )
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.IGNORE,
            controller.restoreAction(
                hidden = hidden,
                now = 19_999L,
                leaseActive = true,
            ),
        )
    }

    @Test
    fun duplicateStartDuringHideKeepsTheOriginalRestoreWindow() {
        val controller = mountedController()
        val hidden = controller.snapshotForHide()!!
        assertTrue(controller.onHideResult(hidden, removed = true))

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.KEEP_HIDDEN,
            controller.onStart(hidden, hasView = true),
        )
    }

    @Test
    fun mountFailureIsTerminalForTheCurrentServiceInstance() {
        val controller = UsageGuardCountdownOverlayCaptureController()
        val session = session()
        controller.onStart(session, hasView = false)

        controller.onMountFailed()

        assertTrue(controller.isTerminal)
        assertFalse(controller.isMounted)
        assertNull(controller.snapshotForHide())
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.IGNORE_TERMINAL,
            controller.onStart(session, hasView = false),
        )
    }

    @Test
    fun destroyedControllerCannotRestoreOrRestart() {
        val controller = mountedController()
        val hidden = controller.snapshotForHide()!!
        assertTrue(controller.onHideResult(hidden, removed = true))

        controller.onDestroy()

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.IGNORE,
            controller.restoreAction(hidden, now = 19_999L, leaseActive = true),
        )
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.IGNORE_TERMINAL,
            controller.onStart(hidden, hasView = true),
        )
    }

    private fun mountedController(): UsageGuardCountdownOverlayCaptureController {
        return UsageGuardCountdownOverlayCaptureController().apply {
            onStart(session(), hasView = false)
            onMountSucceeded()
        }
    }

    private fun session(
        leaseId: Long = 11L,
        runtimeGeneration: Long = 5L,
    ) = UsageGuardCountdownOverlaySession(
        appId = "com.example.target",
        recordId = 7L,
        expiresAt = 20_000L,
        leaseId = leaseId,
        runtimeGeneration = runtimeGeneration,
    )
}
