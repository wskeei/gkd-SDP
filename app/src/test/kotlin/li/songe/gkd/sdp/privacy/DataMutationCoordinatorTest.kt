package li.songe.gkd.sdp.privacy

import li.songe.gkd.sdp.store.SettingsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DataMutationCoordinatorTest {
    @Test
    fun configAndDeleteAllAreBlockedOnlyDuringActiveSession() {
        assertTrue(DataMutationCoordinator.configurationDeletionAllowed(hasActiveSession = false))
        assertFalse(DataMutationCoordinator.configurationDeletionAllowed(hasActiveSession = true))
        assertTrue(DataMutationCoordinator.deleteAllAllowed(hasActiveSession = false))
        assertFalse(DataMutationCoordinator.deleteAllAllowed(hasActiveSession = true))
    }

    @Test
    fun deleteAllRequiresTheFixedPhrase() {
        assertTrue(DataMutationCoordinator.validatesDeleteAllPhrase("删除全部数据"))
        assertFalse(DataMutationCoordinator.validatesDeleteAllPhrase("删除"))
    }

    @Test
    fun selfControlResetResetsPolicyFieldsWithoutTouchingDisplayPreferences() {
        val current = SettingsStore(
            actionToast = "",
            customNotifTitle = "",
            updateChannel = 0,
            enableDarkTheme = false,
            displayDensityScale = 1.1f,
            languageTag = "zh-CN",
            usageGuardEnabled = true,
            usageGuardMinReasonLength = 20,
            accessibilityGuardEnabled = true,
        )

        val reset = DataMutationCoordinator.resetSelfControlConfig(current)

        assertFalse(reset.usageGuardEnabled)
        assertEquals(8, reset.usageGuardMinReasonLength)
        assertFalse(reset.accessibilityGuardEnabled)
        assertEquals(false, reset.enableDarkTheme)
        assertEquals(1.1f, reset.displayDensityScale)
        assertEquals("zh-CN", reset.languageTag)
    }

    @Test
    fun blockReasonIsPresentOnlyDuringActiveSession() {
        assertTrue(DataMutationCoordinator.blockReason(hasActiveSession = true) != null)
        assertEquals(null, DataMutationCoordinator.blockReason(hasActiveSession = false))
    }
}
