package li.songe.gkd.sdp.a11y

import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.UsageGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlModeParityTest {
    @Test
    fun productionCoordinatorRegistersEverySelfControlFeature() {
        assertEquals(
            setOf("focus", "usage-guard", "app-blocker", "url-blocker"),
            selfControlRuntimeFeatureNames,
        )
    }

    @Test
    fun elapsedEventKeysRemainStableAcrossFeatureKinds() {
        assertEquals(
            "app_blocker:com.example.app",
            SelfControlElapsedPolicy.appBlockerEventKey("com.example.app"),
        )
        assertEquals(
            "selector_intercept:v2:12:com.example.app:2:7:key:9",
            SelfControlElapsedPolicy.selectorInterceptEventKey(
                subsId = 12L,
                appId = "com.example.app",
                groupType = 2,
                groupKey = 7,
                ruleIdentity = "key:9",
            ),
        )
        assertEquals(
            "url_intercept:9",
            SelfControlElapsedPolicy.urlInterceptEventKey(9L),
        )
    }

    @Test
    fun cancellingUsageRequestDoesNotBecomeAnAcceptedRecord() {
        val invalid = UsageGuardPolicy.validateRequest(
            selectedTags = listOf("查资料"),
            reason = "",
            minReasonLength = 6,
            requestedDurationMinutes = 15,
        )

        assertTrue(!invalid.accepted)
    }
}
