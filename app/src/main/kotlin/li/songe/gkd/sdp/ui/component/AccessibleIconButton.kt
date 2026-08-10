package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Dp
import li.songe.gkd.sdp.ui.style.DimensionTokens

/**
 * Icon button with a minimum 48dp touch target and a single accessible name.
 *
 * [contentDescription] is the spoken name and [onClickLabel] the spoken
 * action label; both are mandatory for clickable icons. The inner icon is
 * decorative: its own description is cleared so TalkBack reads one name.
 */
@Composable
fun AccessibleIconButton(
    contentDescription: String,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = DimensionTokens.IconSizeDefault,
    touchTarget: Dp = DimensionTokens.MinTouchTarget,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.size(touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(touchTarget)
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    onClick(label = onClickLabel, action = null)
                },
            enabled = enabled,
            colors = colors,
        ) {
            Box(
                modifier = Modifier.size(iconSize),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
