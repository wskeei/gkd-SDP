@file:JvmName("SettingsScreen")

package li.songe.gkd.sdp.ui.home

import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.ignoreBatteryOptimizationsState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.service.StatusService
import li.songe.gkd.sdp.service.TrackService
import li.songe.gkd.sdp.service.fixRestartAutomatorService
import li.songe.gkd.sdp.settings.SettingsFormPolicy
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.AboutRoute
import li.songe.gkd.sdp.ui.AdvancedPageRoute
import li.songe.gkd.sdp.ui.BlockA11yAppListRoute
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.component.updateDialogOptions
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.util.BackupUtils
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.mapState
import li.songe.gkd.sdp.util.openAppDetailsSettings
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.toast

private data class SettingsHostData(
    val state: SettingsRenderState,
    val callbacks: SettingsCallbacks,
    val context: MainActivity?,
    val backupScope: CoroutineScope,
    val resetPageScrollEvent: Flow<HomeDestination>,
)

@Composable
private fun rememberSettingsRenderState(
    store: SettingsStore,
    vm: HomeVm,
    backupWorkflow: MutableState<BackupWorkflowState?>,
    showToastInputDlg: MutableState<Boolean>,
    showNotifTextInputDlg: MutableState<Boolean>,
    showToastSettingsDlg: MutableState<Boolean>,
    showA11yBlockDlg: MutableState<Boolean>,
    showBackupDlg: MutableState<Boolean>,
): SettingsRenderState {
    val a11ySectionScope = rememberCoroutineScope()
    val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
    val trackServiceRunning by TrackService.isRunning.collectAsStateWithLifecycle()
    val shizukuContext by shizukuContextFlow.collectAsStateWithLifecycle()
    val ignoreBatteryOptimizations by ignoreBatteryOptimizationsState.stateFlow
        .collectAsStateWithLifecycle()
    val statusRunning by StatusService.isRunning.collectAsStateWithLifecycle()
    val summary by ruleSummaryFlow.collectAsStateWithLifecycle()
    val constraints by FocusLockUtils.allConstraintsFlow.collectAsStateWithLifecycle()
    val showA11ySection by remember {
        storeFlow.mapState(a11ySectionScope) { it.enableBlockA11yAppList }
            .debounce(300)
            .stateIn(a11ySectionScope, SharingStarted.Eagerly, store.enableBlockA11yAppList)
    }.collectAsStateWithLifecycle()

    val activeLockCount = remember(summary, constraints) {
        val activeConstraints = constraints.filter { it.lockEndTime > System.currentTimeMillis() }
        fun locked(subsId: Long, appId: String?, groupKey: Int): Boolean = activeConstraints.any {
            when (it.targetType) {
                li.songe.gkd.sdp.data.ConstraintConfig.TYPE_SUBSCRIPTION -> it.subsId == subsId
                li.songe.gkd.sdp.data.ConstraintConfig.TYPE_APP ->
                    appId != null && it.subsId == subsId && it.appId == appId
                li.songe.gkd.sdp.data.ConstraintConfig.TYPE_RULE_GROUP ->
                    it.subsId == subsId && it.appId == appId && it.groupKey == groupKey
                else -> false
            }
        }
        summary.globalGroups.count { locked(it.subsItem.id, null, it.group.key) } +
            summary.appIdToAllGroups.values.flatten().count {
                locked(it.subsItem.id, it.appId, it.group.key)
            }
    }

    return SettingsRenderState(
        store = store,
        backupWorkflow = backupWorkflow.value,
        showToastInputDlg = showToastInputDlg.value,
        showNotifTextInputDlg = showNotifTextInputDlg.value,
        showToastSettingsDlg = showToastSettingsDlg.value,
        showA11yBlockDlg = showA11yBlockDlg.value,
        showBackupDlg = showBackupDlg.value,
        subsStatus = subsStatus,
        trackServiceRunning = trackServiceRunning,
        shizukuOk = shizukuContext.ok,
        ignoreBatteryOptimizations = ignoreBatteryOptimizations,
        statusRunning = statusRunning,
        showA11ySection = showA11ySection,
        activeLockCount = activeLockCount,
    )
}

