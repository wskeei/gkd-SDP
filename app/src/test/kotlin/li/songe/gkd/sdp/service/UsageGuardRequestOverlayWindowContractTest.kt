package li.songe.gkd.sdp.service

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageGuardRequestOverlayWindowContractTest {
    @Test
    fun requestOverlayIsSecureFocusableAndResizableForIme() {
        val requiredFlags = listOf(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        requiredFlags.forEach { flag ->
            assertEquals(flag, USAGE_GUARD_REQUEST_OVERLAY_FLAGS and flag)
        }

        val forbiddenFlags = listOf(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        )
        forbiddenFlags.forEach { flag ->
            assertEquals(0, USAGE_GUARD_REQUEST_OVERLAY_FLAGS and flag)
        }

        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE,
        )
    }
}
