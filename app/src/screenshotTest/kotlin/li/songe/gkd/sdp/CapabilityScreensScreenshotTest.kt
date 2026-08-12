package li.songe.gkd.sdp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import li.songe.gkd.sdp.capability.CapabilityInput
import li.songe.gkd.sdp.capability.CapabilityResolver
import li.songe.gkd.sdp.ui.capability.CapabilityCenterContent

@PreviewTest
@Preview(name = "Capability center ready", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotCapabilityCenterReady() {
    MaterialTheme {
        CapabilityCenterContent(
            graph = CapabilityResolver.resolve(
                CapabilityInput(
                    chosenMode = li.songe.gkd.sdp.capability.RuntimeModeChoice.ACCESSIBILITY,
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
            ),
            onAction = {},
            onRefresh = {},
        )
    }
}

@PreviewTest
@Preview(name = "Capability center automation required", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotCapabilityCenterAutomationRequired() {
    MaterialTheme {
        CapabilityCenterContent(
            graph = CapabilityResolver.resolve(
                CapabilityInput(
                    chosenMode = li.songe.gkd.sdp.capability.RuntimeModeChoice.AUTOMATION,
                    a11yReady = false,
                    shizukuReady = false,
                    overlayReady = true,
                    notificationReady = true,
                    batteryExempted = false,
                    a11yGuardEnabled = false,
                    appListReady = true,
                    selfControlLocked = false,
                    isGkdFlavor = true,
                ),
            ),
            onAction = {},
            onRefresh = {},
        )
    }
}

@PreviewTest
@Preview(name = "Capability center accessibility action required", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotCapabilityCenterAccessibilityActionRequired() {
    MaterialTheme {
        CapabilityCenterContent(
            graph = CapabilityResolver.resolve(
                CapabilityInput(
                    chosenMode = li.songe.gkd.sdp.capability.RuntimeModeChoice.ACCESSIBILITY,
                    a11yReady = false,
                    shizukuReady = false,
                    overlayReady = false,
                    notificationReady = false,
                    batteryExempted = false,
                    a11yGuardEnabled = false,
                    appListReady = false,
                    selfControlLocked = false,
                    isGkdFlavor = true,
                ),
            ),
            onAction = {},
            onRefresh = {},
        )
    }
}
