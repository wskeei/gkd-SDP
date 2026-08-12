@file:JvmName("UrlBlockerScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.appInfoMapFlow

/** Stable screen host used by navigation; rendering sections stay isolated from ViewModels. */
@Composable
internal fun UrlBlockerScreen() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UrlBlockVm>()
    val allGroups by vm.allGroupsFlow.collectAsStateWithLifecycle()
    val allUrlRules by vm.allUrlRulesFlow.collectAsStateWithLifecycle()
    val allTimeRules by vm.allTimeRulesFlow.collectAsStateWithLifecycle()
    val globalLock by vm.globalLockFlow.collectAsStateWithLifecycle()
    val browsers by vm.browsersFlow.collectAsStateWithLifecycle()
    val appInfoMap by appInfoMapFlow.collectAsStateWithLifecycle()

    var uiState by remember { mutableStateOf(UrlBlockerUiState()) }
    val dataState = vm.present(
        allGroups = allGroups,
        allTimeRules = allTimeRules,
        allUrlRules = allUrlRules,
        globalLock = globalLock,
        browsers = browsers,
    )
    val state = uiState.copy(
        allGroups = dataState.allGroups,
        allTimeRules = dataState.allTimeRules,
        allUrlRules = dataState.allUrlRules,
        globalLock = dataState.globalLock,
        browsers = dataState.browsers,
    )

    var showGlobalLockSheet by remember { mutableStateOf(false) }
    var showGroupLockSheet by remember { mutableStateOf(false) }
    var showTimeRuleLockSheet by remember { mutableStateOf(false) }
    var showUrlRuleLockSheet by remember { mutableStateOf(false) }
    var lockTargetGroup by remember { mutableStateOf<UrlRuleGroup?>(null) }
    var lockTargetTimeRule by remember { mutableStateOf<UrlTimeRule?>(null) }
    var lockTargetUrlRule by remember { mutableStateOf<UrlBlockRule?>(null) }
    var urlEditorInitialGroupId by remember { mutableStateOf(0L) }
    var timeRuleEditorTargetType by remember { mutableStateOf(UrlTimeRule.TARGET_TYPE_RULE) }
    var timeRuleEditorTargetId by remember { mutableStateOf(0L) }

    val actions = UrlBlockerScreenStateActions(
        dispatch = { action -> uiState = uiState.reduce(action) },
        showGlobalLockSheet = { showGlobalLockSheet = it },
        showGroupLockSheet = { showGroupLockSheet = it },
        showTimeRuleLockSheet = { showTimeRuleLockSheet = it },
        showUrlRuleLockSheet = { showUrlRuleLockSheet = it },
        lockTargetGroup = { lockTargetGroup = it },
        lockTargetTimeRule = { lockTargetTimeRule = it },
        lockTargetUrlRule = { lockTargetUrlRule = it },
        urlEditorInitialGroupId = { urlEditorInitialGroupId = it },
        timeRuleEditorTargetType = { timeRuleEditorTargetType = it },
        timeRuleEditorTargetId = { timeRuleEditorTargetId = it },
        currentLockTargetGroup = { lockTargetGroup },
        currentLockTargetTimeRule = { lockTargetTimeRule },
        currentLockTargetUrlRule = { lockTargetUrlRule },
    )
    val callbacks = createUrlBlockerCallbacks(
        vm = vm,
        state = state,
        actions = actions,
        onBack = { mainVm.popPage() },
    )
    val urlEditorInitialTimeRule = state.editingUrlRule?.let { rule ->
        state.allTimeRules.firstOrNull {
            it.targetType == UrlTimeRule.TARGET_TYPE_RULE && it.targetId == rule.id
        }
    }

    UrlBlockerPageSections(
        state = state,
        showGlobalLockSheet = showGlobalLockSheet,
        showGroupLockSheet = showGroupLockSheet,
        lockTargetGroup = lockTargetGroup,
        showTimeRuleLockSheet = showTimeRuleLockSheet,
        lockTargetTimeRule = lockTargetTimeRule,
        showUrlRuleLockSheet = showUrlRuleLockSheet,
        lockTargetUrlRule = lockTargetUrlRule,
        urlEditorInitialGroupId = urlEditorInitialGroupId,
        urlEditorInitialTimeRule = urlEditorInitialTimeRule,
        timeRuleEditorTargetType = timeRuleEditorTargetType,
        timeRuleEditorTargetId = timeRuleEditorTargetId,
        appInfoMap = appInfoMap,
        callbacks = callbacks,
    )
}

