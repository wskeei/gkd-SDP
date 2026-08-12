@file:JvmName("FocusLockScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.a11y.AppBlockerEngine
import li.songe.gkd.sdp.a11y.FocusModeEngine
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.a11y.sdpRuntimeFeatureCoordinator
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.LocalMainViewModel

internal class FocusLockDialogState {
    var showLockSheet by mutableStateOf(false)
    var showPauseSheet by mutableStateOf(false)
    var showPermissionDialog by mutableStateOf(false)
    var showAccessibilityGuardDialog by mutableStateOf(false)
    var showAccessibilityGuardDisableDialog by mutableStateOf(false)
    var showAutoReenableDialog by mutableStateOf(false)
    var currentLockTarget by mutableStateOf<LockTarget?>(null)
    var currentPauseTarget by mutableStateOf<PauseTarget?>(null)
}

@Composable
internal fun rememberFocusLockDialogState(): FocusLockDialogState =
    remember { FocusLockDialogState() }

@Composable
fun FocusLockPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalContext.current
    val vm = viewModel<FocusLockVm>()
    val subStates by vm.subStatesFlow.collectAsStateWithLifecycle()
    val expandedSubs by vm.expandedSubs.collectAsStateWithLifecycle()
    val expandedApps by vm.expandedApps.collectAsStateWithLifecycle()
    val settings by storeFlow.collectAsStateWithLifecycle()
    val urlBlockerEnabled by UrlBlockerEngine.enabledFlow.collectAsStateWithLifecycle()
    val focusModeActive by FocusModeEngine.isActiveFlow.collectAsStateWithLifecycle()
    val appBlockerRules by AppBlockerEngine.enabledRulesFlow.collectAsStateWithLifecycle()
    val appBlockerGroups by AppBlockerEngine.enabledGroupsFlow.collectAsStateWithLifecycle()
    val runtimeStatus by sdpRuntimeFeatureCoordinator.statusFlow.collectAsStateWithLifecycle()
    val overlayPermission by canDrawOverlaysState.stateFlow.collectAsStateWithLifecycle()
    val state = vm.present(subStates, expandedSubs, expandedApps)
    val autoReenableUiState = FocusLockVm.evaluateAutoReenableUiState(
        intervalMinutes = settings.autoReenableIntervalMinutes,
        lastChangedAt = settings.autoReenableIntervalChangedAt,
        scheduledNextEnforceAt = settings.autoReenableNextEnforceAt,
        dailyDisableLimit = settings.autoReenableDailyDisableLimit,
        dailyDisableUsed = settings.autoReenableDailyDisableUsed,
        dailyDisableDayStartAt = settings.autoReenableDailyDisableDayStartAt,
        now = System.currentTimeMillis(),
    )
    val dialogState = rememberFocusLockDialogState()
    val lockSheetState = rememberModalBottomSheetState()
    val pauseSheetState = rememberModalBottomSheetState()

    FocusLockPageSections(
        state = state,
        settings = settings,
        runtimeStatus = runtimeStatus,
        overlayPermission = overlayPermission,
        autoReenableUiState = autoReenableUiState,
        urlBlockerEnabled = urlBlockerEnabled,
        focusModeActive = focusModeActive,
        appBlockerRules = appBlockerRules,
        appBlockerGroups = appBlockerGroups,
        dialogState = dialogState,
        lockSheetState = lockSheetState,
        pauseSheetState = pauseSheetState,
        onBack = { mainVm.popPage() },
        onOpenFocusMode = { mainVm.navigatePage(FocusModeRoute) },
        onOpenUrlBlocker = { mainVm.navigatePage(UrlBlockRoute) },
        onOpenAppBlocker = { mainVm.navigatePage(AppBlockerRoute) },
        onOpenUsageGuard = { mainVm.navigatePage(UsageGuardRoute) },
        onOpenAppInstallMonitor = { mainVm.navigatePage(AppInstallMonitorRoute) },
        onAccessibilityGuardCheckedChange = { requestedEnabled ->
            if (requestedEnabled) {
                dialogState.showAccessibilityGuardDialog = true
            } else {
                dialogState.showAccessibilityGuardDisableDialog = true
            }
        },
        onAutoReenableClick = { dialogState.showAutoReenableDialog = true },
        onToggleExpandSubs = { vm.toggleExpandSubs(it) },
        onToggleExpandApp = { vm.toggleExpandApp(it) },
        onLockClick = { target ->
            dialogState.currentLockTarget = target
            dialogState.showLockSheet = true
        },
        onPauseClick = { target ->
            if (!android.provider.Settings.canDrawOverlays(context)) {
                dialogState.showPermissionDialog = true
            } else {
                dialogState.currentPauseTarget = target
                dialogState.showPauseSheet = true
            }
        },
        onLockTarget = { target, request ->
            vm.selectedDuration = request.durationMinutes
            vm.isCustomDuration = request.isCustom
            vm.customDaysText = request.daysText
            vm.customHoursText = request.hoursText
            vm.lockTarget(target.type, target.subsId, target.appId, target.groupKey)
        },
        onUpdateInterceptConfig = { target, enabled, cooldown, message ->
            if (target.groupKey != null) {
                vm.updateInterceptConfig(
                    target.subsId,
                    target.appId,
                    target.groupKey,
                    enabled,
                    cooldown,
                    message,
                )
            } else {
                vm.batchUpdateInterceptConfig(
                    target.subsId,
                    target.appId,
                    enabled,
                    cooldown,
                    message,
                )
            }
        },
        onSaveAutoReenable = { intervalMinutes, dailyLimit ->
            if (intervalMinutes != null) {
                vm.updateAutoReenableInterval(intervalMinutes)
            }
            if (dailyLimit != null) {
                vm.updateAutoReenableDailyDisableLimit(dailyLimit)
            }
        },
        onNavigateAuthA11y = { mainVm.navigatePage(AuthA11yRoute) },
    )
}
