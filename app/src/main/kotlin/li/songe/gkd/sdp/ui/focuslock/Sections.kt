@file:JvmName("FocusLockSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.a11y.AppBlockerEngine
import li.songe.gkd.sdp.a11y.FocusModeEngine
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding

@Composable
fun FocusLockPageSections() {
    val vm = viewModel<FocusLockVm>()
    val subStates by vm.subStatesFlow.collectAsStateWithLifecycle()
    val expandedSubs by vm.expandedSubs.collectAsStateWithLifecycle()
    val expandedApps by vm.expandedApps.collectAsStateWithLifecycle()
    val settings by storeFlow.collectAsStateWithLifecycle()
    val lockSheetState = rememberModalBottomSheetState()
    val pauseSheetState = rememberModalBottomSheetState()
    val dialogState = rememberFocusLockDialogState()

    FocusLockPageScaffold(
        vm = vm,
        subStates = subStates,
        expandedSubs = expandedSubs,
        expandedApps = expandedApps,
        settings = settings,
        dialogState = dialogState,
        lockSheetState = lockSheetState,
        pauseSheetState = pauseSheetState,
    )
}

@Composable
private fun FocusLockPageScaffold(
    vm: FocusLockVm,
    subStates: List<SubscriptionState>,
    expandedSubs: Set<Long>,
    expandedApps: Set<String>,
    settings: SettingsStore,
    dialogState: FocusLockDialogState,
    lockSheetState: androidx.compose.material3.SheetState,
    pauseSheetState: androidx.compose.material3.SheetState,
) {
    val mainVm = LocalMainViewModel.current
    val context = LocalContext.current
    val urlBlockerEnabled by UrlBlockerEngine.enabledFlow.collectAsStateWithLifecycle()
    val focusModeActive by FocusModeEngine.isActiveFlow.collectAsStateWithLifecycle()
    val appBlockerRules by AppBlockerEngine.enabledRulesFlow.collectAsStateWithLifecycle()
    val appBlockerGroups by AppBlockerEngine.enabledGroupsFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popPage() },
                    )
                },
                title = { Text(text = "数字自律") },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            item(key = "self_control_runtime_status") {
                SelfControlRuntimeStatusCard()
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "focus_mode") {
                FocusModeCard(
                    isActive = focusModeActive,
                    onClick = { mainVm.navigatePage(FocusModeRoute) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "url_blocker") {
                UrlBlockerCard(
                    enabled = urlBlockerEnabled,
                    onClick = { mainVm.navigatePage(UrlBlockRoute) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "app_blocker") {
                AppBlockerCard(
                    enabledRuleCount = appBlockerRules.size,
                    enabledGroupCount = appBlockerGroups.size,
                    onClick = { mainVm.navigatePage(AppBlockerRoute) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "usage_guard") {
                UsageGuardCard(
                    enabled = settings.usageGuardEnabled,
                    scopeMode = settings.usageGuardScopeMode,
                    onClick = { mainVm.navigatePage(UsageGuardRoute) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (META.isGkdChannel) {
                item(key = "accessibility_guard") {
                    AccessibilityGuardCard(
                        enabled = settings.accessibilityGuardEnabled,
                        armed = settings.accessibilityGuardAutoReenableArmed,
                        onCheckedChange = { requestedEnabled ->
                            if (requestedEnabled) {
                                dialogState.showAccessibilityGuardDialog = true
                            } else {
                                dialogState.showAccessibilityGuardDisableDialog = true
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            item(key = "app_install_monitor") {
                AppInstallMonitorCard(
                    onClick = { mainVm.navigatePage(AppInstallMonitorRoute) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "auto_reenable_guard") {
                AutoReenableGuardCard(
                    intervalMinutes = settings.autoReenableIntervalMinutes,
                    changedAt = settings.autoReenableIntervalChangedAt,
                    nextEnforceAt = settings.autoReenableNextEnforceAt,
                    dailyDisableLimit = settings.autoReenableDailyDisableLimit,
                    dailyDisableUsed = settings.autoReenableDailyDisableUsed,
                    dailyDisableDayStartAt = settings.autoReenableDailyDisableDayStartAt,
                    onClick = { dialogState.showAutoReenableDialog = true },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (subStates.isEmpty()) {
                item {
                    Text(
                        text = "当前没有已启用的规则组，请先前往订阅页面启用规则。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.itemPadding(),
                    )
                }
            }
            subStates.forEach { subState ->
                item(key = "sub_${subState.subsId}") {
                    SubscriptionCard(
                        subState = subState,
                        isExpanded = expandedSubs.contains(subState.subsId),
                        expandedApps = expandedApps,
                        onExpandSubs = { vm.toggleExpandSubs(subState.subsId) },
                        onExpandApp = { appId ->
                            vm.toggleExpandApp("${subState.subsId}_$appId")
                        },
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
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        FocusLockPageDialogs(
            vm = vm,
            settings = settings,
            state = dialogState,
            lockSheetState = lockSheetState,
            pauseSheetState = pauseSheetState,
        )
    }
}
