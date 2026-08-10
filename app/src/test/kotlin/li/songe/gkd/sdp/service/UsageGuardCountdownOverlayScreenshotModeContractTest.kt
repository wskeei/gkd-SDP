package li.songe.gkd.sdp.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayScreenshotModeContractTest {
    @Test
    fun screenshotModeUnmountsThenRestoresOnlyTheCurrentSecureOverlay() {
        val source = overlaySource()
        val hideMethod = source
            .substringAfter("private fun hideOverlayForScreenshot()")
            .substringBefore("private fun restoreOverlayAfterScreenshot")
        val restoreMethod = source
            .substringAfter("private fun restoreOverlayAfterScreenshot")
            .substringBefore("private fun showTerminateConfirmScreen")

        assertTrue(
            source.contains(
                "private val captureController = UsageGuardCountdownOverlayCaptureController()",
            ),
        )
        assertTrue(source.contains("private var restoreOverlayJob: Job? = null"))
        assertTrue(source.contains("EXTRA_OVERLAY_LEASE_ID"))
        assertTrue(source.contains("EXTRA_RUNTIME_GENERATION"))
        assertTrue(hideMethod.contains("windowManager.removeView(overlayView)"))
        assertTrue(
            hideMethod.contains(
                "delay(UsageGuardCountdownOverlayCapturePolicy.HIDE_DURATION_MS)",
            ),
        )
        assertTrue(
            hideMethod.contains(
                "restoreOverlayAfterScreenshot(hidden)",
            ),
        )
        assertTrue(
            restoreMethod.contains(
                "UsageGuardEngine.canRestoreCountdownOverlay(",
            ),
        )
        assertTrue(restoreMethod.contains("RestoreAction.MOUNT -> showOverlay()"))
        assertTrue(hideMethod.contains("view = null"))
        assertTrue(hideMethod.contains("preservedPosition = params.x to params.y"))
        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_SECURE"))
        assertTrue(source.contains("captureController.onMountFailed()"))
        assertTrue(source.contains("view = null"))
        assertTrue(source.contains("layoutParams = null"))
    }

    @Test
    fun terminalServiceRejectsAndRevokesTheIncomingReplacementLease() {
        val source = overlaySource()
        val terminalBranch = source
            .substringAfter(
                "UsageGuardCountdownOverlayCaptureController.StartAction.IGNORE_TERMINAL",
            )
            .substringBefore("\n        appId = incomingAppId")

        assertTrue(terminalBranch.contains("UsageGuardEngine.onOverlayMountFailed("))
        assertTrue(terminalBranch.contains("appId = incomingAppId"))
        assertTrue(terminalBranch.contains("countdownLeaseId = incomingOverlayLeaseId"))
        assertTrue(terminalBranch.contains("stopSelf()"))
    }

    @Test
    fun usageControlExposesAnAccessibleScreenshotAction() {
        val source = overlaySource()
        val control = source.substringAfter("private fun UsageGuardTerminateConfirmScreen(")

        assertTrue(source.contains("onHideForScreenshot = { hideOverlayForScreenshot() }"))
        assertTrue(control.contains("onHideForScreenshot: () -> Unit"))
        assertTrue(control.contains("OutlinedButton("))
        assertTrue(control.contains("onClick = onHideForScreenshot"))
        assertTrue(control.contains(".heightIn(min = 48.dp)"))
        assertTrue(control.contains("Text(\"隐藏 10 秒用于截图\")"))
        assertTrue(control.contains("text = \"隐藏期间倒计时继续，之后自动恢复。\""))
        assertTrue(control.contains("style = MaterialTheme.typography.bodySmall"))
    }

    private fun sourceFile(relativePath: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        var directory = File(userDir).absoluteFile
        while (!File(directory, "settings.gradle.kts").isFile || !File(directory, "app/src").isDirectory) {
            directory = directory.parentFile ?: error("Repository root marker not found from $userDir")
        }
        return File(directory, relativePath).also { check(it.isFile) { "Missing source: $relativePath" } }
    }

    private fun overlaySource(): String = listOf(
        "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardcountdown/ServiceHost.kt",
        "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardcountdown/Screen.kt",
        "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardcountdown/UiState.kt",
        "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardcountdown/Presenter.kt",
        "app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardcountdown/WindowController.kt",
    ).joinToString("\n") { sourceFile(it).readText() }
}