@Composable
private fun rememberSettingsCallbacks(
    mainVm: MainViewModel,
    vm: HomeVm,
    context: MainActivity?,
    store: SettingsStore,
    backupScope: CoroutineScope,
    backupWorkflow: MutableState<BackupWorkflowState?>,
    showToastInputDlg: MutableState<Boolean>,
    showNotifTextInputDlg: MutableState<Boolean>,
    showA11yBlockDlg: MutableState<Boolean>,
    showBackupDlg: MutableState<Boolean>,
    showToastSettingsDlg: MutableState<Boolean>,
): SettingsCallbacks {
    val updateStore: (SettingsStore) -> Unit = { storeFlow.value = it }
    val filePickerUnavailable = stringResource(R.string.backup_file_picker_unavailable)
    return SettingsCallbacks(
        updateStore = updateStore,
        navigateRoute = { mainVm.navigatePage(it) },
        showToastInput = { showToastInputDlg.value = true },
        dismissToastInput = { showToastInputDlg.value = false },
        confirmToast = { newValue ->
            if (newValue != store.actionToast) {
                updateStore(store.copy(actionToast = newValue))
                toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
            }
            showToastInputDlg.value = false
        },
        showToastHelp = {
            showToastInputDlg.value = false
            val confirmAction = {
                mainVm.dialogFlow.value = null
                showToastInputDlg.value = true
            }
            mainVm.dialogFlow.updateDialogOptions(
                title = li.songe.gkd.sdp.app.getString(R.string.s_d88d6e6c25),
                text = li.songe.gkd.sdp.app.getString(R.string.s_1941b8aa85),
                confirmAction = confirmAction,
                onDismissRequest = confirmAction,
            )
        },
        showNotifInput = { showNotifTextInputDlg.value = true },
        dismissNotifInput = { showNotifTextInputDlg.value = false },
        confirmNotif = { title, text ->
            context?.justHideSoftInput()
            if (
                context != null &&
                SettingsFormPolicy.notificationTextChanged(
                    store.customNotifTitle,
                    store.customNotifText,
                    title,
                    text,
                )
            ) {
                updateStore(store.copy(customNotifTitle = title, customNotifText = text))
                toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
            }
            showNotifTextInputDlg.value = false
        },
        showNotifHelp = {
            showNotifTextInputDlg.value = false
            val confirmAction = {
                mainVm.dialogFlow.value = null
                showNotifTextInputDlg.value = true
            }
            mainVm.dialogFlow.updateDialogOptions(
                title = li.songe.gkd.sdp.app.getString(R.string.s_d88d6e6c25),
                text = li.songe.gkd.sdp.app.getString(R.string.s_3036ac5688),
                confirmAction = confirmAction,
                onDismissRequest = confirmAction,
            )
        },
        dismissA11yBlock = { showA11yBlockDlg.value = false },
        confirmA11yBlock = vm.viewModelScope.launchAsFn {
            showA11yBlockDlg.value = false
            delay(200)
            updateStore(store.copy(enableBlockA11yAppList = true))
        },
        guardShizuku = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            mainVm.guardShizukuContext()
        },
        requestStatusService = vm.viewModelScope.launchAsFn {
            context?.let { StatusService.requestStart(it) }
        },
        openBatterySettings = vm.viewModelScope.launchAsFn {
            context?.let { requiredPermission(it, ignoreBatteryOptimizationsState) }
        },
        openAppDetails = { openAppDetailsSettings() },
        switchRecentApps = {
            val inputManager = shizukuContextFlow.value.inputManager
            if (inputManager != null) {
                inputManager.key(KeyEvent.KEYCODE_APP_SWITCH)
            } else {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_7b29e9051a))
            }
        },
        toggleToastSettings = { showToastSettingsDlg.value = !showToastSettingsDlg.value },
        showViewRestrictions = {
            mainVm.dialogFlow.updateDialogOptions(
                title = li.songe.gkd.sdp.app.getString(R.string.s_1b2219a307),
                text = li.songe.gkd.sdp.app.getString(R.string.s_5118a80944),
            )
        },
        toggleTrackService = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                mainVm.dialogFlow.waitResult(
                    title = li.songe.gkd.sdp.app.getString(R.string.s_59e2c8e61d),
                    text = li.songe.gkd.sdp.app.getString(R.string.s_881aca9e23),
                    confirmText = li.songe.gkd.sdp.app.getString(R.string.s_1fc1afc5c5),
                )
                context?.let { requiredPermission(it, foregroundServiceSpecialUseState) }
                context?.let { requiredPermission(it, notificationState) }
                context?.let { requiredPermission(it, canDrawOverlaysState) }
                TrackService.start()
            } else {
                TrackService.stop()
            }
        },
        toggleExcludeFromRecents = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                mainVm.dialogFlow.waitResult(
                    title = li.songe.gkd.sdp.app.getString(R.string.s_8c91b02262),
                    text = li.songe.gkd.sdp.app.getString(R.string.s_7834885df6),
                    confirmText = li.songe.gkd.sdp.app.getString(R.string.s_1fc1afc5c5),
                )
            }
            updateStore(store.copy(excludeFromRecents = !store.excludeFromRecents))
        },
        enableBlockA11y = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                showA11yBlockDlg.value = true
            } else {
                updateStore(store.copy(enableBlockA11yAppList = false))
                fixRestartAutomatorService()
            }
        },
        navigateBlockA11y = { mainVm.navigatePage(BlockA11yAppListRoute) },
        showBackup = { showBackupDlg.value = true },
        dismissBackup = { showBackupDlg.value = false },
        importBackup = {
            showBackupDlg.value = false
            if (context != null) {
                backupScope.launch {
                    context.pickFile("*/*")?.let { BackupUtils.pendingImportUriFlow.value = it }
                }
            } else {
                backupWorkflow.value = BackupWorkflowState(
                    stage = BackupWorkflowStage.IMPORT_PASSWORD,
                    errorText = filePickerUnavailable,
                )
            }
        },
        exportBackup = {
            showBackupDlg.value = false
            backupWorkflow.value = BackupWorkflowState(stage = BackupWorkflowStage.EXPORT_CATEGORIES)
        },
        updateBackupWorkflow = { backupWorkflow.value = it },
    )
}

