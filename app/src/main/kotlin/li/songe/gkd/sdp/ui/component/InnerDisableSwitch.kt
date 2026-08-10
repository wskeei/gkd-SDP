package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.R

@Composable
fun InnerDisableSwitch(
    modifier: Modifier = Modifier,
    valid: Boolean = true,
    isSelectedMode: Boolean = false,
) {
    val mainVm = LocalMainViewModel.current
    val onClick = {
        if (valid) {
            mainVm.dialogFlow.updateDialogOptions(
                title = li.songe.gkd.sdp.app.getString(R.string.s_f10b25a414),
                text = li.songe.gkd.sdp.app.getString(R.string.s_42e1b49044),
            )
        } else {
            mainVm.dialogFlow.updateDialogOptions(
                title = li.songe.gkd.sdp.app.getString(R.string.s_5c57086db5),
                text = li.songe.gkd.sdp.app.getString(R.string.s_ceea8ce8e5),
            )
        }
    }
    PerfSwitch(
        checked = false,
        enabled = false,
        onCheckedChange = null,
        modifier = modifier.semantics {
            stateDescription = "已禁用"
        }
            .minimumInteractiveComponentSize().run {
                if (isSelectedMode) {
                    this
                } else {
                    clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Switch,
                        onClick = throttle(onClick),
                        onClickLabel = li.songe.gkd.sdp.app.getString(R.string.s_5e844385f4),
                    )
                }
            }
    )
}
