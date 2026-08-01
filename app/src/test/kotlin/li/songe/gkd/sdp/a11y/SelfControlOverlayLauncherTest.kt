package li.songe.gkd.sdp.a11y

import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlOverlayLauncherTest {
    @Test
    fun missingOverlayPermissionReturnsMissingPermissionWithoutStartingService() {
        var starts = 0
        val launcher = SelfControlOverlayLauncher(
            appContext = null,
            canDrawOverlays = { false },
            startService = { starts++ },
        )

        assertEquals(
            OverlayLaunchResult.MissingPermission,
            launcher.launch(Intent("test")),
        )
        assertEquals(0, starts)
    }

    @Test
    fun acceptedStartReturnsAccepted() {
        var starts = 0
        val launcher = SelfControlOverlayLauncher(
            appContext = null,
            canDrawOverlays = { true },
            startService = { starts++ },
        )

        assertEquals(OverlayLaunchResult.Accepted, launcher.launch(Intent("test")))
        assertEquals(1, starts)
    }

    @Test
    fun backgroundStartRejectionIsClassifiedAndDoesNotThrow() {
        val launcher = SelfControlOverlayLauncher(
            appContext = null,
            canDrawOverlays = { true },
            startService = { throw IllegalStateException("background start not allowed") },
        )

        val result = launcher.launch(Intent("test"))

        assertEquals(
            OverlayLaunchResult.Rejected(OverlayFailureCategory.BACKGROUND_START),
            result,
        )
    }

    @Test
    fun missingRuntimeIsReportedForNodeDependentUrlEvaluation() {
        val launcher = SelfControlOverlayLauncher(
            appContext = null,
            canDrawOverlays = { true },
            startService = {},
        )

        assertEquals(
            OverlayLaunchResult.RuntimeUnavailable,
            launcher.launch(Intent("test"), runtimeAvailable = false),
        )
    }
}