private data class UrlBlockerScreenStateActions(
    val dispatch: (UrlBlockerAction) -> Unit,
    val showGlobalLockSheet: (Boolean) -> Unit,
    val showGroupLockSheet: (Boolean) -> Unit,
    val showTimeRuleLockSheet: (Boolean) -> Unit,
    val showUrlRuleLockSheet: (Boolean) -> Unit,
    val lockTargetGroup: (UrlRuleGroup?) -> Unit,
    val lockTargetTimeRule: (UrlTimeRule?) -> Unit,
    val lockTargetUrlRule: (UrlBlockRule?) -> Unit,
    val urlEditorInitialGroupId: (Long) -> Unit,
    val timeRuleEditorTargetType: (Int) -> Unit,
    val timeRuleEditorTargetId: (Long) -> Unit,
    val currentLockTargetGroup: () -> UrlRuleGroup?,
    val currentLockTargetTimeRule: () -> UrlTimeRule?,
    val currentLockTargetUrlRule: () -> UrlBlockRule?,
)

private fun createUrlBlockerCallbacks(
    vm: UrlBlockVm,
    state: UrlBlockerUiState,
    actions: UrlBlockerScreenStateActions,
    onBack: () -> Unit,
): UrlBlockerCallbacks = UrlBlockerCallbacks(
    onBack = onBack,
    onAddGroup = { actions.dispatch(UrlBlockerAction.OpenGroupEditor) },
    onOpenBrowserList = { actions.dispatch(UrlBlockerAction.OpenBrowserList) },
    onOpenGlobalLock = { actions.showGlobalLockSheet(true) },
    onDismissGlobalLock = { actions.showGlobalLockSheet(false) },
    onLockGlobal = { draft ->
        applyLockDraft(vm, draft)
        vm.lockGlobal()
        actions.showGlobalLockSheet(false)
    },
    onToggleGroup = { vm.toggleGroupEnabled(it) },
    onEditGroup = { actions.dispatch(UrlBlockerAction.EditGroup(it)) },
    onDeleteGroup = { vm.deleteGroup(it) },
    onLockGroup = { group ->
        actions.lockTargetGroup(group)
        actions.showGroupLockSheet(true)
    },
    onAddTimeRule = { targetType, targetId ->
        actions.timeRuleEditorTargetType(targetType)
        actions.timeRuleEditorTargetId(targetId)
        actions.dispatch(UrlBlockerAction.OpenTimeRuleEditor)
    },
    onEditTimeRule = { rule ->
        actions.timeRuleEditorTargetType(rule.targetType)
        actions.timeRuleEditorTargetId(rule.targetId)
        actions.dispatch(UrlBlockerAction.EditTimeRule(rule))
    },
    onDeleteTimeRule = { vm.deleteTimeRule(it) },
    onLockTimeRule = { rule ->
        actions.lockTargetTimeRule(rule)
        actions.showTimeRuleLockSheet(true)
    },
    onAddUrlRule = { groupId ->
        actions.urlEditorInitialGroupId(groupId)
        actions.dispatch(UrlBlockerAction.OpenUrlEditor)
    },
    onEditUrlRule = { rule ->
        actions.urlEditorInitialGroupId(rule.groupId)
        actions.dispatch(UrlBlockerAction.EditUrlRule(rule))
    },
    onDeleteUrlRule = { vm.deleteUrlRule(it) },
    onToggleUrlRule = { vm.toggleUrlRuleEnabled(it) },
    onLockUrlRule = { rule ->
        actions.lockTargetUrlRule(rule)
        actions.showUrlRuleLockSheet(true)
    },
    onDismissGroupEditor = { actions.dispatch(UrlBlockerAction.CloseGroupEditor) },
    onSaveGroup = { name, quickUrls ->
        vm.editingGroup = state.editingGroup
        vm.groupName = name
        vm.groupQuickUrls = quickUrls
        vm.saveGroup()
        actions.dispatch(UrlBlockerAction.CloseGroupEditor)
    },
    onDismissUrlEditor = { actions.dispatch(UrlBlockerAction.CloseUrlEditor) },
    onSaveUrlRule = { draft ->
        saveUrlRuleDraft(vm, state, draft)
        actions.dispatch(UrlBlockerAction.CloseUrlEditor)
    },
    onDismissTimeRuleEditor = { actions.dispatch(UrlBlockerAction.CloseTimeRuleEditor) },
    onSaveTimeRule = { draft ->
        vm.editingTimeRule = state.editingTimeRule
        vm.timeRuleTargetType = draft.targetType
        vm.timeRuleTargetId = draft.targetId
        vm.timeRuleStartTime = draft.startTime
        vm.timeRuleEndTime = draft.endTime
        vm.timeRuleDaysOfWeek = draft.daysOfWeek
        vm.timeRuleIsAllowMode = draft.isAllowMode
        vm.saveTimeRule()
        actions.dispatch(UrlBlockerAction.CloseTimeRuleEditor)
    },
    onDismissBrowserList = { actions.dispatch(UrlBlockerAction.CloseBrowserList) },
    onAddBrowser = { actions.dispatch(UrlBlockerAction.OpenBrowserEditor) },
    onEditBrowser = { actions.dispatch(UrlBlockerAction.EditBrowser(it)) },
    onDeleteBrowser = { vm.deleteBrowser(it) },
    onToggleBrowser = { vm.toggleBrowserEnabled(it) },
    onDismissBrowserEditor = { actions.dispatch(UrlBlockerAction.CloseBrowserEditor) },
    onSaveBrowser = { draft ->
        vm.editingBrowser = state.editingBrowser
        vm.browserName = draft.name
        vm.browserPackageName = draft.packageName
        vm.browserUrlBarId = draft.urlBarId
        vm.saveBrowser()
        actions.dispatch(UrlBlockerAction.CloseBrowserEditor)
    },
    onDismissGroupLock = {
        actions.showGroupLockSheet(false)
        actions.lockTargetGroup(null)
    },
    onLockGroupTarget = { draft ->
        actions.currentLockTargetGroup()?.let { group ->
            applyLockDraft(vm, draft)
            vm.lockGroup(group)
        }
        actions.showGroupLockSheet(false)
        actions.lockTargetGroup(null)
    },
    onDismissTimeRuleLock = {
        actions.showTimeRuleLockSheet(false)
        actions.lockTargetTimeRule(null)
    },
    onLockTimeRuleTarget = { draft ->
        actions.currentLockTargetTimeRule()?.let { rule ->
            applyLockDraft(vm, draft)
            vm.lockTimeRule(rule)
        }
        actions.showTimeRuleLockSheet(false)
        actions.lockTargetTimeRule(null)
    },
    onDismissUrlRuleLock = {
        actions.showUrlRuleLockSheet(false)
        actions.lockTargetUrlRule(null)
    },
    onLockUrlRuleTarget = { draft ->
        actions.currentLockTargetUrlRule()?.let { rule ->
            applyLockDraft(vm, draft)
            vm.lockUrlRule(rule)
        }
        actions.showUrlRuleLockSheet(false)
        actions.lockTargetUrlRule(null)
    },
)

