package li.songe.gkd.sdp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorRuleSnapshotTest {
    private fun snapshot(
        subsVersion: Int = 123,
        appId: String = "demo.app",
        groupType: Int = 2,
        groupKey: Int = 7,
        ruleIndex: Int = 2,
        ruleKey: Int? = 9,
        ruleName: String? = "确认按钮",
        groupName: String? = "弹窗规则",
        subscriptionName: String? = "示例订阅",
    ) = SelectorRuleSnapshot(
        subsId = 42L,
        subsVersion = subsVersion,
        appId = appId,
        groupType = groupType,
        groupKey = groupKey,
        ruleIndex = ruleIndex,
        ruleKey = ruleKey,
        ruleName = ruleName,
        groupName = groupName,
        subscriptionName = subscriptionName,
        matchedAt = 1_700_000_000_000L,
    )

    @Test
    fun keyedRulesRemainStableAcrossSubscriptionVersions() {
        val old = snapshot(subsVersion = 123)
        val updated = snapshot(subsVersion = 124)

        assertEquals(old.eventKey(), updated.eventKey())
        assertEquals(
            "selector_intercept:v2:42:demo.app:2:7:key:9",
            old.eventKey(),
        )
    }

    @Test
    fun unkeyedRulesIncludeVersionAndIndex() {
        val snapshot = snapshot(
            subsVersion = 123,
            ruleIndex = 2,
            ruleKey = null,
        )

        assertEquals(
            "selector_intercept:v2:42:demo.app:2:7:version:123:index:2",
            snapshot.eventKey(),
        )
    }

    @Test
    fun appGroupAndGlobalGroupKeysNeverCollide() {
        val appGroup = snapshot(groupType = 1)
        val globalGroup = snapshot(groupType = 2)

        assertFalse(appGroup.eventKey() == globalGroup.eventKey())
    }

    @Test
    fun differentForegroundAppsNeverShareSelectorHistory() {
        val first = snapshot(appId = "demo.app")
        val second = snapshot(appId = "other.app")

        assertFalse(first.eventKey() == second.eventKey())
    }

    @Test
    fun snapshotConvertsToInterceptedActionLogWithoutSensitiveFields() {
        val source = snapshot(
            ruleName = "  确认   按钮 ",
            groupName = "  弹窗 规则 ",
            subscriptionName = " 示例 订阅 ",
        )

        val log = source.toActionLog(ActionLog.OUTCOME_INTERCEPTED)

        assertEquals(42L, log.subsId)
        assertEquals(123, log.subsVersion)
        assertEquals(2, log.groupType)
        assertEquals(7, log.groupKey)
        assertEquals(2, log.ruleIndex)
        assertEquals(9, log.ruleKey)
        assertEquals(ActionLog.OUTCOME_INTERCEPTED, log.outcome)
        assertEquals("确认 按钮", log.ruleNameSnapshot)
        assertEquals("弹窗 规则", log.groupNameSnapshot)
        assertEquals("示例 订阅", log.subsNameSnapshot)
        assertTrue(log.matchedAt == source.matchedAt)
        assertFalse(log.containsSensitiveSelectorData())
    }

    @Test
    fun unnamedRuleStillHasSafeIdentity() {
        val source = snapshot(ruleName = null, ruleKey = null, ruleIndex = 4)

        assertEquals("规则 #5", source.displayRuleIdentity())
        assertFalse(source.toActionLog(ActionLog.OUTCOME_INTERCEPTED).ruleNameSnapshot == "")
    }
}
