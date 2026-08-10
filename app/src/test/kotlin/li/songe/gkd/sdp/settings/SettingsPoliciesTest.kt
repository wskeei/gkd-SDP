package li.songe.gkd.sdp.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormPolicyTest {
    @Test
    fun savesWhenOnlyTitleChanged() {
        assertTrue(SettingsFormPolicy.notificationTextChanged("旧标题", "正文", "新标题", "正文"))
    }

    @Test
    fun savesWhenOnlyTextChanged() {
        assertTrue(SettingsFormPolicy.notificationTextChanged("标题", "旧正文", "标题", "新正文"))
    }

    @Test
    fun doesNotReportChangeWhenNeitherChanged() {
        assertFalse(SettingsFormPolicy.notificationTextChanged("标题", "正文", "标题", "正文"))
    }

    @Test
    fun comparisonIsFieldByField() {
        // title value compared with the title, text value with the text
        assertTrue(SettingsFormPolicy.notificationTextChanged("a", "b", "a", "c"))
        assertTrue(SettingsFormPolicy.notificationTextChanged("a", "b", "c", "b"))
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