private fun applyLockDraft(vm: UrlBlockVm, draft: UrlLockDraft) {
    vm.selectedLockDuration = draft.durationMinutes
    vm.isCustomLockDuration = draft.isCustom
    vm.customLockDaysText = draft.daysText
    vm.customLockHoursText = draft.hoursText
}

private fun saveUrlRuleDraft(vm: UrlBlockVm, state: UrlBlockerUiState, draft: UrlRuleDraft) {
    val editingRule = state.editingUrlRule
    vm.editingUrlRule = editingRule
    vm.editingTimeRule = state.allTimeRules.firstOrNull {
        it.targetType == UrlTimeRule.TARGET_TYPE_RULE && it.targetId == editingRule?.id
    }
    vm.urlPattern = draft.pattern
    vm.urlMatchType = draft.matchType
    vm.urlName = draft.name
    vm.urlRedirectUrl = draft.redirectUrl
    vm.urlShowIntercept = draft.showIntercept
    vm.urlInterceptMessage = draft.interceptMessage
    vm.urlGroupId = draft.groupId
    vm.timeRuleStartTime = draft.timeRuleStartTime
    vm.timeRuleEndTime = draft.timeRuleEndTime
    vm.timeRuleDaysOfWeek = draft.timeRuleDaysOfWeek
    vm.timeRuleIsAllowMode = draft.timeRuleIsAllowMode
    vm.timeRuleInterceptMsg = draft.interceptMessage
    vm.saveUrlRule()
}
