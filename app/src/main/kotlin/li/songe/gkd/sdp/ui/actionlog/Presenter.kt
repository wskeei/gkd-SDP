@file:JvmName("ActionLogPresenter0")

package li.songe.gkd.sdp.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.db.DbSet

internal fun applyActionLogAction(
    state: ActionLogUiState,
    action: ActionLogAction,
): ActionLogUiState = when (action) {
    is ActionLogAction.SelectTab -> state.copy(selectedTabIndex = action.index)
    is ActionLogAction.OpenDetail -> state
    ActionLogAction.DismissDetail -> state.copy(detail = null)
}

internal fun dispatchActionLog(
    selectedTabIndex: MutableStateFlow<Int>,
    detail: MutableStateFlow<ActionLog?>,
    scope: CoroutineScope,
    loadDetail: suspend (Int) -> ActionLog?,
    action: ActionLogAction,
) {
    selectedTabIndex.value = applyActionLogAction(
        state = ActionLogUiState(selectedTabIndex.value),
        action = action,
    ).selectedTabIndex
    when (action) {
        is ActionLogAction.OpenDetail -> {
            val actionLogId = action.actionLogId
            scope.launch { detail.value = loadDetail(actionLogId) }
        }
        ActionLogAction.DismissDetail -> detail.value = null
        is ActionLogAction.SelectTab -> Unit
    }
}

internal fun ActionLogVm.dispatch(action: ActionLogAction) {
    dispatchActionLog(
        selectedTabIndex = selectedTabIndex,
        detail = showActionLogFlow,
        scope = viewModelScope,
        loadDetail = DbSet.actionLogDao::queryById,
        action = action,
    )
}
