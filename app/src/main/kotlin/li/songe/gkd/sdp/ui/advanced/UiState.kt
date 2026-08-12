@file:JvmName("AdvancedUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

import li.songe.gkd.sdp.store.SettingsStore

@Immutable
data class AdvancedUiState(
    val showEditPortDlg: Boolean = false,
    val showShizukuState: Boolean = false,
    val showCaptureScreenshotDlg: Boolean = false,
    val showHttpSettingDlg: Boolean = false,
    val store: SettingsStore = SettingsStore(),
)

sealed interface AdvancedAction {
    data object OpenEditPortDialog : AdvancedAction
    data object CloseEditPortDialog : AdvancedAction
    data object OpenShizukuState : AdvancedAction
    data object CloseShizukuState : AdvancedAction
    data object OpenCaptureScreenshotDialog : AdvancedAction
    data object CloseCaptureScreenshotDialog : AdvancedAction
    data object ToggleHttpSetting : AdvancedAction
}
