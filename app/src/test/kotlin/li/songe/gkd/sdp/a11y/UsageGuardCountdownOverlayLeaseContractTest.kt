package li.songe.gkd.sdp.a11y

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayLeaseContractTest {
    @Test
    fun engineOwnsAndRevokesTheRestoreLease() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt",
        ).readText()
        val restoreCheck = source
            .substringAfter("fun canRestoreCountdownOverlay(")
            .substringBefore("fun onRequestOverlayStopped")
        val clearState = source
            .substringAfter("private fun clearCountdownOverlayState()")
            .substringBefore("private fun UsageGuardAppProfile.toSnapshot")

        assertTrue(source.contains("AtomicReference<UsageGuardCountdownOverlayLease?>"))
        assertTrue(source.contains("countdownOverlayLeaseSequence.incrementAndGet()"))
        assertTrue(source.contains("putExtra(UsageGuardCountdownOverlayService.EXTRA_OVERLAY_LEASE_ID"))
        assertTrue(source.contains("putExtra(UsageGuardCountdownOverlayService.EXTRA_RUNTIME_GENERATION"))
        assertTrue(restoreCheck.contains("UsageGuardCountdownOverlayCapturePolicy.isLeaseActive("))
        assertTrue(restoreCheck.contains("topActivityFlow.value.appId"))
        assertTrue(restoreCheck.contains("currentOwner()?.generation"))
        assertTrue(clearState.contains("countdownOverlayLease.set(null)"))
    }

    @Test
    fun staleServiceCallbacksCannotClearAReplacementLease() {
        val source = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt",
        ).readText()
        val stoppedCallback = source
            .substringAfter("fun onCountdownOverlayStopped(")
            .substringBefore("fun onRuntimeDisconnected")

        assertTrue(stoppedCallback.contains("leaseId"))
        assertTrue(stoppedCallback.contains("activeLease.leaseId == leaseId"))
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
