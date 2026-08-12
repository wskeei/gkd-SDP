package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.util.throttle

@Composable
fun PerfSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    key: Any? = null,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.runtime.key(key) {
    val enabledState = stringResource(R.string.common_enabled_state)
    val disabledState = stringResource(R.string.common_disabled_state)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { throttle(it) },
        modifier = modifier.semantics {
            stateDescription = if (checked) {
                enabledState
            } else {
                disabledState
            }
        },
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}
