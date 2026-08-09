package li.songe.gkd.sdp.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayScreenshotModeContractTest {
    @Test
    fun screenshotModeUnmountsThenRestoresOnlyTheCurrentSecureOverlay() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt",
        ).readText()
        val hideMethod = source
            .substringAfter("private fun hideOverlayForScreenshot()")
            .substringBefore("private fun restoreOverlayAfterScreenshot")
        val restoreMethod = source
            .substringAfter("private fun restoreOverlayAfterScreenshot")
            .substringBefore("private fun showTerminateConfirmScreen")

        assertTrue(source.contains("private var overlayMounted = false"))
        assertTrue(source.contains("private var restoreOverlayJob: Job? = null"))
        assertTrue(hideMethod.contains("windowManager.removeView(overlayView)"))
        assertTrue(
            hideMethod.contains(
                "delay(UsageGuardCountdownOverlayCapturePolicy.HIDE_DURATION_MS)",
            ),
        )
        assertTrue(
            hideMethod.contains(
                "restoreOverlayAfterScreenshot(hiddenAppId, hiddenRecordId)",
            ),
        )
        assertTrue(
            restoreMethod.contains(
                "UsageGuardCountdownOverlayCapturePolicy.shouldRestore(",
            ),
        )
        assertTrue(restoreMethod.contains("mountOverlayView(overlayView, params)"))
        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_SECURE"))
    }

    @Test
    fun usageControlExposesAnAccessibleScreenshotAction() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt",
        ).readText()
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
        var directory = File(System.getProperty("user.dir"))
        repeat(8) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return File(relativePath)
        }
        return File(relativePath)
    }
}
