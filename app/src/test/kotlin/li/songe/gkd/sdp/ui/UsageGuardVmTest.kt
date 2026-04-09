package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import li.songe.gkd.sdp.util.UsageGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardVmTest {
    @Test
    fun settingsStoreDefaultsIncludeFourDurationOptions() {
        assertEquals(
            UsageGuardUiStatePolicy.defaultDurationOptions,
            SettingsStore(
                actionToast = "",
                customNotifTitle = "",
                updateChannel = 0,
            ).usageGuardDurationOptionsMinutes,
        )
    }

    @Test
    fun activeRecordCloseReasonsStayStable() {
        assertEquals(UsageGuardPolicy.GRANT_MODE_STRICT, 0)
        assertEquals(UsageGuardRecord.END_REASON_EXPIRED, 1)
        assertEquals(UsageGuardRecord.END_REASON_LEFT_APP, 2)
        assertEquals(UsageGuardRecord.END_REASON_HOME_BUTTON, 4)
    }

    @Test
    fun overrideOnlyProfileIsRetainedWhenGrantModeDiffersFromDefault() {
        val profile = UsageGuardAppProfile(
            appId = "com.example.video",
            selectedTarget = false,
            globalWhitelist = false,
            grantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
        )

        assertTrue(
            UsageGuardVm.shouldRetainProfile(
                profile = profile,
                defaultGrantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
            )
        )
    }

    @Test
    fun overrideOnlyProfileMatchingDefaultCanBeDropped() {
        val profile = UsageGuardAppProfile(
            appId = "com.example.video",
            selectedTarget = false,
            globalWhitelist = false,
            grantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
        )

        assertFalse(
            UsageGuardVm.shouldRetainProfile(
                profile = profile,
                defaultGrantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
            )
        )
    }

    @Test
    fun durationOptionsAlwaysNormalizeToFourPositiveValues() {
        val normalized = UsageGuardUiStatePolicy.normalizeDurationOptions(listOf(5, 0, -1, 25))

        assertEquals(listOf(5, 25, 10, 15), normalized)
    }

    @Test
    fun oppositeGrantModeStillFlipsStrictAndResumable() {
        assertEquals(
            UsageGuardPolicy.GRANT_MODE_STRICT,
            UsageGuardVm.oppositeGrantMode(UsageGuardPolicy.GRANT_MODE_RESUMABLE),
        )
        assertEquals(
            UsageGuardPolicy.GRANT_MODE_RESUMABLE,
            UsageGuardVm.oppositeGrantMode(UsageGuardPolicy.GRANT_MODE_STRICT),
        )
    }
}
