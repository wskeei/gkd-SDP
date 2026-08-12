package li.songe.gkd.sdp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardPolicyTest {
    @Test
    fun selectedScopeOnlyProtectsSelectedApps() {
        val selectedProfile = UsageGuardPolicy.AppProfileSnapshot(
            appId = "com.example.chat",
            selectedTarget = true,
            globalWhitelist = false,
            grantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
        )

        assertTrue(
            UsageGuardPolicy.shouldProtectApp(
                enabled = true,
                scopeMode = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                appProfile = selectedProfile,
            )
        )
        assertFalse(
            UsageGuardPolicy.shouldProtectApp(
                enabled = true,
                scopeMode = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                appProfile = null,
            )
        )
    }

    @Test
    fun globalScopeProtectsNonWhitelistedApps() {
        val whitelistedProfile = UsageGuardPolicy.AppProfileSnapshot(
            appId = "com.example.bank",
            selectedTarget = false,
            globalWhitelist = true,
            grantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
        )

        assertFalse(
            UsageGuardPolicy.shouldProtectApp(
                enabled = true,
                scopeMode = UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
                appProfile = whitelistedProfile,
            )
        )
        assertTrue(
            UsageGuardPolicy.shouldProtectApp(
                enabled = true,
                scopeMode = UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
                appProfile = null,
            )
        )
    }

    @Test
    fun requestValidationRequiresTagReasonAndPositiveDuration() {
        val invalid = UsageGuardPolicy.validateRequest(
            selectedTags = emptyList(),
            reason = "太短",
            minReasonLength = 6,
            requestedDurationMinutes = 0,
        )

        assertFalse(invalid.accepted)

        val valid = UsageGuardPolicy.validateRequest(
            selectedTags = listOf("查资料"),
            reason = "查资料准备今晚的演讲",
            minReasonLength = 6,
            requestedDurationMinutes = 15,
        )

        assertTrue(valid.accepted)
    }

    @Test
    fun disabledUnknownScopeAndUnselectedTargetsAreNotProtected() {
        val profile = UsageGuardPolicy.AppProfileSnapshot(
            appId = "app",
            selectedTarget = false,
            globalWhitelist = false,
            grantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
        )

        assertFalse(
            UsageGuardPolicy.shouldProtectApp(
                enabled = false,
                scopeMode = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                appProfile = profile,
            ),
        )
        assertFalse(
            UsageGuardPolicy.shouldProtectApp(
                enabled = true,
                scopeMode = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                appProfile = profile,
            ),
        )
        assertFalse(
            UsageGuardPolicy.shouldProtectApp(
                enabled = true,
                scopeMode = 99,
                appProfile = profile,
            ),
        )
    }
}
