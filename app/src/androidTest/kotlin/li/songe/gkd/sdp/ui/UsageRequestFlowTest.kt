package li.songe.gkd.sdp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.usage.UsageRequestPresenter
import li.songe.gkd.sdp.usage.UsageRequestValidationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageRequestFlowTest {
    @Test
    fun validFormAcceptsAndInvalidFormFailsWithoutWriting() {
        val valid = UsageRequestValidationPolicy.validate(
            selectedTags = listOf("查资料"),
            reason = "准备今晚的演讲材料",
            minReasonLength = 6,
            requestedDurationMinutes = 15,
        )
        assertTrue(valid.accepted)

        val invalid = UsageRequestValidationPolicy.validate(
            selectedTags = emptyList(),
            reason = "短",
            minReasonLength = 6,
            requestedDurationMinutes = 0,
        )
        assertFalse(invalid.accepted)
    }

    @Test
    fun firstRequestDoesNotInventAnElapsedAnchor() {
        val state = UsageRequestPresenter.present(
            appId = "com.example.app",
            appName = "Example",
            data = null,
            nowEpochMs = 1_000L,
        )
        assertEquals(li.songe.gkd.sdp.usage.UsageRequestUiState.Status.UNAVAILABLE, state.status)
    }
}
