@file:JvmName("AppBlockerScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.a11y.sdpRuntimeFeatureCoordinator
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.ui.share.LocalMainViewModel

/** Small host; rendering sections stay isolated from navigation and service callers. */
@Composable
fun AppBlockerPage() {
    val vm = viewModel<AppBlockerVm>()
    val mainVm = LocalMainViewModel.current
    val allGroups by vm.allGroupsFlow.collectAsStateWithLifecycle()
    val allRules by vm.allRulesFlow.collectAsStateWithLifecycle()
    val globalLock by vm.globalLockFlow.collectAsStateWithLifecycle()
    val runtimeStatus by sdpRuntimeFeatureCoordinator.statusFlow.collectAsStateWithLifecycle()
    val overlayPermission by canDrawOverlaysState.stateFlow.collectAsStateWithLifecycle()

    var showGlobalLockSheet by remember { mutableStateOf(false) }
    var showGroupLockSheet by remember { mutableStateOf(false) }
    var showRuleLockSheet by remember { mutableStateOf(false) }
    var lockTargetGroup by remember { mutableStateOf<AppGroup?>(null) }
    var lockTargetRule by remember { mutableStateOf<BlockTimeRule?>(null) }

    val state = vm.present(
        allGroups = allGroups,
        allRules = allRules,
        globalLock = globalLock,
    )
    val callbacks = createAppBlockerCallbacks(
        vm = vm,
        showGlobalLockSheet = { showGlobalLockSheet = it },
        showGroupLockSheet = { showGroupLockSheet = it },
        showRuleLockSheet = { showRuleLockSheet = it },
        lockTargetGroup = { lockTargetGroup = it },
        lockTargetRule = { lockTargetRule = it },
        currentLockTargetGroup = { lockTargetGroup },
        currentLockTargetRule = { lockTargetRule },
        onBack = mainVm::popPage,
    )

    AppBlockerPageSections(
        state = state,
        showGlobalLockSheet = showGlobalLockSheet,
        showGroupLockSheet = showGroupLockSheet,
        showRuleLockSheet = showRuleLockSheet,
        lockTargetGroup = lockTargetGroup,
        lockTargetRule = lockTargetRule,
        runtimeStatus = runtimeStatus,
        overlayPermission = overlayPermission,
        callbacks = callbacks,
    )
}

private fun createAppBlockerCallbacks(
    vm: AppBlockerVm,
    showGlobalLockSheet: (Boolean) -> Unit,
    showGroupLockSheet: (Boolean) -> Unit,
    showRuleLockSheet: (Boolean) -> Unit,
    lockTargetGroup: (AppGroup?) -> Unit,
    lockTargetRule: (BlockTimeRule?) -> Unit,
    currentLockTargetGroup: () -> AppGroup?,
    currentLockTargetRule: () -> BlockTimeRule?,
    onBack: () -> Unit,
): AppBlockerCallbacks = AppBlockerCallbacks(
    onBack = onBack,
    onOpenGroupEditor = {
        vm.resetGroupForm()
        vm.showGroupEditor = true
    },
    onEditGroup = { vm.loadGroupForEdit(it) },
    onAddAppsToGroup = {
        vm.loadGroupForEdit(it, AppBlockerVm.GroupEditorMode.AppendApps)
    },
    onDismissGroupEditor = { vm.resetGroupForm() },
    onSaveGroup = { vm.saveGroup() },
    onGroupNameChange = { vm.groupName = it },
    onGroupAppsPicked = { vm.applyPickedApps(it) },
    onRemoveGroupApp = { vm.removeAppFromGroup(it) },
    onOpenAppRuleEditor = {
        vm.resetRuleForm()
        vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_APP
        vm.showRuleEditor = true
    },
    onOpenGroupRuleEditor = { group ->
        vm.resetRuleForm()
        vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_GROUP
        vm.ruleTargetId = group.id.toString()
        vm.showRuleEditor = true
    },
    onEditRule = { vm.loadRuleForEdit(it) },
    onDismissRuleEditor = { vm.resetRuleForm() },
    onSaveRule = { vm.saveRule() },
    onRuleTargetTypeChange = { targetType ->
        vm.ruleTargetType = targetType
        vm.ruleTargetId = ""
    },
    onRuleTargetIdChange = { vm.ruleTargetId = it },
    onRuleStartTimeChange = { vm.ruleStartTime = it },
    onRuleEndTimeChange = { vm.ruleEndTime = it },
    onRuleDaysOfWeekChange = { vm.ruleDaysOfWeek = it },
    onRuleInterceptMessageChange = { vm.ruleInterceptMessage = it },
    onRuleIsAllowModeChange = { vm.ruleIsAllowMode = it },
    onApplyTimeTemplate = { vm.applyTemplate(it) },
    onToggleGroupEnabled = { vm.toggleGroupEnabled(it) },
    onDeleteGroup = { vm.deleteGroup(it) },
    onToggleRuleEnabled = { vm.toggleRuleEnabled(it) },
    onDeleteRule = { vm.deleteRule(it) },
    onOpenGlobalLock = { showGlobalLockSheet(true) },
    onDismissGlobalLock = { showGlobalLockSheet(false) },
    onLockGlobal = {
        vm.lockGlobal()
        showGlobalLockSheet(false)
    },
    onOpenGroupLock = { group ->
        lockTargetGroup(group)
        showGroupLockSheet(true)
    },
    onDismissGroupLock = {
        showGroupLockSheet(false)
        lockTargetGroup(null)
    },
    onLockGroupTarget = {
        currentLockTargetGroup()?.let { vm.lockGroup(it) }
        showGroupLockSheet(false)
        lockTargetGroup(null)
    },
    onOpenRuleLock = { rule ->
        lockTargetRule(rule)
        showRuleLockSheet(true)
    },
    onDismissRuleLock = {
        showRuleLockSheet(false)
        lockTargetRule(null)
    },
    onLockRuleTarget = {
        currentLockTargetRule()?.let { vm.lockRule(it) }
        showRuleLockSheet(false)
        lockTargetRule(null)
    },
    onSelectedLockDurationChange = { vm.selectedLockDuration = it },
    onCustomLockDurationChange = { vm.isCustomLockDuration = it },
    onCustomLockDaysTextChange = { vm.customLockDaysText = it },
    onCustomLockHoursTextChange = { vm.customLockHoursText = it },
)
