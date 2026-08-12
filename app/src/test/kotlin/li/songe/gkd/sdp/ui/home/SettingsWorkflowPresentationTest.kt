package li.songe.gkd.sdp.ui.home

import li.songe.gkd.sdp.backup.BackupErrorCode
import li.songe.gkd.sdp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsWorkflowPresentationTest {
    @Test
    fun passwordValidationUsesUnicodeCodePoints() {
        assertFalse(settingsPasswordIsValid("short"))
        assertFalse(settingsPasswordIsValid("12345678901"))
        assertTrue(settingsPasswordIsValid("123456789012"))
        assertTrue(settingsPasswordIsValid("测试密码至少十二个字符以上"))
    }

    @Test
    fun backupCategoryTitlesAndSubtitlesAreStable() {
        val ids = listOf(
            "settings",
            "subscriptions",
            "self_control_config",
            "self_control_history",
            "upstream_history",
            "sensitive_optional",
        )
        ids.forEach { id ->
            assertTrue(backupCategoryTitleRes(id) != 0)
            if (id != "sensitive_optional") {
                assertTrue(backupCategorySubtitleRes(id) != null)
            }
        }
        assertEquals(R.string.backup_category_unknown_title, backupCategoryTitleRes("unknown"))
        assertNull(backupCategorySubtitleRes("unknown"))
    }

    @Test
    fun backupErrorTextCoversEveryStableCode() {
        BackupErrorCode.entries.forEach { code ->
            assertTrue(backupErrorTextRes(code) != 0)
        }
    }
}
