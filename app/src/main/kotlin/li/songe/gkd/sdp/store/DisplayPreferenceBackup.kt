package li.songe.gkd.sdp.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.backup.BackupDataMutationBarrier
import li.songe.gkd.sdp.util.json
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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

data class DisplayPreferenceUiState(
    val enableDarkTheme: Boolean?,
    val enableDynamicColor: Boolean,
    val densityScale: Float,
    val languageTag: String,
)

object DisplayPreferenceUiPolicy {
    fun resolve(
        settings: SettingsStore,
        systemLanguageTag: String,
    ) = DisplayPreferenceUiState(
        enableDarkTheme = settings.enableDarkTheme,
        enableDynamicColor = settings.enableDynamicColor,
        densityScale = settings.displayDensityScale
            .takeIf { it.isFinite() && it in 0.85f..1.30f }
            ?: 1f,
        languageTag = DisplayPreferenceBackupPolicy.sanitizeLanguageTag(
            settings.languageTag,
        ).ifBlank { systemLanguageTag },
    )
}

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

    internal fun sanitizeLanguageTag(value: String): String {
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

object ProcessLocalePolicy {
    fun resolve(languageTag: String, systemLocale: Locale): Locale =
        DisplayPreferenceBackupPolicy.sanitizeLanguageTag(languageTag)
            .takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
            ?: systemLocale
}

private data class PersistedDisplayPreferences(
    val theme: AppThemeBackup?,
    val density: DisplayDensityBackup?,
    val language: LanguageBackup?,
) {
    fun matches(snapshot: DisplayPreferenceSnapshot): Boolean =
        theme == snapshot.theme && density == snapshot.density && language == snapshot.language
}

fun initDisplayPreferenceBackup() {
    val folder = app.filesDir.resolve("store").apply(File::mkdirs)
    val persisted = PersistedDisplayPreferences(
        theme = readBackup(folder, DisplayPreferenceBackupFile.APP_THEME),
        density = readBackup(folder, DisplayPreferenceBackupFile.DISPLAY_DENSITY),
        language = readBackup(folder, DisplayPreferenceBackupFile.LANGUAGE),
    )
    val restored = DisplayPreferenceBackupPolicy.restore(
        current = storeFlow.value,
        theme = persisted.theme,
        density = persisted.density,
        language = persisted.language,
    )
    storeFlow.value = restored
    initProcessLocaleCoordinator()
    appScope.launch(Dispatchers.IO) {
        var lastPersisted = persisted
        storeFlow
            .map(DisplayPreferenceBackupPolicy::snapshot)
            .distinctUntilChanged()
            .collect { snapshot ->
                while (!lastPersisted.matches(snapshot)) {
                    val updated = BackupDataMutationBarrier.withMutation {
                        writeChangedDisplayPreferences(folder, lastPersisted, snapshot)
                    }
                    if (updated == lastPersisted) delay(1_000)
                    lastPersisted = updated
                }
            }
    }
}

private val processLocaleCoordinatorStarted = AtomicBoolean(false)

private fun initProcessLocaleCoordinator() {
    if (!processLocaleCoordinatorStarted.compareAndSet(false, true)) return
    val systemLocale = Locale.getDefault()
    appScope.launch(Dispatchers.Default) {
        storeFlow
            .map { settings ->
                ProcessLocalePolicy.resolve(settings.languageTag, systemLocale)
            }
            .distinctUntilChanged()
            .collect(Locale::setDefault)
    }
}

private inline fun <reified T> readBackup(
    folder: File,
    backupFile: DisplayPreferenceBackupFile,
): T? = runCatching {
    json.decodeFromString<T>(folder.resolve(backupFile.filename).readText())
}.getOrNull()

private fun writeChangedDisplayPreferences(
    folder: File,
    persisted: PersistedDisplayPreferences,
    snapshot: DisplayPreferenceSnapshot,
): PersistedDisplayPreferences {
    var result = persisted
    if (persisted.theme != snapshot.theme) {
        if (writeBackup(folder, DisplayPreferenceBackupFile.APP_THEME, snapshot.theme)) {
            result = result.copy(theme = snapshot.theme)
        }
    }
    if (persisted.density != snapshot.density) {
        if (writeBackup(folder, DisplayPreferenceBackupFile.DISPLAY_DENSITY, snapshot.density)) {
            result = result.copy(density = snapshot.density)
        }
    }
    if (persisted.language != snapshot.language) {
        if (writeBackup(folder, DisplayPreferenceBackupFile.LANGUAGE, snapshot.language)) {
            result = result.copy(language = snapshot.language)
        }
    }
    return result
}

private inline fun <reified T> writeBackup(
    folder: File,
    backupFile: DisplayPreferenceBackupFile,
    value: T,
): Boolean = runCatching {
    writeTextAtomically(
        folder.resolve(backupFile.filename),
        json.encodeToString(value),
    )
}.isSuccess
