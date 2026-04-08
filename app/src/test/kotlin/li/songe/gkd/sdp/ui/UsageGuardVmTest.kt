package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.util.UsageGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardVmTest {
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
}
