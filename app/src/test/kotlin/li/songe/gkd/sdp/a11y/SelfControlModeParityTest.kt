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
            sdpRuntimeFeatureCoordinator.featureNames,
        )
    }

    @Test
    fun elapsedEventKeysRemainStableAcrossFeatureKinds() {
        assertEquals(
            "app-blocker:com.example.app",
            SelfControlElapsedPolicy.appBlockerEventKey("com.example.app"),
        )
        assertEquals(
            "selector-intercept:12:com.example.app:7",
            SelfControlElapsedPolicy.selectorInterceptEventKey(12L, "com.example.app", 7),
        )
        assertEquals(
            "url-intercept:9",
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
