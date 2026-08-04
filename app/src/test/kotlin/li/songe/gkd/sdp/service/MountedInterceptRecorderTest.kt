package li.songe.gkd.sdp.service

import kotlinx.coroutines.runBlocking
import li.songe.gkd.sdp.data.SelectorRuleSnapshot
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MountedInterceptRecorderTest {
    private val selectorSnapshot = SelectorRuleSnapshot(
        subsId = 42L,
        subsVersion = 123,
        appId = "demo.app",
        groupType = 2,
        groupKey = 7,
        ruleIndex = 2,
        ruleKey = 9,
        ruleName = "确认按钮",
        groupName = "弹窗规则",
        subscriptionName = "示例订阅",
        matchedAt = 100L,
    )

    private class FakeActionSink : MountedInterceptRecorder.ActionLogSink {
        val snapshots = mutableListOf<SelectorRuleSnapshot>()
        var fail = false

        override suspend fun recordIntercepted(snapshot: SelectorRuleSnapshot): Long {
            if (fail) error("action log unavailable")
            snapshots += snapshot
            return snapshots.size.toLong()
        }
    }

    private class FakeAttemptSink : MountedInterceptRecorder.AttemptSink {
        val descriptors = mutableListOf<SelfControlIntervalRepository.AttemptDescriptor>()
        var fail = false

        override suspend fun recordIntercept(
            descriptor: SelfControlIntervalRepository.AttemptDescriptor,
            occurredAt: Long,
        ): SelfControlAttempt.RecordedAttemptInsight? {
            if (fail) error("attempt log unavailable")
            descriptors += descriptor
            return SelfControlAttempt.RecordedAttemptInsight(null, emptyList())
        }
    }

    @Test
    fun mountedSelectorWritesActionAndIntervalOnce() = runBlocking {
        val actionSink = FakeActionSink()
        val attemptSink = FakeAttemptSink()
        val recorder = MountedInterceptRecorder(actionSink, attemptSink)
        val pending = MountedInterceptRecorder.Pending(
            recordToken = "selector-100",
            eventKey = selectorSnapshot.eventKey(),
            eventKind = SelfControlAttempt.KIND_SELECTOR_INTERCEPT,
            subjectId = "demo.app",
            subjectLabel = "确认按钮",
            selectorSnapshot = selectorSnapshot,
        )

        val first = recorder.recordMounted(pending, mounted = true, occurredAt = 100L)
        val duplicate = recorder.recordMounted(pending, mounted = true, occurredAt = 100L)

        assertTrue(first.actionLogAttempted)
        assertTrue(first.intervalAttempted)
        assertEquals(1, actionSink.snapshots.size)
        assertEquals(1, attemptSink.descriptors.size)
        assertFalse(duplicate.actionLogAttempted)
        assertFalse(duplicate.intervalAttempted)
    }

    @Test
    fun mountedUrlWritesOnlyInterval() = runBlocking {
        val actionSink = FakeActionSink()
        val attemptSink = FakeAttemptSink()
        val recorder = MountedInterceptRecorder(actionSink, attemptSink)
        val pending = MountedInterceptRecorder.Pending(
            recordToken = "url-100",
            eventKey = "url_intercept:42",
            eventKind = SelfControlAttempt.KIND_URL_INTERCEPT,
            subjectId = "42",
            subjectLabel = "网址规则 #42",
        )

        val result = recorder.recordMounted(pending, mounted = true, occurredAt = 100L)

        assertFalse(result.actionLogAttempted)
        assertTrue(result.intervalAttempted)
        assertTrue(actionSink.snapshots.isEmpty())
        assertEquals(1, attemptSink.descriptors.size)
    }

    @Test
    fun duplicateInvalidAndMountFailureWriteNothing() = runBlocking {
        val actionSink = FakeActionSink()
        val attemptSink = FakeAttemptSink()
        val recorder = MountedInterceptRecorder(actionSink, attemptSink)
        val valid = MountedInterceptRecorder.Pending(
            recordToken = "valid",
            eventKey = selectorSnapshot.eventKey(),
            eventKind = SelfControlAttempt.KIND_SELECTOR_INTERCEPT,
            subjectId = "demo.app",
            subjectLabel = "确认按钮",
            selectorSnapshot = selectorSnapshot,
        )
        val invalid = valid.copy(recordToken = "invalid", eventKey = "")

        recorder.recordMounted(valid, mounted = false, occurredAt = 100L)
        recorder.recordMounted(invalid, mounted = true, occurredAt = 100L)

        assertTrue(actionSink.snapshots.isEmpty())
        assertTrue(attemptSink.descriptors.isEmpty())
    }

    @Test
    fun malformedUrlIntentDescriptorWritesNothing() = runBlocking {
        val actionSink = FakeActionSink()
        val attemptSink = FakeAttemptSink()
        val recorder = MountedInterceptRecorder(actionSink, attemptSink)
        val pending = MountedInterceptRecorder.Pending(
            recordToken = "url-invalid",
            eventKey = "url_intercept:missing",
            eventKind = SelfControlAttempt.KIND_URL_INTERCEPT,
            subjectId = "not-a-rule-id",
            subjectLabel = "网址规则",
        )

        val result = recorder.recordMounted(pending, mounted = true, occurredAt = 100L)

        assertFalse(result.intervalAttempted)
        assertTrue(attemptSink.descriptors.isEmpty())
    }

    @Test
    fun urlDescriptorMustMatchItsStableRuleKey() = runBlocking {
        val recorder = MountedInterceptRecorder(FakeActionSink(), FakeAttemptSink())
        val pending = MountedInterceptRecorder.Pending(
            recordToken = "url-mismatch",
            eventKey = "url_intercept:8",
            eventKind = SelfControlAttempt.KIND_URL_INTERCEPT,
            subjectId = "7",
            subjectLabel = "网址规则",
        )

        val result = recorder.recordMounted(pending, mounted = true, occurredAt = 100L)

        assertFalse(result.intervalAttempted)
    }

    @Test
    fun sinkFailuresAreReportedIndependently() = runBlocking {
        val actionSink = FakeActionSink().also { it.fail = true }
        val attemptSink = FakeAttemptSink()
        val recorder = MountedInterceptRecorder(actionSink, attemptSink)
        val pending = MountedInterceptRecorder.Pending(
            recordToken = "selector-100",
            eventKey = selectorSnapshot.eventKey(),
            eventKind = SelfControlAttempt.KIND_SELECTOR_INTERCEPT,
            subjectId = "demo.app",
            subjectLabel = "确认按钮",
            selectorSnapshot = selectorSnapshot,
        )

        val result = recorder.recordMounted(pending, mounted = true, occurredAt = 100L)

        assertTrue(result.actionLogAttempted)
        assertTrue(result.intervalAttempted)
        assertFalse(result.actionLogSucceeded)
        assertTrue(result.intervalSucceeded)
        assertEquals(1, attemptSink.descriptors.size)
    }
}
