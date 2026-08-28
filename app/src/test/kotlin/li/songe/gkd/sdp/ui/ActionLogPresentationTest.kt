package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.ActionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLogPresentationTest {
    private fun log(outcome: Int) = ActionLog(
        ctime = 100L,
        appId = "demo.app",
        subsId = 42L,
        subsVersion = 123,
        groupKey = 7,
        groupType = 2,
        ruleIndex = 2,
        ruleKey = 9,
        outcome = outcome,
        matchedAt = 100L,
        subsNameSnapshot = "示例订阅",
        groupNameSnapshot = "弹窗规则",
        ruleNameSnapshot = "确认按钮",
    )

    @Test
    fun interceptedOutcomeIsExplicitlyNotAnExecutedAction() {
        val presentation = ActionLogPresentation.from(log(ActionLog.OUTCOME_INTERCEPTED))

        assertEquals("已拦截", presentation.outcomeTitle)
        assertTrue(presentation.outcomeDescription.contains("动作未执行"))
    }

    @Test
    fun executedOutcomeRetainsExistingMeaning() {
        val presentation = ActionLogPresentation.from(log(ActionLog.OUTCOME_ACTION_EXECUTED))

        assertEquals("已执行", presentation.outcomeTitle)
        assertTrue(presentation.outcomeDescription.contains("动作已执行"))
    }

    @Test
    fun snapshotFallbackSurvivesMissingSubscriptionContent() {
        val presentation = ActionLogPresentation.from(log(ActionLog.OUTCOME_INTERCEPTED))

        assertEquals("示例订阅", presentation.subscriptionName)
        assertEquals("弹窗规则", presentation.groupName)
        assertEquals("确认按钮", presentation.ruleName)
    }
}
