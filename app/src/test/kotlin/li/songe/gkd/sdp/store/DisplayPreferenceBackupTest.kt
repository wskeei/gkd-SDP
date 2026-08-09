package li.songe.gkd.sdp.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPreferenceBackupTest {
    @Test
    fun `system backup uses the three allowlisted display preference files`() {
        assertEquals(
            setOf("app_theme.json", "display_density.json", "language.json"),
            DisplayPreferenceBackupFile.entries.mapTo(mutableSetOf()) { it.filename },
        )
    }

    @Test
    fun `restored display preferences cannot enable runtime features`() {
        val current = settings(
            enableAutomator = false,
            enableShizuku = false,
            usageGuardEnabled = false,
            accessibilityGuardEnabled = false,
        )

        val restored = DisplayPreferenceBackupPolicy.restore(
            current = current,
            theme = AppThemeBackup(enableDarkTheme = true, enableDynamicColor = false),
            density = DisplayDensityBackup(scale = 1.2f),
            language = LanguageBackup(languageTag = "zh-CN"),
        )

        assertEquals(true, restored.enableDarkTheme)
        assertFalse(restored.enableDynamicColor)
        assertEquals(1.2f, restored.displayDensityScale)
        assertEquals("zh-CN", restored.languageTag)
        assertFalse(restored.enableAutomator)
        assertFalse(restored.enableShizuku)
        assertFalse(restored.usageGuardEnabled)
        assertFalse(restored.accessibilityGuardEnabled)

        val uiState = DisplayPreferenceUiPolicy.resolve(
            settings = restored,
            systemLanguageTag = "en-US",
        )
        assertEquals(true, uiState.enableDarkTheme)
        assertEquals(1.2f, uiState.densityScale)
        assertEquals("zh-CN", uiState.languageTag)
    }

    @Test
    fun `invalid display preference values fall back to safe defaults`() {
        val restored = DisplayPreferenceBackupPolicy.restore(
            current = settings(enableDarkTheme = true, displayDensityScale = 1.1f),
            theme = AppThemeBackup(enableDarkTheme = null, enableDynamicColor = true),
            density = DisplayDensityBackup(scale = Float.POSITIVE_INFINITY),
            language = LanguageBackup(languageTag = "not a valid tag !!!"),
        )

        assertNull(restored.enableDarkTheme)
        assertTrue(restored.enableDynamicColor)
        assertEquals(1f, restored.displayDensityScale)
        assertEquals("", restored.languageTag)
    }

    @Test
    fun `missing restored files preserve current display preferences`() {
        val current = settings(enableDarkTheme = true, displayDensityScale = 1.1f)
            .copy(enableDynamicColor = false, languageTag = "zh-CN")

        val restored = DisplayPreferenceBackupPolicy.restore(
            current = current,
            theme = null,
            density = null,
            language = null,
        )

        assertEquals(current, restored)
        assertEquals(
            "zh-CN",
            DisplayPreferenceUiPolicy.resolve(restored, "en-US").languageTag,
        )
    }

    private fun settings(
        enableAutomator: Boolean = false,
        enableShizuku: Boolean = false,
        enableDarkTheme: Boolean? = null,
        displayDensityScale: Float = 1f,
        usageGuardEnabled: Boolean = false,
        accessibilityGuardEnabled: Boolean = false,
    ) = SettingsStore(
        actionToast = "",
        customNotifTitle = "",
        updateChannel = 0,
        enableAutomator = enableAutomator,
        enableShizuku = enableShizuku,
        enableDarkTheme = enableDarkTheme,
        displayDensityScale = displayDensityScale,
        usageGuardEnabled = usageGuardEnabled,
        accessibilityGuardEnabled = accessibilityGuardEnabled,
    )
}
