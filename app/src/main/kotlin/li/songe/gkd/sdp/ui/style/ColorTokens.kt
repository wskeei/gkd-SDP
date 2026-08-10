package li.songe.gkd.sdp.ui.style

import androidx.compose.ui.graphics.Color

/**
 * Fixed color roles shared by the light and dark schemes.
 *
 * Every role here is a product decision; change the value and the
 * DesignTokenContractTest together. Roles not listed are derived in
 * Theme.kt from these anchors so Material components keep usable contrast.
 */
object ColorTokens {

    object Light {
        val primary = Color(0xFF4F46E5)
        val onPrimary = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFE0E7FF)
        val onPrimaryContainer = Color(0xFF1E1B4B)
        val secondary = Color(0xFF0F766E)
        val onSecondary = Color(0xFFFFFFFF)
        val secondaryContainer = Color(0xFFCCFBF1)
        val onSecondaryContainer = Color(0xFF042F2C)
        val tertiary = Color(0xFFB45309)
        val onTertiary = Color(0xFFFFFFFF)
        val tertiaryContainer = Color(0xFFFFE7C2)
        val onTertiaryContainer = Color(0xFF3A2E00)
        val error = Color(0xFFB3261E)
        val onError = Color(0xFFFFFFFF)
        val errorContainer = Color(0xFFF9DEDC)
        val onErrorContainer = Color(0xFF410E0B)
        val background = Color(0xFFF8FAFC)
        val onBackground = Color(0xFF0F172A)
        val surface = Color(0xFFFFFFFF)
        val onSurface = Color(0xFF0F172A)
        val surfaceVariant = Color(0xFFF1F5F9)
        val onSurfaceVariant = Color(0xFF475569)
        val outline = Color(0xFF64748B)
        val outlineVariant = Color(0xFFCBD5E1)
    }

    object Dark {
        val primary = Color(0xFFA5B4FC)
        val onPrimary = Color(0xFF1E1B4B)
        val primaryContainer = Color(0xFF312E81)
        val onPrimaryContainer = Color(0xFFE0E7FF)
        val secondary = Color(0xFF5EEAD4)
        val onSecondary = Color(0xFF042F2C)
        val secondaryContainer = Color(0xFF134E4A)
        val onSecondaryContainer = Color(0xFFCCFBF1)
        val tertiary = Color(0xFFFCD34D)
        val onTertiary = Color(0xFF3A2E00)
        val tertiaryContainer = Color(0xFF6B4E00)
        val onTertiaryContainer = Color(0xFFFFE7C2)
        val error = Color(0xFFFFB4AB)
        val onError = Color(0xFF690005)
        val errorContainer = Color(0xFF93000A)
        val onErrorContainer = Color(0xFFFFDAD6)
        val background = Color(0xFF0F172A)
        val onBackground = Color(0xFFF8FAFC)
        val surface = Color(0xFF111827)
        val onSurface = Color(0xFFF8FAFC)
        val surfaceVariant = Color(0xFF1E293B)
        val onSurfaceVariant = Color(0xFFCBD5E1)
        val outline = Color(0xFF94A3B8)
        val outlineVariant = Color(0xFF475569)
    }
}
