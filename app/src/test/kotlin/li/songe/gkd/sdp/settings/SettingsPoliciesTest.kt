package li.songe.gkd.sdp.settings

import li.songe.gkd.sdp.R
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
            titleRes = R.string.settings_capabilities_title,
            searchTitle = "运行能力",
            titleEn = "Capabilities",
            keywords = listOf("权限", "无障碍", "Shizuku"),
            aliases = listOf("能力中心"),
        ),
        SettingsEntry(
            group = SettingsGroup.SELF_CONTROL,
            titleRes = R.string.settings_self_control_title,
            searchTitle = "数字自律",
            titleEn = "Self-control",
            keywords = listOf("自律", "复盘"),
        ),
        SettingsEntry(
            group = SettingsGroup.PRIVACY_DATA,
            titleRes = R.string.settings_privacy_title,
            searchTitle = "隐私与数据",
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

    @Test
    fun indexCoversEverySettingsGroupInDisplayOrder() {
        assertEquals(
            SettingsGroup.entries.toList(),
            SettingsIndex.entries.map { it.group },
        )
        assertEquals(SettingsIndex.entries.size, SettingsIndex.entries.map { it.titleRes }.distinct().size)
    }

    @Test
    fun recentOnlyReturnsKnownIdsAndIsCappedAtFive() {
        val recent = SettingsSearchPolicy.recent(
            entries = SettingsIndex.entries,
            recentIds = listOf("privacy_data", "missing", "capabilities", "privacy_data"),
        )
        assertEquals(listOf("privacy_data", "capabilities"), recent.map { it.id })
        assertEquals(
            5,
            SettingsSearchPolicy.recent(
                entries = SettingsIndex.entries,
                recentIds = SettingsIndex.entries.map { it.id } + listOf("privacy_data"),
            ).size,
        )
    }

    @Test
    fun entriesHaveStableIdsAndTargets() {
        assertEquals(
            SettingsIndex.entries.size,
            SettingsIndex.entries.map { it.id }.distinct().size,
        )
        assertTrue(SettingsIndex.entries.all { it.target is SettingsTarget.Route || it.target is SettingsTarget.InPage })
    }

    @Test
    fun rememberRecentMovesSelectionToFrontAndCapsLimit() {
        assertEquals(
            listOf("privacy_data", "capabilities"),
            SettingsSearchPolicy.rememberRecent(
                recentIds = listOf("capabilities", "privacy_data"),
                selectedId = "privacy_data",
            ),
        )
        assertEquals(
            emptyList<String>(),
            SettingsSearchPolicy.recent(SettingsIndex.entries, listOf("privacy_data"), limit = 0),
        )
        assertEquals(
            emptyList<String>(),
            SettingsSearchPolicy.rememberRecent(listOf("privacy_data"), "privacy_data", limit = 0),
        )
    }
}
