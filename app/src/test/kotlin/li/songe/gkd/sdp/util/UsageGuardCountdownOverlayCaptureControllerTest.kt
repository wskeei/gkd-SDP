package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayCaptureControllerTest {
    @Test
    fun validSessionWithoutViewRequestsCreateAndMount() {
        val controller = UsageGuardCountdownOverlayCaptureController()

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.CREATE_AND_MOUNT,
            controller.onStart(session(), hasView = false),
        )
        assertTrue(controller.onMountSucceeded())
        assertTrue(controller.isMounted)
        assertFalse(controller.isTerminal)
    }

    @Test
    fun unchangedSessionKeepsMountedOrHiddenState() {
        val controller = UsageGuardCountdownOverlayCaptureController()
        val session = session()

        controller.onStart(session, hasView = false)
        controller.onMountSucceeded()
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.KEEP_MOUNTED,
            controller.onStart(session, hasView = true),
        )

        val hidden = controller.snapshotForHide()
        assertEquals(session, hidden)
        assertTrue(controller.onHideResult(session, removed = true))
        assertFalse(controller.isMounted)
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.KEEP_HIDDEN,
            controller.onStart(session, hasView = true),
        )
    }

    @Test
    fun replacementSessionRequestsResetAndMount() {
        val controller = mountedController(session())

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.StartAction.RESET_AND_MOUNT,
            controller.onStart(session(recordId = 8L), hasView = true),
        )
    }

    @Test
    fun mountFailureMakesControllerTerminal() {
        val controller = UsageGuardCountdownOverlayCaptureController()

        controller.onStart(session(), hasView = false)
        controller.onMountFailed()

        assertFalse(controller.isMounted)
        assertTrue(controller.isTerminal)
        assertFalse(controller.onMountSucceeded())
        assertNull(controller.snapshotForHide())
    }

    @Test
    fun hideRequiresSuccessfulRemovalOfMountedWindow() {
        val controller = mountedController(session())
        val session = controller.snapshotForHide()

        assertNotNull(session)
        assertFalse(controller.onHideResult(session!!, removed = false))
        assertTrue(controller.isMounted)
        assertEquals(session, controller.snapshotForHide())
    }

    @Test
    fun restoreRequiresSameCurrentUnexpiredSessionAndActiveLease() {
        val controller = mountedController(session())
        val hidden = controller.snapshotForHide()!!
        controller.onHideResult(hidden, removed = true)

        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.MOUNT,
            controller.restoreAction(hidden, now = 20_000L, leaseActive = true),
        )

        val expiredController = mountedController(session(expiresAt = 20_000L))
        val expired = expiredController.snapshotForHide()!!
        expiredController.onHideResult(expired, removed = true)
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.STOP_EXPIRED,
            expiredController.restoreAction(
                expired,
                now = 20_000L,
                leaseActive = true,
            ),
        )
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.STOP_REVOKED,
            controller.restoreAction(hidden, now = 20_000L, leaseActive = false),
        )
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.IGNORE,
            controller.restoreAction(
                hidden.copy(recordId = hidden.recordId + 1L),
                now = 20_000L,
                leaseActive = true,
            ),
        )
    }

    @Test
    fun destructionPreventsLaterRestore() {
        val controller = mountedController(session())
        val hidden = controller.snapshotForHide()!!
        controller.onHideResult(hidden, removed = true)
        controller.onDestroy()

        assertTrue(controller.isTerminal)
        assertEquals(
            UsageGuardCountdownOverlayCaptureController.RestoreAction.IGNORE,
            controller.restoreAction(hidden, now = 20_000L, leaseActive = true),
        )
    }

    private fun mountedController(session: UsageGuardCountdownOverlaySession): UsageGuardCountdownOverlayCaptureController {
        return UsageGuardCountdownOverlayCaptureController().also {
            it.onStart(session, hasView = false)
            it.onMountSucceeded()
        }
    }

    private fun session(
        appId: String = "com.example.target",
        recordId: Long = 7L,
        expiresAt: Long = 20_001L,
        leaseId: Long = 11L,
        runtimeGeneration: Long = 5L,
    ) = UsageGuardCountdownOverlaySession(
        appId = appId,
        recordId = recordId,
        expiresAt = expiresAt,
        leaseId = leaseId,
        runtimeGeneration = runtimeGeneration,
    )
}
