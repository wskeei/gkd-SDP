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
import li.songe.gkd.sdp.util.AutoReenablePolicy
import li.songe.gkd.sdp.util.format
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
internal fun FocusLockPageDialogs(
    settings: SettingsStore,
    autoReenableUiState: AutoReenableUiState,
    state: FocusLockDialogState,
    lockSheetState: SheetState,
    pauseSheetState: SheetState,
    onLockTarget: (LockTarget, LockDurationRequest) -> Unit,
    onUpdateInterceptConfig: (PauseTarget, Boolean, Int, String) -> Unit,
    onSaveAutoReenable: (Int?, Int?) -> Unit,
    onNavigateAuthA11y: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val scope = rememberCoroutineScope()

    if (state.showLockSheet) {
        state.currentLockTarget?.let { target ->
            FocusLockTargetSheet(
                target = target,
                sheetState = lockSheetState,
                onDismiss = { state.showLockSheet = false },
                onConfirm = { request ->
                    onLockTarget(target, request)
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
                    onUpdateInterceptConfig(target, enabled, cooldown, message)
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
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_ce953b779c))
                            onNavigateAuthA11y()
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
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_5c6b661917))
                        is AccessibilityGuardController.DisableResult.BlockedByQuota ->
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_ba1f755996, (result.limit).toString()))
                        AccessibilityGuardController.DisableResult.Disabled,
                        AccessibilityGuardController.DisableResult.NoChange -> Unit
                    }
                }
            },
        )
    }
    if (state.showAutoReenableDialog) {
        FocusLockAutoReenableDialog(
            settings = settings,
            autoReenableUiState = autoReenableUiState,
            onDismiss = { state.showAutoReenableDialog = false },
            onSave = onSaveAutoReenable,
        )
    }
}

@Composable
private fun FocusLockTargetSheet(
    target: LockTarget,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConfirm: (LockDurationRequest) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LockDurationSheet(
            targetName = target.name,
            currentEndTime = target.currentEndTime,
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
        title = { Text(stringResource(R.string.s_b600d981ce)) },
        text = { Text(stringResource(R.string.s_923ae7391e)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.s_1f2998c9c8))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.s_4d0b4688c7))
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
        title = { Text(stringResource(R.string.s_b23186d5e5)) },
        text = {
            Text(
                stringResource(R.string.s_36524f9faf) +
                    stringResource(R.string.s_e4ccf19996) +
                    stringResource(R.string.s_c95c313099) +
                    stringResource(R.string.s_efcaa30b99),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.s_c5c9cefa90))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.s_4d0b4688c7))
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
        title = { Text(stringResource(R.string.s_61c6738b36)) },
        text = {
            Text(
                stringResource(R.string.s_db67ff730b) +
                    stringResource(R.string.s_c61e3028fc),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.s_9df1323aef))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.s_4d0b4688c7))
            }
        },
    )
}

@Composable
private fun FocusLockAutoReenableDialog(
    settings: SettingsStore,
    autoReenableUiState: AutoReenableUiState,
    onDismiss: () -> Unit,
    onSave: (Int?, Int?) -> Unit,
) {
    var inputText by remember { mutableStateOf(settings.autoReenableIntervalMinutes.toString()) }
    var dailyLimitText by remember { mutableStateOf(settings.autoReenableDailyDisableLimit.toString()) }
    val uiState = autoReenableUiState
    val nextEditableText = if (uiState.canEditInterval) {
        stringResource(R.string.focus_lock_editable_now)
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
        title = { Text(stringResource(R.string.s_7fd2ebc2a8)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.s_99858cff31))
                Text(stringResource(R.string.s_e78f2c3a4a, (uiState.nextEnforceAt.format("MM-dd HH:mm")).toString()))
                Text(stringResource(R.string.s_f70a7d4f0a, (uiState.dailyDisableUsed).toString(), (uiState.dailyDisableLimit).toString()))
                Text(stringResource(R.string.s_3e67db9f62, (uiState.dailyDisableRemaining).toString()))
                Text(stringResource(R.string.s_52113abdd4, (uiState.nextDailyResetAt.format("MM-dd HH:mm")).toString()))
                if (!uiState.canEditInterval) {
                    Text(stringResource(R.string.s_d135a965d5, (nextEditableText).toString()))
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() }) inputText = value
                    },
                    label = { Text(stringResource(R.string.s_6c7f21d273)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = uiState.canEditInterval,
                    isError = !intervalInputValid,
                )
                if (!intervalInputValid) {
                    Text(
                        text = stringResource(R.string.s_a52cfc2df4),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = dailyLimitText,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() }) dailyLimitText = value
                    },
                    label = { Text(stringResource(R.string.s_d73b70ee9b)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !dailyLimitInputValid,
                )
                if (!dailyLimitInputValid) {
                    Text(
                        text = stringResource(R.string.s_1d80e402d6),
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
                    onSave(
                        if (intervalChanged) parsed else null,
                        if (dailyLimitChanged) parsedDailyLimit else null,
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.s_fadf24dbc5))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.s_4d0b4688c7))
            }
        },
    )
}