@Composable
private fun rememberSettingsHost(): SettingsHostData {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as? MainActivity
    val vm = viewModel<HomeVm>()
    val store by storeFlow.collectAsStateWithLifecycle()
    val pendingImportUri by BackupUtils.pendingImportUriFlow.collectAsStateWithLifecycle()
    val backupWorkflow = remember { mutableStateOf<BackupWorkflowState?>(null) }
    val backupScope = rememberCoroutineScope()
    val showToastInputDlg = vm.showToastInputDlgFlow.asMutableState()
    val showNotifTextInputDlg = vm.showNotifTextInputDlgFlow.asMutableState()
    val showToastSettingsDlg = vm.showToastSettingsDlgFlow.asMutableState()
    val showA11yBlockDlg = vm.showA11yBlockDlgFlow.asMutableState()
    val showBackupDlg = vm.showBackupDlgFlow.asMutableState()

    LaunchedEffect(pendingImportUri) {
        pendingImportUri?.let { uri ->
            backupWorkflow.value = BackupWorkflowState(
                stage = BackupWorkflowStage.IMPORT_PASSWORD,
                sourceUri = uri,
            )
            showBackupDlg.value = false
        }
    }

    val state = rememberSettingsRenderState(
        store = store,
        vm = vm,
        backupWorkflow = backupWorkflow,
        showToastInputDlg = showToastInputDlg,
        showNotifTextInputDlg = showNotifTextInputDlg,
        showToastSettingsDlg = showToastSettingsDlg,
        showA11yBlockDlg = showA11yBlockDlg,
        showBackupDlg = showBackupDlg,
    )
    val callbacks = rememberSettingsCallbacks(
        mainVm = mainVm,
        vm = vm,
        context = context,
        store = store,
        backupScope = backupScope,
        backupWorkflow = backupWorkflow,
        showToastInputDlg = showToastInputDlg,
        showNotifTextInputDlg = showNotifTextInputDlg,
        showA11yBlockDlg = showA11yBlockDlg,
        showBackupDlg = showBackupDlg,
        showToastSettingsDlg = showToastSettingsDlg,
    )

    return SettingsHostData(
        state = state,
        callbacks = callbacks,
        context = context,
        backupScope = backupScope,
        resetPageScrollEvent = mainVm.resetPageScrollEvent,
    )
}

