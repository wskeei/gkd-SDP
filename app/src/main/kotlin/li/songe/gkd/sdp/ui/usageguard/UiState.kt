@file:JvmName("UsageGuardUiState")

package li.songe.gkd.sdp.ui

import java.time.format.DateTimeFormatter
import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Rect
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy

internal val usageGuardDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
internal val usageGuardTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal enum class UsageGuardActionScope { Selected, Whitelist, Override }

internal data class UsageGuardAppAction(
    val appId: String,
    val scope: UsageGuardActionScope,
)

internal data class UsageGuardSettingsRenderState(
    val mainVm: MainViewModel,
    val vm: UsageGuardVm,
    val settings: SettingsStore,
    val tags: List<UsageGuardTag>,
    val history: List<UsageGuardRecord>,
    val groupedApps: UsageGuardUiStatePolicy.SelectedAppSections,
    val durationOptions: List<Int>,
    val appInfoMap: Map<String, AppInfo>,
    val context: Context,
    val selectedTargetApps: List<String>,
    val whitelistApps: List<String>,
    val globalOverrideApps: List<String>,
    val profileMap: Map<String, UsageGuardAppProfile>,
    val minReasonLengthText: MutableState<String>,
    val durationOptionTexts: MutableState<List<String>>,
    val customTagText: MutableState<String>,
    val selectedDate: MutableState<java.time.LocalDate>,
    val showSelectedPicker: MutableState<Boolean>,
    val showWhitelistPicker: MutableState<Boolean>,
    val showOverridePicker: MutableState<Boolean>,
    val appAction: MutableState<UsageGuardAppAction?>,
    val strictBoardBounds: MutableState<Rect?>,
    val resumableBoardBounds: MutableState<Rect?>,
    val draggingAppId: MutableState<String?>,
)
