package li.songe.gkd.sdp.store

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.util.json
import java.io.File
import java.util.Locale

enum class DisplayPreferenceBackupFile(val filename: String) {
    APP_THEME("app_theme.json"),
    DISPLAY_DENSITY("display_density.json"),
    LANGUAGE("language.json"),
}

@Serializable
data class AppThemeBackup(
    val schema: Int = 1,
    val enableDarkTheme: Boolean?,
    val enableDynamicColor: Boolean,
)

@Serializable
data class DisplayDensityBackup(
    val schema: Int = 1,
    val scale: Float,
)

@Serializable
data class LanguageBackup(
    val schema: Int = 1,
    val languageTag: String,
)

internal data class DisplayPreferenceSnapshot(
    val theme: AppThemeBackup,
    val density: DisplayDensityBackup,
    val language: LanguageBackup,
)

object DisplayPreferenceBackupPolicy {
    private val languageTagPattern = Regex("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$")

    fun restore(
        current: SettingsStore,
        theme: AppThemeBackup?,
        density: DisplayDensityBackup?,
        language: LanguageBackup?,
    ): SettingsStore {
        val validTheme = theme?.takeIf { it.schema == 1 }
        val restoredDensity = density?.takeIf { it.schema == 1 }?.let {
            it.scale.takeIf { scale -> scale.isFinite() && scale in 0.85f..1.30f } ?: 1f
        } ?: current.displayDensityScale
        val restoredLanguage = language?.takeIf { it.schema == 1 }?.let {
            sanitizeLanguageTag(it.languageTag)
        } ?: current.languageTag
        return current.copy(
            enableDarkTheme = if (validTheme != null) {
                validTheme.enableDarkTheme
            } else {
                current.enableDarkTheme
            },
            enableDynamicColor = validTheme?.enableDynamicColor ?: current.enableDynamicColor,
            displayDensityScale = restoredDensity,
            languageTag = restoredLanguage,
        )
    }

    private fun sanitizeLanguageTag(value: String): String {
        if (value.isBlank()) return ""
        if (value.length > 35 || !languageTagPattern.matches(value)) return ""
        return Locale.forLanguageTag(value).toLanguageTag().takeUnless { it == "und" }.orEmpty()
    }

    internal fun snapshot(settings: SettingsStore) = DisplayPreferenceSnapshot(
        theme = AppThemeBackup(
            enableDarkTheme = settings.enableDarkTheme,
            enableDynamicColor = settings.enableDynamicColor,
        ),
        density = DisplayDensityBackup(
            scale = settings.displayDensityScale
                .takeIf { it.isFinite() && it in 0.85f..1.30f }
                ?: 1f,
        ),
        language = LanguageBackup(
            languageTag = sanitizeLanguageTag(settings.languageTag),
        ),
    )
}

fun initDisplayPreferenceBackup() {
    val folder = app.filesDir.resolve("store").apply(File::mkdirs)
    val restored = DisplayPreferenceBackupPolicy.restore(
        current = storeFlow.value,
        theme = readBackup(folder, DisplayPreferenceBackupFile.APP_THEME),
        density = readBackup(folder, DisplayPreferenceBackupFile.DISPLAY_DENSITY),
        language = readBackup(folder, DisplayPreferenceBackupFile.LANGUAGE),
    )
    storeFlow.value = restored
    writeDisplayPreferenceBackup(folder, DisplayPreferenceBackupPolicy.snapshot(restored))
    appScope.launch {
        storeFlow
            .map(DisplayPreferenceBackupPolicy::snapshot)
            .distinctUntilChanged()
            .collect { snapshot -> writeDisplayPreferenceBackup(folder, snapshot) }
    }
}

private inline fun <reified T> readBackup(
    folder: File,
    backupFile: DisplayPreferenceBackupFile,
): T? = runCatching {
    json.decodeFromString<T>(folder.resolve(backupFile.filename).readText())
}.getOrNull()

private fun writeDisplayPreferenceBackup(
    folder: File,
    snapshot: DisplayPreferenceSnapshot,
) {
    writeTextAtomically(
        folder.resolve(DisplayPreferenceBackupFile.APP_THEME.filename),
        json.encodeToString(snapshot.theme),
    )
    writeTextAtomically(
        folder.resolve(DisplayPreferenceBackupFile.DISPLAY_DENSITY.filename),
        json.encodeToString(snapshot.density),
    )
    writeTextAtomically(
        folder.resolve(DisplayPreferenceBackupFile.LANGUAGE.filename),
        json.encodeToString(snapshot.language),
    )
}
