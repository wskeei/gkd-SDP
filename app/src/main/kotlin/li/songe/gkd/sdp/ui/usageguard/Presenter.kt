@file:JvmName("UsageGuardPresenter")

package li.songe.gkd.sdp.ui

import androidx.compose.ui.geometry.Rect
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy

internal enum class UsageGuardActionScope { Selected, Whitelist, Override }

internal data class UsageGuardAppAction(
    val appId: String,
    val scope: UsageGuardActionScope,
)

internal data class UsageGuardSettingsRenderState(
    val state: UsageGuardUiState,
    val onBack: () -> Unit,
    val onDispatch: (UsageGuardAction) -> Unit,
    val onDeleteCustomTag: (UsageGuardTag) -> Unit,
    val onSaveSelectedTargets: (List<String>) -> Unit,
    val onSaveWhitelist: (List<String>) -> Unit,
    val onSaveGrantModeOverrideApps: (List<String>) -> Unit,
    val onClearAppGrantModeOverride: (String) -> Unit,
    val onSelectDate: (java.time.LocalDate) -> Unit,
    val onOpenSelectedPicker: () -> Unit,
    val onDismissSelectedPicker: () -> Unit,
    val onOpenWhitelistPicker: () -> Unit,
    val onDismissWhitelistPicker: () -> Unit,
    val onOpenOverridePicker: () -> Unit,
    val onDismissOverridePicker: () -> Unit,
    val onOpenAppAction: (String, UsageGuardActionScope) -> Unit,
    val onCloseAppAction: () -> Unit,
    val onStrictBoardBounds: (Rect?) -> Unit,
    val onResumableBoardBounds: (Rect?) -> Unit,
    val onDraggingAppId: (String?) -> Unit,
)

internal fun dispatchUsageGuardAction(
    updateEnabled: (Boolean) -> Unit,
    updateScopeMode: (Int) -> Unit,
    updateDefaultGrantMode: (Int) -> Unit,
    updateMinReasonLength: (Int) -> Unit,
    updateDurationOptions: (List<Int>) -> Unit,
    moveSelectedAppToGrantMode: (String, Int) -> Unit,
    addCustomTag: (String) -> Unit,
    updateSelectedHistoryDate: (java.time.LocalDate) -> Unit,
    action: UsageGuardAction,
) {
    when (action) {
        is UsageGuardAction.UpdateEnabled -> updateEnabled(action.enabled)
        is UsageGuardAction.UpdateScopeMode -> updateScopeMode(action.scopeMode)
        is UsageGuardAction.UpdateDefaultGrantMode -> updateDefaultGrantMode(action.grantMode)
        is UsageGuardAction.UpdateMinReasonLength -> updateMinReasonLength(action.minLength)
        is UsageGuardAction.UpdateDurationOptions -> updateDurationOptions(action.options)
        is UsageGuardAction.MoveSelectedAppToGrantMode ->
            moveSelectedAppToGrantMode(action.appId, action.grantMode)
        is UsageGuardAction.AddCustomTag -> addCustomTag(action.name)
        is UsageGuardAction.UpdateSelectedHistoryDate ->
            updateSelectedHistoryDate(java.time.LocalDate.ofEpochDay(action.dateEpochDay))
        else -> Unit
    }
}

internal fun UsageGuardUiState.reduce(action: UsageGuardAction): UsageGuardUiState = when (action) {
    is UsageGuardAction.UpdateSelectedHistoryDate -> copy(
        selectedHistoryDateEpochDay = action.dateEpochDay,
    )
    UsageGuardAction.ShowSelectedPicker -> copy(showSelectedPicker = true)
    UsageGuardAction.HideSelectedPicker -> copy(showSelectedPicker = false)
    UsageGuardAction.ShowWhitelistPicker -> copy(showWhitelistPicker = true)
    UsageGuardAction.HideWhitelistPicker -> copy(showWhitelistPicker = false)
    UsageGuardAction.ShowOverridePicker -> copy(showOverridePicker = true)
    UsageGuardAction.HideOverridePicker -> copy(showOverridePicker = false)
    is UsageGuardAction.OpenAppAction -> copy(
        appAction = UsageGuardAppAction(action.appId, action.scope),
    )
    UsageGuardAction.CloseAppAction -> copy(appAction = null)
    is UsageGuardAction.UpdateStrictBoardBounds -> copy(strictBoardBounds = action.bounds)
    is UsageGuardAction.UpdateResumableBoardBounds -> copy(resumableBoardBounds = action.bounds)
    is UsageGuardAction.UpdateDraggingAppId -> copy(draggingAppId = action.appId)
    else -> this
}

internal fun UsageGuardVm.dispatch(action: UsageGuardAction) {
    dispatchUsageGuardAction(
        updateEnabled = ::updateEnabled,
        updateScopeMode = ::updateScopeMode,
        updateDefaultGrantMode = ::updateDefaultGrantMode,
        updateMinReasonLength = ::updateMinReasonLength,
        updateDurationOptions = ::updateDurationOptions,
        moveSelectedAppToGrantMode = ::moveSelectedAppToGrantMode,
        addCustomTag = ::addCustomTag,
        updateSelectedHistoryDate = ::updateSelectedHistoryDate,
        action = action,
    )
}

internal fun UsageGuardRecord.endStateTextRes(): Int {
    return when (endReason) {
        UsageGuardRecord.END_REASON_ACTIVE -> R.string.usage_guard_end_active
        UsageGuardRecord.END_REASON_EXPIRED -> R.string.usage_guard_end_expired
        UsageGuardRecord.END_REASON_LEFT_APP -> R.string.usage_guard_end_left_app
        UsageGuardRecord.END_REASON_REPLACED -> R.string.usage_guard_end_replaced
        UsageGuardRecord.END_REASON_HOME_BUTTON -> R.string.usage_guard_end_home_button
        UsageGuardRecord.END_REASON_USER_TERMINATED -> R.string.usage_guard_end_user_terminated
        else -> R.string.usage_guard_end_unknown
    }
}
