package li.songe.gkd.sdp.a11y

import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCaptureController
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCapturePolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayLeaseContractTest {
    @Test
    fun matchingLeaseForegroundAndRuntimeAllowRestore() {
        val session = session()

        assertTrue(
            UsageGuardCountdownOverlayCapturePolicy.isLeaseActive(
                lease = session.toLease(),
                session = session,
                foregroundAppId = session.appId,
                currentRuntimeGeneration = session.runtimeGeneration,
            ),
        )
    }

    @Test
    fun staleServiceCallbacksCannotClearAReplacementLease() {
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
                now = hidden.expiresAt - 1L,
                leaseActive = true,
            ),
        )
        assertFalse(controller.isMounted)
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
