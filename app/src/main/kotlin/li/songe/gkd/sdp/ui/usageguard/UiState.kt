@file:JvmName("UsageGuardUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

import androidx.compose.ui.geometry.Rect
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import java.time.format.DateTimeFormatter

internal val usageGuardDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
internal val usageGuardTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal sealed interface UsageGuardAction {
    data class UpdateEnabled(val enabled: Boolean) : UsageGuardAction
    data class UpdateScopeMode(val scopeMode: Int) : UsageGuardAction
    data class UpdateDefaultGrantMode(val grantMode: Int) : UsageGuardAction
    data class UpdateMinReasonLength(val minLength: Int) : UsageGuardAction
    data class UpdateDurationOptions(val options: List<Int>) : UsageGuardAction
    data class MoveSelectedAppToGrantMode(val appId: String, val grantMode: Int) : UsageGuardAction
    data class AddCustomTag(val name: String) : UsageGuardAction
    data class UpdateSelectedHistoryDate(val dateEpochDay: Long) : UsageGuardAction
    data object ShowSelectedPicker : UsageGuardAction
    data object HideSelectedPicker : UsageGuardAction
    data object ShowWhitelistPicker : UsageGuardAction
    data object HideWhitelistPicker : UsageGuardAction
    data object ShowOverridePicker : UsageGuardAction
    data object HideOverridePicker : UsageGuardAction
    data class OpenAppAction(val appId: String, val scope: UsageGuardActionScope) : UsageGuardAction
    data object CloseAppAction : UsageGuardAction
    data class UpdateStrictBoardBounds(val bounds: Rect?) : UsageGuardAction
    data class UpdateResumableBoardBounds(val bounds: Rect?) : UsageGuardAction
    data class UpdateDraggingAppId(val appId: String?) : UsageGuardAction
}

internal data class UsageGuardUiState(
    val settings: SettingsStore = SettingsStore(
        actionToast = "",
        customNotifTitle = "",
        updateChannel = 0,
    ),
    val appProfiles: List<UsageGuardAppProfile> = emptyList(),
    val tags: List<UsageGuardTag> = emptyList(),
    val history: List<UsageGuardRecord> = emptyList(),
    val groupedApps: UsageGuardUiStatePolicy.SelectedAppSections =
        UsageGuardUiStatePolicy.SelectedAppSections(emptyList(), emptyList()),
    val durationOptions: List<Int> = UsageGuardUiStatePolicy.defaultDurationOptions,
    val appInfoMap: Map<String, AppInfo> = emptyMap(),
    val selectedTargetApps: List<String> = emptyList(),
    val whitelistApps: List<String> = emptyList(),
    val globalOverrideApps: List<String> = emptyList(),
    val profileMap: Map<String, UsageGuardAppProfile> = emptyMap(),
    val selectedHistoryDateEpochDay: Long = 0L,
    val showSelectedPicker: Boolean = false,
    val showWhitelistPicker: Boolean = false,
    val showOverridePicker: Boolean = false,
    val appAction: UsageGuardAppAction? = null,
    val strictBoardBounds: Rect? = null,
    val resumableBoardBounds: Rect? = null,
    val draggingAppId: String? = null,
)
