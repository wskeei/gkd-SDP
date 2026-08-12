@file:JvmName("ActionLogUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

import li.songe.gkd.sdp.data.ActionLog

@Immutable
internal data class ActionLogUiState(
    val selectedTabIndex: Int = 0,
    val detail: ActionLog? = null,
)

sealed interface ActionLogAction {
    data class SelectTab(val index: Int) : ActionLogAction
    data class OpenDetail(val actionLogId: Int) : ActionLogAction
    data object DismissDetail : ActionLogAction
}
