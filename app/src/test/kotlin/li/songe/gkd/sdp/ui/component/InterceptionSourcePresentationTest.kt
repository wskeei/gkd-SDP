package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.data.SelectorRuleSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterceptionSourcePresentationTest {
    private val selector = SelectorRuleSnapshot(
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
        matchedAt = 100L,
    )

    @Test
    fun selectorPresentationShowsExactRuleAndSafeIdentity() {
        val presentation = InterceptionSourcePresentation.selector(selector)

        assertTrue(presentation.lines.any { it.contains("确认按钮") })
        assertTrue(presentation.lines.any { it.contains("key=9") })
        assertTrue(presentation.lines.any { it.contains("应用") && it.contains("key=7") })
        assertTrue(presentation.lines.any { it.contains("demo.app") })
        assertFalse(presentation.lines.any { it.contains("pattern") })
        assertFalse(presentation.lines.any { it.contains("actualUrl") })
        assertFalse(presentation.lines.any { it.contains("nodeText") })
    }

    @Test
    fun selectorWithoutNameKeepsKeyOrIndexFallback() {
        val presentation = InterceptionSourcePresentation.selector(
            selector.copy(ruleName = null, ruleKey = null, ruleIndex = 3),
        )

        assertTrue(presentation.lines.any { it.contains("规则 #4") })
    }

    @Test
    fun selectorLabelsCollapseWhitespaceBeforeDisplay() {
        val presentation = InterceptionSourcePresentation.selector(
            selector.copy(
                subscriptionName = "  示例   订阅  ",
                groupName = "  弹窗   规则  ",
            ),
        )

        assertTrue(presentation.lines.any { it.contains("订阅：示例 订阅") })
        assertTrue(presentation.lines.any { it.contains("规则组：弹窗 规则") })
    }

    @Test
    fun selectorActivityUsesACompactMiddleEllipsis() {
        val longActivity = "demo.app." + "VeryLongActivityName".repeat(6)
        val presentation = InterceptionSourcePresentation.selector(
            selector.copy(activityId = longActivity),
        )
        val pageLine = presentation.lines.single { it.startsWith("页面：") }

        assertTrue(pageLine.contains("…"))
        assertTrue(pageLine.length < longActivity.length)
    }

    @Test
    fun urlPresentationDoesNotExposePatternOrActualUrl() {
        val presentation = InterceptionSourcePresentation.url(
            ruleId = 4_294_967_296L,
            ruleName = "视频网站",
        )

        assertTrue(presentation.lines.any { it.contains("4,294,967,296") })
        assertTrue(presentation.lines.any { it.contains("视频网站") })
        assertFalse(presentation.lines.any { it.contains("pattern") })
        assertFalse(presentation.lines.any { it.contains("https://") })
    }

    @Test
    fun appBlockerPresentationShowsRuleScheduleAndMode() {
        val rule = BlockTimeRule(
            id = 8L,
            targetType = BlockTimeRule.TARGET_TYPE_APP,
            targetId = "demo.app",
            startTime = "22:00",
            endTime = "08:00",
            daysOfWeek = "1,2,3,4,5",
            isAllowMode = true,
        )

        val presentation = InterceptionSourcePresentation.appBlocker(rule)

        assertTrue(presentation.lines.any { it.contains("规则 #8") })
        assertTrue(presentation.lines.any { it.contains("22:00-08:00") })
        assertTrue(presentation.lines.any { it.contains("允许时间段") })
        assertTrue(presentation.lines.any { it.contains("工作日") })
    }
}
