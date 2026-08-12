@file:JvmName("AdvancedPresenter0")

package li.songe.gkd.sdp.ui

fun AdvancedUiState.reduce(action: AdvancedAction): AdvancedUiState = when (action) {
    AdvancedAction.OpenEditPortDialog -> copy(showEditPortDlg = true)
    AdvancedAction.CloseEditPortDialog -> copy(showEditPortDlg = false)
    AdvancedAction.OpenShizukuState -> copy(showShizukuState = true)
    AdvancedAction.CloseShizukuState -> copy(showShizukuState = false)
    AdvancedAction.OpenCaptureScreenshotDialog -> copy(showCaptureScreenshotDlg = true)
    AdvancedAction.CloseCaptureScreenshotDialog -> copy(showCaptureScreenshotDlg = false)
    AdvancedAction.ToggleHttpSetting -> copy(showHttpSettingDlg = !showHttpSettingDlg)
}

fun AdvancedVm.present(): AdvancedUiState = AdvancedUiState(
    showEditPortDlg = showEditPortDlgFlow.value,
    showShizukuState = showShizukuStateFlow.value,
    showCaptureScreenshotDlg = showCaptureScreenshotDlgFlow.value,
)
