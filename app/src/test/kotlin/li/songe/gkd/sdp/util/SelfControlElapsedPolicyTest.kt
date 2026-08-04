package li.songe.gkd.sdp.util

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlElapsedPolicyTest {
    @Test
    fun elapsedTextFormatsSecondsMinutesHoursAndDays() {
        assertEquals(
            "00:00:00",
            SelfControlElapsedPolicy.formatElapsed(anchorAtEpochMs = 0L, nowEpochMs = 0L),
        )
        assertEquals(
            "00:01:05",
            SelfControlElapsedPolicy.formatElapsed(
                anchorAtEpochMs = 0L,
                nowEpochMs = 65_000L,
            ),
        )
        assertEquals(
            "03:04:05",
            SelfControlElapsedPolicy.formatElapsed(
                anchorAtEpochMs = 0L,
                nowEpochMs = (3 * 60 * 60 + 4 * 60 + 5) * 1_000L,
            ),
        )
        assertEquals(
            "2天 03:04:05",
            SelfControlElapsedPolicy.formatElapsed(
                anchorAtEpochMs = 0L,
                nowEpochMs = (2 * 24 * 60 * 60 + 3 * 60 * 60 + 4 * 60 + 5) * 1_000L,
            ),
        )
    }

    @Test
    fun elapsedTextClampsFutureAnchorToZero() {
        assertEquals(
            "00:00:00",
            SelfControlElapsedPolicy.formatElapsed(
                anchorAtEpochMs = 10_000L,
                nowEpochMs = 9_000L,
            ),
        )
    }

    @Test
    fun absoluteTextUsesInjectedZoneAndIncludesSeconds() {
        val timestamp = Instant.parse("2026-08-01T01:02:03Z").toEpochMilli()

        assertEquals(
            "2026年08月01日 09:02:03",
            SelfControlElapsedPolicy.formatAbsolute(
                epochMs = timestamp,
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun usageRequestCopyUsesActualEndAndDoesNotResetOnCancel() {
        val copy = SelfControlElapsedPolicy.copyFor(SelfControlElapsedPolicy.Context.USAGE_REQUEST)

        assertTrue(copy.supportingText.contains("取消"))
        assertTrue(copy.supportingText.contains("结束使用"))
        assertEquals("距离上次结束使用", copy.title)
    }

    @Test
    fun appAttemptAndRuleTriggerUseDifferentLabels() {
        val appCopy = SelfControlElapsedPolicy.copyFor(
            SelfControlElapsedPolicy.Context.APP_OPEN_ATTEMPT,
        )
        val ruleCopy = SelfControlElapsedPolicy.copyFor(
            SelfControlElapsedPolicy.Context.RULE_TRIGGER,
        )

        assertEquals("距离上次尝试打开", appCopy.title)
        assertEquals("距离上次触发拦截", ruleCopy.title)
    }

    @Test
    fun firstGenericAttemptUsesCurrentAttemptAsAnchor() {
        val state = SelfControlElapsedPolicy.stateForAttempt(
            previousOccurredAt = null,
            currentOccurredAt = 100L,
        )

        assertEquals(
            SelfControlElapsedPolicy.ElapsedState.Running(
                anchorAtEpochMs = 100L,
                firstOccurrence = true,
            ),
            state,
        )
    }

    @Test
    fun genericAttemptClockRollbackIsUnavailable() {
        assertEquals(
            SelfControlElapsedPolicy.ElapsedState.Unavailable,
            SelfControlElapsedPolicy.stateForAttempt(
                previousOccurredAt = 200L,
                currentOccurredAt = 100L,
            ),
        )
    }

    @Test
    fun noUsageHistoryDoesNotPretendThereWasAPreviousRequest() {
        assertEquals(
            SelfControlElapsedPolicy.ElapsedState.NoHistory,
            SelfControlElapsedPolicy.stateForUsageRequest(previousRequestedAt = null),
        )
    }

    @Test
    fun eventKeysRemainStableAndIncludeTheActualTarget() {
        assertEquals(
            "app_blocker:com.example.video",
            SelfControlElapsedPolicy.appBlockerEventKey("com.example.video"),
        )
        assertEquals(
            "selector_intercept:v2:123:com.example.video:2:7:key:9",
            SelfControlElapsedPolicy.selectorInterceptEventKey(
                subsId = 123L,
                appId = "com.example.video",
                groupType = 2,
                groupKey = 7,
                ruleIdentity = "key:9",
            ),
        )
        assertEquals(
            "url_intercept:456",
            SelfControlElapsedPolicy.urlInterceptEventKey(456L),
        )
        assertTrue(
            SelfControlElapsedPolicy.selectorInterceptEventKey(123L, "a.app", 2, 7, "key:9") !=
                SelfControlElapsedPolicy.selectorInterceptEventKey(123L, "b.app", 2, 7, "key:9"),
        )
    }
}
