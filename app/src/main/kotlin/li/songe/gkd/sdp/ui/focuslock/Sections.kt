@file:JvmName("FocusLockSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.R

@Composable
internal fun FocusLockPageSections(
    state: FocusLockUiState,
    settings: SettingsStore,
    runtimeStatus: li.songe.gkd.sdp.a11y.SdpRuntimeFeatureCoordinator.RuntimeStatus,
    overlayPermission: Boolean,
    autoReenableUiState: AutoReenableUiState,
    urlBlockerEnabled: Boolean,
    focusModeActive: Boolean,
    appBlockerRules: List<*>,
    appBlockerGroups: List<*>,
    dialogState: FocusLockDialogState,
    lockSheetState: SheetState,
    pauseSheetState: SheetState,
    onBack: () -> Unit,
    onOpenFocusMode: () -> Unit,
    onOpenUrlBlocker: () -> Unit,
    onOpenAppBlocker: () -> Unit,
    onOpenUsageGuard: () -> Unit,
    onOpenAppInstallMonitor: () -> Unit,
    onAccessibilityGuardCheckedChange: (Boolean) -> Unit,
    onAutoReenableClick: () -> Unit,
    onToggleExpandSubs: (Long) -> Unit,
    onToggleExpandApp: (String) -> Unit,
    onLockClick: (LockTarget) -> Unit,
    onPauseClick: (PauseTarget) -> Unit,
    onLockTarget: (LockTarget, LockDurationRequest) -> Unit,
    onUpdateInterceptConfig: (PauseTarget, Boolean, Int, String) -> Unit,
    onSaveAutoReenable: (Int?, Int?) -> Unit,
    onNavigateAuthA11y: () -> Unit,
) {
    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = onBack,
                    )
                },
                title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_6337015d1f)) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            item(key = "self_control_runtime_status") {
                SelfControlRuntimeStatusCard(
                    runtime = runtimeStatus,
                    overlayPermission = overlayPermission,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "focus_mode") {
                FocusModeCard(
                    isActive = focusModeActive,
                    onClick = onOpenFocusMode,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "url_blocker") {
                UrlBlockerCard(
                    enabled = urlBlockerEnabled,
                    onClick = onOpenUrlBlocker,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "app_blocker") {
                AppBlockerCard(
                    enabledRuleCount = appBlockerRules.size,
                    enabledGroupCount = appBlockerGroups.size,
                    onClick = onOpenAppBlocker,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item(key = "usage_guard") {
                UsageGuardCard(
                    enabled = settings.usageGuardEnabled,
                    scopeMode = settings.usageGuardScopeMode,
                    onClick = onOpenUsageGuard,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (META.isGkdChannel) {
                item(key = "accessibility_guard") {
                    AccessibilityGuardCard(
                        enabled = settings.accessibilityGuardEnabled,
                        armed = settings.accessibilityGuardAutoReenableArmed,
                        onCheckedChange = onAccessibilityGuardCheckedChange,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            item(key = "app_install_monitor") {
                AppInstallMonitorCard(
                    onClick = onOpenAppInstallMonitor,
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
                    autoReenableUiState = autoReenableUiState,
                    onClick = onAutoReenableClick,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (state.subStates.isEmpty()) {
                item {
                    Text(
                        text = li.songe.gkd.sdp.app.getString(R.string.s_86539a3eb0),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.itemPadding(),
                    )
                }
            }
            state.subStates.forEach { subState ->
                item(key = "sub_${subState.subsId}") {
                    SubscriptionCard(
                        subState = subState,
                        isExpanded = state.expandedSubs.contains(subState.subsId),
                        expandedApps = state.expandedApps,
                        onExpandSubs = { onToggleExpandSubs(subState.subsId) },
                        onExpandApp = { appId -> onToggleExpandApp("${subState.subsId}_$appId") },
                        onLockClick = onLockClick,
                        onPauseClick = onPauseClick,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        FocusLockPageDialogs(
            settings = settings,
            autoReenableUiState = autoReenableUiState,
            state = dialogState,
            lockSheetState = lockSheetState,
            pauseSheetState = pauseSheetState,
            onLockTarget = onLockTarget,
            onUpdateInterceptConfig = onUpdateInterceptConfig,
            onSaveAutoReenable = onSaveAutoReenable,
            onNavigateAuthA11y = onNavigateAuthA11y,
        )
    }
}
