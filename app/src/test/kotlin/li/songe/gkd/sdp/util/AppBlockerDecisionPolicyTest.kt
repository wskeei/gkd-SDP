package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AppBlockerDecisionPolicyTest {
    @Test
    fun normalWindowIncludesStartAndExcludesEnd() {
        val rule = rule(start = "09:00", end = "10:00")

        assertTrue(
            AppBlockerDecisionPolicy.decide(
                packageName = "com.example.app",
                snapshot = snapshot(rule),
                now = LocalDateTime.of(2026, 8, 3, 9, 0),
            ) is AppBlockerDecision.Block
        )
        assertEquals(
            AppBlockerDecision.OutsideSchedule,
            AppBlockerDecisionPolicy.decide(
                packageName = "com.example.app",
                snapshot = snapshot(rule),
                now = LocalDateTime.of(2026, 8, 3, 10, 0),
            ),
        )
    }

    @Test
    fun overnightWindowUsesTheSelectedStartDay() {
        val rule = rule(start = "22:00", end = "08:00", days = "1")

        assertTrue(
            AppBlockerDecisionPolicy.decide(
                packageName = "com.example.app",
                snapshot = snapshot(rule),
                now = LocalDateTime.of(2026, 8, 4, 2, 0), // Tuesday, Monday rule is active.
            ) is AppBlockerDecision.Block
        )
    }

    @Test
    fun allDayTemplateIncludesTheLastMinuteOfTheDay() {
        val rule = rule(start = "00:00", end = "23:59", days = "1,2,3,4,5,6,7")

        assertTrue(
            AppBlockerDecisionPolicy.decide(
                packageName = "com.example.app",
                snapshot = snapshot(rule),
                now = LocalDateTime.of(2026, 8, 3, 23, 59, 59),
            ) is AppBlockerDecision.Block
        )
    }

    @Test
    fun invalidRuleIsReturnedAsDecisionInsteadOfThrowing() {
        val rule = rule(start = "not-a-time", end = "10:00")

        assertEquals(
            AppBlockerDecision.InvalidRule(listOf(rule.id)),
            AppBlockerDecisionPolicy.decide(
                packageName = "com.example.app",
                snapshot = snapshot(rule),
                now = LocalDateTime.of(2026, 8, 3, 9, 0),
            ),
        )
    }

    @Test
    fun disabledGroupAndMissingRuleAreDistinguishable() {
        val disabledGroup = AppGroup(id = 7, name = "短视频", appIds = "[\"com.example.app\"]", enabled = false)

        assertEquals(
            AppBlockerDecision.GroupDisabled,
            AppBlockerDecisionPolicy.decide(
                packageName = "com.example.app",
                snapshot = AppBlockerDecisionPolicy.Snapshot(groups = listOf(disabledGroup)),
                now = LocalDateTime.of(2026, 8, 3, 9, 0),
            ),
        )
        assertEquals(
            AppBlockerDecision.NoMatchingTarget,
            AppBlockerDecisionPolicy.decide(
                packageName = "com.other.app",
                snapshot = AppBlockerDecisionPolicy.Snapshot(groups = listOf(disabledGroup)),
                now = LocalDateTime.of(2026, 8, 3, 9, 0),
            ),
        )
    }

    @Test
    fun newestBlockingRuleWinsMessagePriority() {
        val older = rule(id = 1, createdAt = 1, message = "旧")
        val newer = rule(id = 2, createdAt = 2, message = "新")

        assertEquals(
            AppBlockerDecision.Block(ruleId = 2, message = "新"),
            AppBlockerDecisionPolicy.decide(
                packageName = "com.example.app",
                snapshot = snapshot(older, newer),
                now = LocalDateTime.of(2026, 8, 3, 9, 0),
            ),
        )
    }

    private fun snapshot(vararg rules: BlockTimeRule) =
        AppBlockerDecisionPolicy.Snapshot(rules = rules.toList())

    private fun rule(
        id: Long = 1,
        start: String = "09:00",
        end: String = "10:00",
        days: String = "1,2,3,4,5,6,7",
        createdAt: Long = 1,
        message: String = "这真的重要吗？",
    ) = BlockTimeRule(
        id = id,
        targetType = BlockTimeRule.TARGET_TYPE_APP,
        targetId = "com.example.app",
        startTime = start,
        endTime = end,
        daysOfWeek = days,
        createdAt = createdAt,
        interceptMessage = message,
    )
}
