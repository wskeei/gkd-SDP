package li.songe.gkd.sdp.ui.style

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.style.DimensionTokens

val DimensionTokens.SpacingBase = DimensionTokens.SpacingBase
val DimensionTokens.SpacingMd = DimensionTokens.SpacingMd
val EmptyHeight = 80.dp
val DimensionTokens.SpacingMd = DimensionTokens.SpacingMd

fun Modifier.itemPadding() = this.padding(DimensionTokens.SpacingBase, DimensionTokens.SpacingMd)

fun Modifier.titleItemPadding(showTop: Boolean = true) = this.padding(
    DimensionTokens.SpacingBase,
    if (showTop) DimensionTokens.SpacingMd + DimensionTokens.SpacingMd / 2 else 0.dp,
    DimensionTokens.SpacingBase,
    DimensionTokens.SpacingMd - DimensionTokens.SpacingMd / 2
)

fun Modifier.appItemPadding() = this.padding(DimensionTokens.SpacingBase, DimensionTokens.SpacingMd)

fun Modifier.scaffoldPadding(values: PaddingValues): Modifier {
    return padding(
        top = values.calculateTopPadding(),
        // 被 LazyXXX 使用时, 移除 bottom padding, 否则 底部导航栏 无法实现透明背景
    )
}

@Composable
fun Modifier.iconTextSize(
    textStyle: TextStyle = LocalTextStyle.current,
    square: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val lineHeightDp = density.run { textStyle.lineHeight.toDp() }
    val fontSizeDp = density.run { textStyle.fontSize.toDp() }
    return if (square) {
        padding((lineHeightDp - fontSizeDp) / 2).size(fontSizeDp)
    } else {
        size(height = lineHeightDp, width = fontSizeDp)
    }
}
