package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlRuntimeReadinessTest {
    @Test
    fun connectedRuntimeWithOverlayPermissionIsReady() {
        val status = SelfControlRuntimeReadiness.evaluate(
            mode = AutomatorModeOption.AutomationMode,
            connected = true,
            switching = false,
            overlayPermission = true,
        )

        assertTrue(status.ready)
        assertEquals(SelfControlRuntimeReadiness.Issue.None, status.issue)
        assertEquals(AutomatorModeOption.AutomationMode.labelRes, status.modeLabelRes)
    }

    @Test
    fun missingOverlayPermissionIsActionable() {
        val status = SelfControlRuntimeReadiness.evaluate(
            mode = AutomatorModeOption.A11yMode,
            connected = true,
            switching = false,
            overlayPermission = false,
        )

        assertFalse(status.ready)
        assertEquals(SelfControlRuntimeReadiness.Issue.OverlayPermissionMissing, status.issue)
    }

    @Test
    fun handoffIsReportedBeforeDisconnected() {
        val status = SelfControlRuntimeReadiness.evaluate(
            mode = AutomatorModeOption.AutomationMode,
            connected = false,
            switching = true,
            overlayPermission = true,
        )

        assertFalse(status.ready)
        assertEquals(SelfControlRuntimeReadiness.Issue.Switching, status.issue)
    }

    @Test
    fun disconnectedRuntimeIsDistinctFromPermissionFailure() {
        val status = SelfControlRuntimeReadiness.evaluate(
            mode = null,
            connected = false,
            switching = false,
            overlayPermission = true,
        )

        assertEquals(SelfControlRuntimeReadiness.Issue.RuntimeUnavailable, status.issue)
    }
}
