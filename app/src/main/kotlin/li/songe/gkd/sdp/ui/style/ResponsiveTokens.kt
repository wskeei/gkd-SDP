package li.songe.gkd.sdp.ui.style

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Window-width dependent layout rules. */
object ResponsiveTokens {
    /** Compact windows are narrower than this. */
    const val CompactMaxWidthDp = 600

    /** Medium windows are narrower than this. */
    const val MediumMaxWidthDp = 840

    /** Horizontal page padding for the current window width. */
    fun pageHorizontalPadding(windowWidthDp: Int): Dp = when {
        windowWidthDp < CompactMaxWidthDp -> DimensionTokens.PagePaddingCompact
        windowWidthDp < MediumMaxWidthDp -> DimensionTokens.PagePaddingMedium
        else -> DimensionTokens.PagePaddingExpanded
    }

    /** Content column width: forms are narrower than lists. */
    fun contentMaxWidth(isForm: Boolean): Dp =
        if (isForm) DimensionTokens.FormMaxWidth else DimensionTokens.ListMaxWidth
}
