@file:JvmName("FocusLockDialogs21")

package li.songe.gkd.sdp.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.service.AccessibilityGuardController
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.AutoReenablePolicy
import li.songe.gkd.sdp.util.format
import li.songe.gkd.sdp.util.toast

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
internal fun FocusLockPageDialogs(
    vm: FocusLockVm,
    settings: SettingsStore,
    state: FocusLockDialogState,
    lockSheetState: SheetState,
    pauseSheetState: SheetState,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val scope = rememberCoroutineScope()

    if (state.showLockSheet) {
        state.currentLockTarget?.let { target ->
            FocusLockTargetSheet(
                target = target,
                vm = vm,
                sheetState = lockSheetState,
                onDismiss = { state.showLockSheet = false },
                onConfirm = {
                    vm.lockTarget(target.type, target.subsId, target.appId, target.groupKey)
                    scope.launch { lockSheetState.hide() }.invokeOnCompletion {
                        if (!lockSheetState.isVisible) state.showLockSheet = false
                    }
                },
            )
        }
    }
    if (state.showPauseSheet) {
        state.currentPauseTarget?.let { target ->
            FocusLockPauseSheet(
                target = target,
                sheetState = pauseSheetState,
                onDismiss = { state.showPauseSheet = false },
                onConfirm = { enabled, cooldown, message ->
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
                    scope.launch { pauseSheetState.hide() }.invokeOnCompletion {
                        if (!pauseSheetState.isVisible) state.showPauseSheet = false
                    }
                },
            )
        }
    }
    if (state.showPermissionDialog) {
        FocusLockOverlayPermissionDialog(
            onDismiss = { state.showPermissionDialog = false },
            onOpenSettings = {
                state.showPermissionDialog = false
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            },
        )
    }
    if (state.showAccessibilityGuardDialog) {
        FocusLockAccessibilityGuardEnableDialog(
            onDismiss = { state.showAccessibilityGuardDialog = false },
            onConfirm = {
                state.showAccessibilityGuardDialog = false
                scope.launch {
                    when (AccessibilityGuardController.enable(activity)) {
                        AccessibilityGuardController.EnableResult.RequiresA11yMode -> {
                            toast("请先切换到无障碍模式")
                            mainVm.navigatePage(AuthA11yRoute)
                        }
                        AccessibilityGuardController.EnableResult.UnavailableChannel,
                        AccessibilityGuardController.EnableResult.Enabled,
                        AccessibilityGuardController.EnableResult.AlreadyEnabled,
                        AccessibilityGuardController.EnableResult.Superseded -> Unit
                    }
                }
            },
        )
    }
    if (state.showAccessibilityGuardDisableDialog) {
        FocusLockAccessibilityGuardDisableDialog(
            onDismiss = { state.showAccessibilityGuardDisableDialog = false },
            onConfirm = {
                state.showAccessibilityGuardDisableDialog = false
                scope.launch {
                    when (val result = AccessibilityGuardController.disable()) {
                        AccessibilityGuardController.DisableResult.BlockedByLock ->
                            toast("数字自律锁定生效中，无法关闭无障碍权限守护")
                        is AccessibilityGuardController.DisableResult.BlockedByQuota ->
                            toast("今日关闭次数已用完（${result.limit} 次），将于明日 00:00 重置")
                        AccessibilityGuardController.DisableResult.Disabled,
                        AccessibilityGuardController.DisableResult.NoChange -> Unit
                    }
                }
            },
        )
    }
    if (state.showAutoReenableDialog) {
        FocusLockAutoReenableDialog(
            vm = vm,
            settings = settings,
            onDismiss = { state.showAutoReenableDialog = false },
        )
    }
}

