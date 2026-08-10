package li.songe.gkd.sdp.ui.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DesignTokenContractTest {
    @Test
    fun lightColorRolesMatchTheProductPalette() {
        assertEquals(Color(0xFF4F46E5), ColorTokens.Light.primary)
        assertEquals(Color(0xFFFFFFFF), ColorTokens.Light.onPrimary)
        assertEquals(Color(0xFFE0E7FF), ColorTokens.Light.primaryContainer)
        assertEquals(Color(0xFF0F766E), ColorTokens.Light.secondary)
        assertEquals(Color(0xFFCCFBF1), ColorTokens.Light.secondaryContainer)
        assertEquals(Color(0xFFB45309), ColorTokens.Light.tertiary)
        assertEquals(Color(0xFFB3261E), ColorTokens.Light.error)
        assertEquals(Color(0xFFF8FAFC), ColorTokens.Light.background)
        assertEquals(Color(0xFFFFFFFF), ColorTokens.Light.surface)
        assertEquals(Color(0xFFF1F5F9), ColorTokens.Light.surfaceVariant)
        assertEquals(Color(0xFF0F172A), ColorTokens.Light.onSurface)
        assertEquals(Color(0xFF475569), ColorTokens.Light.onSurfaceVariant)
        assertEquals(Color(0xFF64748B), ColorTokens.Light.outline)
    }

    @Test
    fun darkColorRolesMatchTheProductPalette() {
        assertEquals(Color(0xFFA5B4FC), ColorTokens.Dark.primary)
        assertEquals(Color(0xFF1E1B4B), ColorTokens.Dark.onPrimary)
        assertEquals(Color(0xFF312E81), ColorTokens.Dark.primaryContainer)
        assertEquals(Color(0xFF5EEAD4), ColorTokens.Dark.secondary)
        assertEquals(Color(0xFF134E4A), ColorTokens.Dark.secondaryContainer)
        assertEquals(Color(0xFFFCD34D), ColorTokens.Dark.tertiary)
        assertEquals(Color(0xFFFFB4AB), ColorTokens.Dark.error)
        assertEquals(Color(0xFF0F172A), ColorTokens.Dark.background)
        assertEquals(Color(0xFF111827), ColorTokens.Dark.surface)
        assertEquals(Color(0xFF1E293B), ColorTokens.Dark.surfaceVariant)
        assertEquals(Color(0xFFF8FAFC), ColorTokens.Dark.onSurface)
        assertEquals(Color(0xFFCBD5E1), ColorTokens.Dark.onSurfaceVariant)
        assertEquals(Color(0xFF94A3B8), ColorTokens.Dark.outline)
    }

    @Test
    fun onRolesAndContainersDifferFromTheirBases() {
        assertNotEquals(ColorTokens.Light.primary, ColorTokens.Light.onPrimary)
        assertNotEquals(ColorTokens.Light.primary, ColorTokens.Light.onPrimaryContainer)
        assertNotEquals(ColorTokens.Light.secondary, ColorTokens.Light.onSecondaryContainer)
        assertNotEquals(ColorTokens.Light.surface, ColorTokens.Light.onSurface)
        assertNotEquals(ColorTokens.Light.surfaceVariant, ColorTokens.Light.onSurfaceVariant)
        assertNotEquals(ColorTokens.Dark.primary, ColorTokens.Dark.onPrimary)
        assertNotEquals(ColorTokens.Dark.primary, ColorTokens.Dark.onPrimaryContainer)
        assertNotEquals(ColorTokens.Dark.secondary, ColorTokens.Dark.onSecondaryContainer)
        assertNotEquals(ColorTokens.Dark.surface, ColorTokens.Dark.onSurface)
        assertNotEquals(ColorTokens.Dark.surfaceVariant, ColorTokens.Dark.onSurfaceVariant)
    }

    @Test
    fun spacingUsesTheFixedScale() {
        assertEquals(4.dp, DimensionTokens.SpacingXs)
        assertEquals(8.dp, DimensionTokens.SpacingSm)
        assertEquals(12.dp, DimensionTokens.SpacingMd)
        assertEquals(16.dp, DimensionTokens.SpacingBase)
        assertEquals(20.dp, DimensionTokens.SpacingLg)
        assertEquals(24.dp, DimensionTokens.SpacingXl)
        assertEquals(32.dp, DimensionTokens.SpacingXxl)
        assertEquals(40.dp, DimensionTokens.SpacingXxxl)
        assertEquals(48.dp, DimensionTokens.SpacingHuge)
    }

    @Test
    fun pagePaddingAndWidthsMatchTheBreakpoints() {
        assertEquals(16.dp, ResponsiveTokens.pageHorizontalPadding(360))
        assertEquals(24.dp, ResponsiveTokens.pageHorizontalPadding(600))
        assertEquals(24.dp, ResponsiveTokens.pageHorizontalPadding(700))
        assertEquals(32.dp, ResponsiveTokens.pageHorizontalPadding(840))
        assertEquals(32.dp, ResponsiveTokens.pageHorizontalPadding(1000))
        assertEquals(720.dp, ResponsiveTokens.contentMaxWidth(isForm = true))
        assertEquals(960.dp, ResponsiveTokens.contentMaxWidth(isForm = false))
    }

    @Test
    fun touchTargetsAndIconSizesAreFixed() {
        assertEquals(48.dp, DimensionTokens.MinTouchTarget)
        assertEquals(20.dp, DimensionTokens.IconSizeSmall)
        assertEquals(24.dp, DimensionTokens.IconSizeDefault)
        assertEquals(64.dp, DimensionTokens.TopBarHeight)
    }

    @Test
    fun cornerRadiiUseTheFixedScale() {
        assertEquals(8.dp, ShapeTokens.RadiusSmall)
        assertEquals(12.dp, ShapeTokens.RadiusMedium)
        assertEquals(16.dp, ShapeTokens.RadiusLarge)
        assertEquals(24.dp, ShapeTokens.RadiusXLarge)
    }

    @Test
    fun motionDurationsAndScaleFollowTheSpec() {
        assertEquals(120, MotionTokens.DurationMicroMs)
        assertEquals(180, MotionTokens.DurationPageThemeMs)
        assertEquals(240, MotionTokens.DurationEmphasisMs)
        assertEquals(MotionTokens.DurationMicroMs, MotionTokens.scaledDurationMs(120, 1f))
        assertEquals(0, MotionTokens.scaledDurationMs(180, 0f))
        assertEquals(90, MotionTokens.scaledDurationMs(180, 0.5f))
        assertEquals(0, MotionTokens.scaledDurationMs(240, -1f))
        assertEquals(240, MotionTokens.scaledDurationMs(240, 2f))
    }

    @Test
    fun typeScaleUsesTheFixedPairs() {
        assertEquals(32.sp, TypographyTokens.DisplaySmall.fontSize)
        assertEquals(40.sp, TypographyTokens.DisplaySmall.lineHeight)
        assertEquals(24.sp, TypographyTokens.HeadlineSmall.fontSize)
        assertEquals(32.sp, TypographyTokens.HeadlineSmall.lineHeight)
        assertEquals(20.sp, TypographyTokens.TitleLarge.fontSize)
        assertEquals(28.sp, TypographyTokens.TitleLarge.lineHeight)
        assertEquals(16.sp, TypographyTokens.TitleMedium.fontSize)
        assertEquals(24.sp, TypographyTokens.TitleMedium.lineHeight)
        assertEquals(16.sp, TypographyTokens.BodyLarge.fontSize)
        assertEquals(24.sp, TypographyTokens.BodyLarge.lineHeight)
        assertEquals(14.sp, TypographyTokens.BodyMedium.fontSize)
        assertEquals(20.sp, TypographyTokens.BodyMedium.lineHeight)
        assertEquals(14.sp, TypographyTokens.LabelLarge.fontSize)
        assertEquals(20.sp, TypographyTokens.LabelLarge.lineHeight)
    }
}
