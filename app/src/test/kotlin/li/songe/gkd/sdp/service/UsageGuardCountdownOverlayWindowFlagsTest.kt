package li.songe.gkd.sdp.service

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageGuardCountdownOverlayWindowFlagsTest {
    @Test
    fun overlayIsSecureWithoutLosingExistingInteractionFlags() {
        val requiredFlags = listOf(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        )

        requiredFlags.forEach { flag ->
            assertEquals(flag, USAGE_GUARD_COUNTDOWN_OVERLAY_FLAGS and flag)
        }
    }
}
