package li.songe.gkd.sdp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.settings.SettingsEntry
import li.songe.gkd.sdp.settings.SettingsGroup
import li.songe.gkd.sdp.settings.SettingsSearchPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSearchTest {
    @Test
    fun searchMatchesChineseAndEnglishSemanticAliases() {
        val entries = listOf(
            SettingsEntry(
                group = SettingsGroup.PRIVACY_DATA,
                title = "隐私与数据",
                titleEn = "Privacy & data",
                keywords = listOf("备份", "删除"),
                aliases = listOf("数据"),
            ),
        )

        assertEquals(1, SettingsSearchPolicy.search(entries, "隐私").size)
        assertEquals(1, SettingsSearchPolicy.search(entries, "privacy").size)
        assertEquals(1, SettingsSearchPolicy.search(entries, "数据").size)
        assertEquals(0, SettingsSearchPolicy.search(entries, "不存在").size)
    }
}
