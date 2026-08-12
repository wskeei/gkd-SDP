@file:JvmName("UsageGuardScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.appInfoMapFlow
import java.time.LocalDate

@Composable
fun UsageGuardPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UsageGuardVm>()
    val settings by storeFlow.collectAsStateWithLifecycle()
    val appProfiles by vm.appProfilesFlow.collectAsStateWithLifecycle()
    val tags by vm.tagsFlow.collectAsStateWithLifecycle()
    val history by vm.historyFlow.collectAsStateWithLifecycle()
    val groupedApps by vm.selectedAppSectionsFlow.collectAsStateWithLifecycle()
    val durationOptions by vm.durationOptionsFlow.collectAsStateWithLifecycle()
    val appInfoMap by appInfoMapFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showSelectedPicker by remember { mutableStateOf(false) }
    var showWhitelistPicker by remember { mutableStateOf(false) }
    var showOverridePicker by remember { mutableStateOf(false) }
    var appAction by remember { mutableStateOf<UsageGuardAppAction?>(null) }
    var strictBoardBounds by remember { mutableStateOf<Rect?>(null) }
    var resumableBoardBounds by remember { mutableStateOf<Rect?>(null) }
    var draggingAppId by remember { mutableStateOf<String?>(null) }

    val selectedTargetApps = remember(appProfiles) {
        appProfiles.filter { it.selectedTarget }.map { it.appId }
    }
    val whitelistApps = remember(appProfiles) {
        appProfiles.filter { it.globalWhitelist }.map { it.appId }
    }
    val profileMap = remember(appProfiles) {
        appProfiles.associateBy { it.appId }
    }
    val globalOverrideApps = remember(appProfiles, settings.usageGuardDefaultGrantMode) {
        appProfiles.filter {
            !it.globalWhitelist && it.grantMode != settings.usageGuardDefaultGrantMode
        }.map { it.appId }
    }

    val uiState = UsageGuardUiState(
        settings = settings,
        appProfiles = appProfiles,
        tags = tags,
        history = history,
        groupedApps = groupedApps,
        durationOptions = durationOptions,
        appInfoMap = appInfoMap,
        selectedTargetApps = selectedTargetApps,
        whitelistApps = whitelistApps,
        globalOverrideApps = globalOverrideApps,
        profileMap = profileMap,
        selectedHistoryDateEpochDay = selectedDate.toEpochDay(),
        showSelectedPicker = showSelectedPicker,
        showWhitelistPicker = showWhitelistPicker,
        showOverridePicker = showOverridePicker,
        appAction = appAction,
        strictBoardBounds = strictBoardBounds,
        resumableBoardBounds = resumableBoardBounds,
        draggingAppId = draggingAppId,
    )

    LaunchedEffect(selectedDate) {
        vm.updateSelectedHistoryDate(selectedDate)
    }

    val renderState = UsageGuardSettingsRenderState(
        state = uiState,
        onBack = { mainVm.popPage() },
        onDispatch = vm::dispatch,
        onDeleteCustomTag = vm::deleteCustomTag,
        onSaveSelectedTargets = vm::saveSelectedTargets,
        onSaveWhitelist = vm::saveWhitelist,
        onSaveGrantModeOverrideApps = vm::saveGrantModeOverrideApps,
        onClearAppGrantModeOverride = vm::clearAppGrantModeOverride,
        onSelectDate = { selectedDate = it },
        onOpenSelectedPicker = { showSelectedPicker = true },
        onDismissSelectedPicker = { showSelectedPicker = false },
        onOpenWhitelistPicker = { showWhitelistPicker = true },
        onDismissWhitelistPicker = { showWhitelistPicker = false },
        onOpenOverridePicker = { showOverridePicker = true },
        onDismissOverridePicker = { showOverridePicker = false },
        onOpenAppAction = { appId, scope -> appAction = UsageGuardAppAction(appId, scope) },
        onCloseAppAction = { appAction = null },
        onStrictBoardBounds = { strictBoardBounds = it },
        onResumableBoardBounds = { resumableBoardBounds = it },
        onDraggingAppId = { draggingAppId = it },
    )

    UsageGuardSettingsList(renderState, context)
}
