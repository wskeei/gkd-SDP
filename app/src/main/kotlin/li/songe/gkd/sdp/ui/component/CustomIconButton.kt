package li.songe.gkd.sdp.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import li.songe.gkd.sdp.ui.style.DimensionTokens

@Composable
fun PerfCustomIconButton(
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    onClickLabel: String? = null,
    @DrawableRes id: Int,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) = TooltipIconButtonBox(
    contentDescription = contentDescription,
) {
    AccessibleIconButton(
        contentDescription = contentDescription ?: "",
        onClickLabel = onClickLabel ?: "",
        onClick = onClick,
        touchTarget = size.coerceAtLeast(DimensionTokens.MinTouchTarget),
        iconSize = iconSize.coerceAtMost(size.coerceAtLeast(DimensionTokens.MinTouchTarget)),
    ) {
        PerfIcon(
            modifier = Modifier.size(iconSize),
            id = id,
            contentDescription = null,
            tint = tint,
        )
    }
}
