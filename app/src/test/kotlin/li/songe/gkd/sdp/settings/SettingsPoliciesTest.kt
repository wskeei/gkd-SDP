package li.songe.gkd.sdp.settings

import li.songe.gkd.sdp.store.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormPolicyTest {
    private fun store(
        title: String = "默认标题",
        text: String = "默认正文",
    ) = SettingsStore(
        customNotifTitle = title,
        customNotifText = text,
    )

    @Test
    fun savesWhenOnlyTitleChanged() {
        val s = store(title = "旧标题", text = "正文")
        assertTrue(SettingsFormPolicy.notificationTextChanged(s, "新标题", "正文"))
        val updated = SettingsFormPolicy.notificationTextUpdate(s, "新标题", "正文")
        assertEquals("新标题", updated.customNotifTitle)
        assertEquals("正文", updated.customNotifText)
    }

    @Test
    fun savesWhenOnlyTextChanged() {
        val s = store(title = "标题", text = "旧正文")
        assertTrue(SettingsFormPolicy.notificationTextChanged(s, "标题", "新正文"))
        val updated = SettingsFormPolicy.notificationTextUpdate(s, "标题", "新正文")
        assertEquals("标题", updated.customNotifTitle)
        assertEquals("新正文", updated.customNotifText)
    }

    @Test
    fun doesNotReportChangeWhenNeitherChanged() {
        val s = store(title = "标题", text = "正文")
        assertFalse(SettingsFormPolicy.notificationTextChanged(s, "标题", "正文"))
    }

    @Test
    fun updateAlwaysSavesBothCurrentValues() {
        val s = store(title = "a", text = "b")
        val updated = SettingsFormPolicy.notificationTextUpdate(s, "c", "d")
        assertEquals("c", updated.customNotifTitle)
        assertEquals("d", updated.customNotifText)
        assertEquals("a", s.customNotifTitle)
        assertEquals("b", s.customNotifText)
    }
}

class SettingsSearchPolicyTest {
    private val entries = listOf(
        SettingsEntry(
            group = SettingsGroup.RUNTIME_CAPABILITIES,
            title = "运行能力",
            titleEn = "Capabilities",
            keywords = listOf("权限", "无障碍", "Shizuku"),
            aliases = listOf("能力中心"),
        ),
        SettingsEntry(
            group = SettingsGroup.SELF_CONTROL,
            title = "数字自律",
            titleEn = "Self-control",
            keywords = listOf("自律", "复盘"),
        ),
        SettingsEntry(
            group = SettingsGroup.PRIVACY_DATA,
            title = "隐私与数据",
            titleEn = "Privacy & data",
            keywords = listOf("备份", "删除"),
            aliases = listOf("数据"),
        ),
    )

    @Test
    fun matchesChineseTitle() {
        assertTrue(SettingsSearchPolicy.matches(entries[0], "运行"))
    }

    @Test
    fun matchesEnglishTitle() {
        assertTrue(SettingsSearchPolicy.matches(entries[1], "self"))
        assertTrue(SettingsSearchPolicy.matches(entries[1], "SELF-CONTROL"))
    }

    @Test
    fun matchesKeywordsAndAliases() {
        assertTrue(SettingsSearchPolicy.matches(entries[0], "Shizuku"))
        assertTrue(SettingsSearchPolicy.matches(entries[0], "能力中心"))
        assertTrue(SettingsSearchPolicy.matches(entries[2], "备份"))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertEquals(3, SettingsSearchPolicy.search(entries, "").size)
    }

    @Test
    fun searchIsCaseInsensitive() {
        assertEquals(1, SettingsSearchPolicy.search(entries, "SELF").size)
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(SettingsSearchPolicy.search(entries, "不存在词").isEmpty())
    }
}
