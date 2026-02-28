package li.songe.gkd.sdp.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStatusTogglePolicyTest {
    @Test
    fun runningServiceCannotBeDisabledFromUi() {
        assertFalse(
            canToggleServiceStatusFromUi(
                currentEnabled = true,
                requestedEnabled = false
            )
        )
    }

    @Test
    fun stoppedServiceCanBeEnabledFromUi() {
        assertTrue(
            canToggleServiceStatusFromUi(
                currentEnabled = false,
                requestedEnabled = true
            )
        )
    }

    @Test
    fun keepingServiceEnabledIsAllowed() {
        assertTrue(
            canToggleServiceStatusFromUi(
                currentEnabled = true,
                requestedEnabled = true
            )
        )
    }
}
