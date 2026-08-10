package li.songe.gkd.sdp.service

import android.view.WindowManager
import li.songe.gkd.sdp.usage.UsageRequestValidationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardRequestLayoutContractTest {
    @Test
    fun requestOverlayIsSecureResizableAndUsesTheSharedFormPolicy() {
        assertEquals(
            WindowManager.LayoutParams.FLAG_SECURE,
            USAGE_GUARD_REQUEST_OVERLAY_FLAGS and WindowManager.LayoutParams.FLAG_SECURE,
        )
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE,
        )

        val valid = UsageRequestValidationPolicy.validate(
            selectedTags = listOf("查资料"),
            reason = "准备今晚的演讲材料",
            minReasonLength = 6,
            requestedDurationMinutes = 15,
        )
        assertTrue(valid.accepted)
        assertFalse(
            UsageRequestValidationPolicy.validate(
                selectedTags = emptyList(),
                reason = "准备今晚的演讲材料",
                minReasonLength = 6,
                requestedDurationMinutes = 15,
            ).accepted,
        )
    }

    @Test
    fun formPolicyKeepsTheOtherTagLastAndRejectsDuplicateNames() {
        assertFalse(
            UsageRequestValidationPolicy.hasDuplicateTag(
                existing = listOf("工作"),
                candidate = "work",
            ),
        )
        assertTrue(
            UsageRequestValidationPolicy.hasDuplicateTag(
                existing = listOf("工作"),
                candidate = "工作",
            ),
        )
    }
}