@Composable
private fun FocusLockTargetSheet(
    target: LockTarget,
    vm: FocusLockVm,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LockDurationSheet(
            targetName = target.name,
            currentEndTime = target.currentEndTime,
            vm = vm,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun FocusLockPauseSheet(
    target: PauseTarget,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int, String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        MindfulPauseSheet(target = target, onConfirm = onConfirm)
    }
}

@Composable
private fun FocusLockOverlayPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要悬浮窗权限") },
        text = { Text("全屏拦截功能需要悬浮窗权限才能正常显示。请前往设置开启。") },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("去设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun FocusLockAccessibilityGuardEnableDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启无障碍权限守护") },
        text = {
            Text(
                "即使当前无障碍已经关闭，也可以先开启守护。检测到关闭后会立即显示倒计时，" +
                    "并在 15、25、30、33、35、36 分钟分别提醒一次（间隔为 15/10/5/3/2/1 分钟）。" +
                    "第 36 分钟最后一次提醒后仍未恢复，会显示全屏悬浮窗。" +
                    "关闭守护会受数字自律锁定、每日关闭限额和自动重开保护约束。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("同意并开启")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun FocusLockAccessibilityGuardDisableDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关闭无障碍权限守护") },
        text = {
            Text(
                "关闭后将停止无障碍权限提醒、倒计时和全屏提示。" +
                    "如果已加入自动重开保护，守护会在下一次检查时恢复。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("关闭守护")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun FocusLockAutoReenableDialog(
    vm: FocusLockVm,
    settings: SettingsStore,
    onDismiss: () -> Unit,
) {
    var inputText by remember { mutableStateOf(settings.autoReenableIntervalMinutes.toString()) }
    var dailyLimitText by remember { mutableStateOf(settings.autoReenableDailyDisableLimit.toString()) }
    val uiState = FocusLockVm.evaluateAutoReenableUiState(
        intervalMinutes = settings.autoReenableIntervalMinutes,
        lastChangedAt = settings.autoReenableIntervalChangedAt,
        scheduledNextEnforceAt = settings.autoReenableNextEnforceAt,
        dailyDisableLimit = settings.autoReenableDailyDisableLimit,
        dailyDisableUsed = settings.autoReenableDailyDisableUsed,
        dailyDisableDayStartAt = settings.autoReenableDailyDisableDayStartAt,
        now = System.currentTimeMillis(),
    )
    val nextEditableText = if (uiState.canEditInterval) {
        "可立即修改"
    } else {
        uiState.nextEditableAt.format("MM-dd HH:mm")
    }
    val parsed = inputText.toIntOrNull()
    val parsedDailyLimit = dailyLimitText.toIntOrNull()
    val intervalInputValid = parsed != null && parsed in 0..AutoReenablePolicy.MAX_INTERVAL_MINUTES
    val dailyLimitInputValid = parsedDailyLimit != null &&
        parsedDailyLimit in AutoReenablePolicy.MIN_DAILY_DISABLE_LIMIT..AutoReenablePolicy.MAX_DAILY_DISABLE_LIMIT
    val intervalChanged = parsed != null && parsed != settings.autoReenableIntervalMinutes
    val dailyLimitChanged = parsedDailyLimit != null &&
        AutoReenablePolicy.normalizeDailyDisableLimit(parsedDailyLimit) != uiState.dailyDisableLimit
    val canSaveInterval = intervalInputValid && (!intervalChanged || uiState.canEditInterval)
    val canSave = canSaveInterval && dailyLimitInputValid && (intervalChanged || dailyLimitChanged)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动重开间隔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("自动重开始终启用，无法关闭。会恢复已关闭的规则、使用申请开关与已加入保护的无障碍权限守护。")
                Text("下一次自动重开：${uiState.nextEnforceAt.format("MM-dd HH:mm")}")
                Text("今日已用/总额：${uiState.dailyDisableUsed}/${uiState.dailyDisableLimit}")
                Text("剩余次数：${uiState.dailyDisableRemaining}")
                Text("下一次重置时间：${uiState.nextDailyResetAt.format("MM-dd HH:mm")}")
                if (!uiState.canEditInterval) {
                    Text("冷却中，下次可修改：$nextEditableText")
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() }) inputText = value
                    },
                    label = { Text("间隔（分钟）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = uiState.canEditInterval,
                    isError = !intervalInputValid,
                )
                if (!intervalInputValid) {
                    Text(
                        text = "请输入 0~240 的整数分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = dailyLimitText,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() }) dailyLimitText = value
                    },
                    label = { Text("每日关闭限额（次）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !dailyLimitInputValid,
                )
                if (!dailyLimitInputValid) {
                    Text(
                        text = "请输入 1~5 的整数次数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (intervalChanged) {
                        vm.updateAutoReenableInterval(parsed)
                    }
                    if (dailyLimitChanged) {
                        vm.updateAutoReenableDailyDisableLimit(
                            parsedDailyLimit,
                        )
                    }
                    onDismiss()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
