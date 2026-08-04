package li.songe.gkd.sdp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTriggerLogRepositoryTest {
    private class FakeSink : RuleTriggerLogRepository.Sink {
        val logs = mutableListOf<ActionLog>()

        override suspend fun insertBounded(log: ActionLog): Long {
            logs += log
            return logs.size.toLong()
        }
    }

    private val snapshot = SelectorRuleSnapshot(
        subsId = 42L,
        subsVersion = 123,
        appId = "demo.app",
        activityId = "demo.app.MainActivity",
        groupType = 2,
        groupKey = 7,
        ruleIndex = 2,
        ruleKey = 9,
        ruleName = "确认按钮",
        groupName = "弹窗规则",
        subscriptionName = "示例订阅",
        matchedAt = 1_700_000_000_000L,
    )

    @Test
    fun executedAndInterceptedUseDifferentOutcomes() = runBlocking {
        val sink = FakeSink()
        val repository = RuleTriggerLogRepository(sink)

        repository.recordExecuted(snapshot)
        repository.recordIntercepted(snapshot)

        assertEquals(
            listOf(ActionLog.OUTCOME_ACTION_EXECUTED, ActionLog.OUTCOME_INTERCEPTED),
            sink.logs.map { it.outcome },
        )
        assertTrue(sink.logs.all { it.subsId == snapshot.subsId && it.ruleKey == snapshot.ruleKey })
    }

    @Test
    fun repositoryDoesNotChangeRuleRuntimeCounters() = runBlocking {
        val sink = FakeSink()
        val repository = RuleTriggerLogRepository(sink)

        repository.recordIntercepted(snapshot)

        assertEquals(1, sink.logs.size)
        assertEquals(ActionLog.OUTCOME_INTERCEPTED, sink.logs.single().outcome)
    }
}
