package li.songe.gkd.sdp.ui.style

import android.content.res.Configuration
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.store.DisplayPreferenceUiPolicy
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.LocalDarkTheme
import li.songe.gkd.sdp.ui.share.LocalIsTalkbackEnabled
import li.songe.gkd.sdp.util.AndroidTarget
import java.util.Locale

private val LightColorScheme = lightColorScheme(
    primary = ColorTokens.Light.primary,
    onPrimary = ColorTokens.Light.onPrimary,
    primaryContainer = ColorTokens.Light.primaryContainer,
    onPrimaryContainer = ColorTokens.Light.onPrimaryContainer,
    secondary = ColorTokens.Light.secondary,
    onSecondary = ColorTokens.Light.onSecondary,
    secondaryContainer = ColorTokens.Light.secondaryContainer,
    onSecondaryContainer = ColorTokens.Light.onSecondaryContainer,
    tertiary = ColorTokens.Light.tertiary,
    onTertiary = ColorTokens.Light.onTertiary,
    tertiaryContainer = ColorTokens.Light.tertiaryContainer,
    onTertiaryContainer = ColorTokens.Light.onTertiaryContainer,
    error = ColorTokens.Light.error,
    onError = ColorTokens.Light.onError,
    errorContainer = ColorTokens.Light.errorContainer,
    onErrorContainer = ColorTokens.Light.onErrorContainer,
    background = ColorTokens.Light.background,
    onBackground = ColorTokens.Light.onBackground,
    surface = ColorTokens.Light.surface,
    onSurface = ColorTokens.Light.onSurface,
    surfaceVariant = ColorTokens.Light.surfaceVariant,
    onSurfaceVariant = ColorTokens.Light.onSurfaceVariant,
    outline = ColorTokens.Light.outline,
    outlineVariant = ColorTokens.Light.outlineVariant,
)
private val DarkColorScheme = darkColorScheme(
    primary = ColorTokens.Dark.primary,
    onPrimary = ColorTokens.Dark.onPrimary,
    primaryContainer = ColorTokens.Dark.primaryContainer,
    onPrimaryContainer = ColorTokens.Dark.onPrimaryContainer,
    secondary = ColorTokens.Dark.secondary,
    onSecondary = ColorTokens.Dark.onSecondary,
    secondaryContainer = ColorTokens.Dark.secondaryContainer,
    onSecondaryContainer = ColorTokens.Dark.onSecondaryContainer,
    tertiary = ColorTokens.Dark.tertiary,
    onTertiary = ColorTokens.Dark.onTertiary,
    tertiaryContainer = ColorTokens.Dark.tertiaryContainer,
    onTertiaryContainer = ColorTokens.Dark.onTertiaryContainer,
    error = ColorTokens.Dark.error,
    onError = ColorTokens.Dark.onError,
    errorContainer = ColorTokens.Dark.errorContainer,
    onErrorContainer = ColorTokens.Dark.onErrorContainer,
    background = ColorTokens.Dark.background,
    onBackground = ColorTokens.Dark.onBackground,
    surface = ColorTokens.Dark.surface,
    onSurface = ColorTokens.Dark.onSurface,
    surfaceVariant = ColorTokens.Dark.surfaceVariant,
    onSurfaceVariant = ColorTokens.Dark.onSurfaceVariant,
    outline = ColorTokens.Dark.outline,
    outlineVariant = ColorTokens.Dark.outlineVariant,
)

@Composable
fun AppTheme(
    invertedTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val baseConfiguration = LocalConfiguration.current
    val systemLanguageTag = remember(baseConfiguration) {
        baseConfiguration.locales[0].toLanguageTag().ifBlank { "zh-CN" }
    }
    val displayPreferencesFlow = remember(systemLanguageTag) {
        storeFlow.map {
            DisplayPreferenceUiPolicy.resolve(it, systemLanguageTag)
        }.debounce(300).stateIn(
            scope,
            SharingStarted.Eagerly,
            DisplayPreferenceUiPolicy.resolve(storeFlow.value, systemLanguageTag),
        )
    }
    val displayPreferences by displayPreferencesFlow.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = (displayPreferences.enableDarkTheme ?: systemInDarkTheme).let {
        if (invertedTheme) !it else it
    }
    val colorScheme = when {
        AndroidTarget.S && displayPreferences.enableDynamicColor && darkTheme ->
            dynamicDarkColorScheme(app)
        AndroidTarget.S && displayPreferences.enableDynamicColor && !darkTheme ->
            dynamicLightColorScheme(app)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, displayPreferences.densityScale) {
        Density(
            density = baseDensity.density * displayPreferences.densityScale,
            fontScale = baseDensity.fontScale,
        )
    }
    val baseContext = LocalContext.current
    val localizedConfiguration = remember(
        baseConfiguration,
        displayPreferences.languageTag,
    ) {
        Configuration(baseConfiguration).apply {
            setLocale(Locale.forLanguageTag(displayPreferences.languageTag))
        }
    }
    val localizedContext = remember(baseContext, localizedConfiguration) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }
    val activity = LocalActivity.current
    if (activity != null) {
        LaunchedEffect(darkTheme) {
            // https://github.com/gkd-kit/gkd/pull/421
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
            }
        }
        val bg = colorScheme.background.toArgb()
        LaunchedEffect(darkTheme, bg) {
            activity.window.decorView.setBackgroundColor(bg)
        }
    }

    var isTalkbackEnabled by remember { mutableStateOf(app.a11yManager.isTouchExplorationEnabled) }
    DisposableEffect(null) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener {
            isTalkbackEnabled = it
        }
        app.a11yManager.addTouchExplorationStateChangeListener(listener)
        onDispose {
            app.a11yManager.removeTouchExplorationStateChangeListener(listener)
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalActivity provides activity,
        LocalConfiguration provides localizedConfiguration,
        LocalDensity provides scaledDensity,
        LocalDarkTheme provides darkTheme,
        LocalIsTalkbackEnabled provides isTalkbackEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
