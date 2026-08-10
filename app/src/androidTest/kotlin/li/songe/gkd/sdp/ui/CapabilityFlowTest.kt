package li.songe.gkd.sdp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.capability.CapabilityInput
import li.songe.gkd.sdp.capability.CapabilityResolver
import li.songe.gkd.sdp.capability.CapabilityStatus
import li.songe.gkd.sdp.capability.RuntimeModeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapabilityFlowTest {
    @Test
    fun readyAccessibilityFlowHasNoNextStep() {
        val graph = CapabilityResolver.resolve(
            CapabilityInput(
                chosenMode = RuntimeModeChoice.ACCESSIBILITY,
                a11yReady = true,
                shizukuReady = false,
                overlayReady = true,
                notificationReady = true,
                batteryExempted = true,
                a11yGuardEnabled = true,
                appListReady = true,
                selfControlLocked = false,
                isGkdFlavor = true,
            ),
        )

        assertNull(graph.nextStep)
        assertEquals(CapabilityStatus.ACTIVE, graph.nodes.first { it.id.name == "RUNTIME_MODE" }.status)
    }
}