@Composable
internal fun useSettingsPageHost(): ScaffoldExt {
    val host = rememberSettingsHost()
    val state = host.state
    val callbacks = host.callbacks

    host.context?.let {
        SettingsTextDialogs(
            context = it,
            store = state.store,
            showToastInputDlg = state.showToastInputDlg,
            showNotifTextInputDlg = state.showNotifTextInputDlg,
            onDismissToastInput = callbacks.dismissToastInput,
            onConfirmToastInput = callbacks.confirmToast,
            onShowToastHelp = callbacks.showToastHelp,
            onDismissNotifInput = callbacks.dismissNotifInput,
            onConfirmNotifInput = callbacks.confirmNotif,
            onShowNotifHelp = callbacks.showNotifHelp,
        )
    }

    if (state.showA11yBlockDlg) {
        BlockA11yDialog(
            onDismissRequest = callbacks.dismissA11yBlock,
            onConfirm = callbacks.confirmA11yBlock,
            shizukuOk = state.shizukuOk,
            statusRunning = state.statusRunning,
            ignoreBatteryOptimizations = state.ignoreBatteryOptimizations,
            onGuardShizuku = callbacks.guardShizuku,
            onRequestStatusService = callbacks.requestStatusService,
            onOpenBatterySettings = callbacks.openBatterySettings,
            onOpenAppDetails = callbacks.openAppDetails,
            onSwitchRecentApps = callbacks.switchRecentApps,
        )
    }

    SettingsBackupDialogs(
        context = host.context,
        backupScope = host.backupScope,
        workflow = state.backupWorkflow,
        onUpdateWorkflow = callbacks.updateBackupWorkflow,
        showBackupDlg = state.showBackupDlg,
        onDismissBackup = callbacks.dismissBackup,
        onImportBackup = callbacks.importBackup,
        onExportBackup = callbacks.exportBackup,
    )

    return SettingsContent(
        state = state,
        callbacks = callbacks,
        resetPageScrollEvent = host.resetPageScrollEvent,
    )
}

@Composable
internal fun SettingsBackupDialogs(
    context: MainActivity?,
    vm: HomeVm,
    backupScope: CoroutineScope,
    backupWorkflow: MutableState<BackupWorkflowState?>,
) {
    val showBackupDlg by vm.showBackupDlgFlow.collectAsStateWithLifecycle()
    val filePickerUnavailable = stringResource(R.string.backup_file_picker_unavailable)
    SettingsBackupDialogs(
        context = context,
        backupScope = backupScope,
        workflow = backupWorkflow.value,
        onUpdateWorkflow = { backupWorkflow.value = it },
        showBackupDlg = showBackupDlg,
        onDismissBackup = { vm.showBackupDlgFlow.value = false },
        onImportBackup = {
            vm.showBackupDlgFlow.value = false
            if (context != null) {
                backupScope.launch {
                    context.pickFile("*/*")?.let { BackupUtils.pendingImportUriFlow.value = it }
                }
            } else {
                backupWorkflow.value = BackupWorkflowState(
                    stage = BackupWorkflowStage.IMPORT_PASSWORD,
                    errorText = filePickerUnavailable,
                )
            }
        },
        onExportBackup = {
            vm.showBackupDlgFlow.value = false
            backupWorkflow.value = BackupWorkflowState(stage = BackupWorkflowStage.EXPORT_CATEGORIES)
        },
    )
}

@Composable
fun useSettingsPage(): ScaffoldExt = useSettingsPageHost()

@Composable
fun SettingsScreen() {
    val settings = useSettingsPage()
    Column(modifier = Modifier) {
        settings.topBar()
        settings.content(PaddingValues())
    }
}
