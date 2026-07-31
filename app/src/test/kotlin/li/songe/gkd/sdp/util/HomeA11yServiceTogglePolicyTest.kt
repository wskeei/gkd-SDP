package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeA11yServiceTogglePolicyTest {
    @Test
    fun turningTheSwitchOffAlwaysExplainsSystemSettings() {
        assertEquals(
            HomeA11yServiceTogglePolicy.Action.EXPLAIN_SYSTEM_SETTINGS,
            HomeA11yServiceTogglePolicy.action(
                requestedEnabled = false,
                writeSecureSettings = false,
            ),
        )
        assertEquals(
            HomeA11yServiceTogglePolicy.Action.EXPLAIN_SYSTEM_SETTINGS,
            HomeA11yServiceTogglePolicy.action(
                requestedEnabled = false,
                writeSecureSettings = true,
            ),
        )
    }

    @Test
    fun turningOnWithoutSecureSettingsPermissionOpensAuthorization() {
        assertEquals(
            HomeA11yServiceTogglePolicy.Action.OPEN_AUTHORIZATION,
            HomeA11yServiceTogglePolicy.action(
                requestedEnabled = true,
                writeSecureSettings = false,
            ),
        )
    }

    @Test
    fun turningOnWithSecureSettingsPermissionIssuesStartOrRepair() {
        assertEquals(
            HomeA11yServiceTogglePolicy.Action.START_OR_REPAIR,
            HomeA11yServiceTogglePolicy.action(
                requestedEnabled = true,
                writeSecureSettings = true,
            ),
        )
    }
}
