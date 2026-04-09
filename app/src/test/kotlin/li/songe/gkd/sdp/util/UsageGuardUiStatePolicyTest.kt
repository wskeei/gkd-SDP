package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardAppProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageGuardUiStatePolicyTest {
    @Test
    fun protectionStatusAutoReenableMessageStatesOnlyMainSwitchRecovery() {
        assertEquals(
            "自动重开保护会恢复使用申请总开关，不会改动范围、默认授权模式或时长选项。",
            UsageGuardUiStatePolicy.protectionStatusAutoReenableMessage(),
        )
    }

    @Test
    fun normalizeDurationOptionsPadsAndCleansInvalidValues() {
        val normalized = UsageGuardUiStatePolicy.normalizeDurationOptions(
            raw = listOf(0, 25, -3),
        )

        assertEquals(listOf(25, 10, 15, 30), normalized)
    }

    @Test
    fun groupSelectedAppsSplitsStrictAndResumableUsingDefaultGrantMode() {
        val grouped = UsageGuardUiStatePolicy.groupSelectedApps(
            profiles = listOf(
                UsageGuardAppProfile(
                    appId = "strict.app",
                    selectedTarget = true,
                    grantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
                ),
                UsageGuardAppProfile(
                    appId = "default.app",
                    selectedTarget = true,
                    grantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                ),
                UsageGuardAppProfile(
                    appId = "ignored.app",
                    selectedTarget = false,
                    grantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
                ),
            ),
            defaultGrantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
        )

        assertEquals(listOf("strict.app"), grouped.strictAppIds)
        assertEquals(listOf("default.app"), grouped.resumableAppIds)
    }
}
