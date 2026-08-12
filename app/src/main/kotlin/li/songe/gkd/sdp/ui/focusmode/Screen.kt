@file:JvmName("FocusModeScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.ui.share.LocalMainViewModel

@Composable
fun FocusModePage() {
    val vm = viewModel<FocusModeVm>()
    val mainVm = LocalMainViewModel.current
    val allRules by vm.allRulesFlow.collectAsStateWithLifecycle()
    val activeSession by vm.activeSessionFlow.collectAsStateWithLifecycle()
    val isActive by vm.isActiveFlow.collectAsStateWithLifecycle()
    val currentWhitelist by vm.currentWhitelistFlow.collectAsStateWithLifecycle()

    var showQuickStartSheet by remember { mutableStateOf(false) }
    var showRuleEditorSheet by remember { mutableStateOf(false) }
    var showWhitelistPicker by remember { mutableStateOf(false) }
    var showLockSheet by remember { mutableStateOf(false) }
    var lockTargetRule by remember { mutableStateOf<FocusRule?>(null) }
    var whitelistPickerMode by remember { mutableStateOf("rule") }

    val state = vm.present(
        allRules = allRules,
        activeSession = activeSession,
        isActive = isActive,
        currentWhitelist = currentWhitelist,
    )
    val callbacks = createFocusModeCallbacks(
        vm = vm,
        showQuickStartSheet = { showQuickStartSheet = it },
        showRuleEditorSheet = { showRuleEditorSheet = it },
        showWhitelistPicker = { showWhitelistPicker = it },
        showLockSheet = { showLockSheet = it },
        lockTargetRule = { lockTargetRule = it },
        whitelistPickerMode = { whitelistPickerMode = it },
        currentLockTargetRule = { lockTargetRule },
        currentWhitelistPickerMode = { whitelistPickerMode },
        onBack = mainVm::popPage,
    )

    FocusModePageSections(
        state = state,
        showQuickStartSheet = showQuickStartSheet,
        showRuleEditorSheet = showRuleEditorSheet,
        showWhitelistPicker = showWhitelistPicker,
        showLockSheet = showLockSheet,
        lockTargetRule = lockTargetRule,
        whitelistPickerMode = whitelistPickerMode,
        callbacks = callbacks,
    )
}

private fun createFocusModeCallbacks(
    vm: FocusModeVm,
    showQuickStartSheet: (Boolean) -> Unit,
    showRuleEditorSheet: (Boolean) -> Unit,
    showWhitelistPicker: (Boolean) -> Unit,
    showLockSheet: (Boolean) -> Unit,
    lockTargetRule: (FocusRule?) -> Unit,
    whitelistPickerMode: (String) -> Unit,
    currentLockTargetRule: () -> FocusRule?,
    currentWhitelistPickerMode: () -> String,
    onBack: () -> Unit,
): FocusModeCallbacks = FocusModeCallbacks(
    onBack = onBack,
    onOpenRuleEditor = {
        vm.resetRuleForm()
        showRuleEditorSheet(true)
    },
    onEditRule = { rule ->
        vm.loadRuleForEdit(rule)
        showRuleEditorSheet(true)
    },
    onDismissRuleEditor = {
        showRuleEditorSheet(false)
        vm.resetRuleForm()
    },
    onSaveRule = {
        vm.saveRule()
        showRuleEditorSheet(false)
    },
    onRuleNameChange = { vm.ruleName = it },
    onRuleTypeChange = { vm.ruleType = it },
    onRuleStartTimeChange = { vm.ruleStartTime = it },
    onRuleEndTimeChange = { vm.ruleEndTime = it },
    onRuleDaysOfWeekChange = { vm.ruleDaysOfWeek = it },
    onRuleDurationHoursChange = { vm.ruleDurationHours = it },
    onRuleDurationMinutesChange = { vm.ruleDurationMinutes = it },
    onRuleWhitelistChange = { vm.ruleWhitelistApps = it },
    onRuleInterceptMessageChange = { vm.ruleInterceptMessage = it },
    onRuleIsLockedChange = { vm.ruleIsLocked = it },
    onRuleLockDurationChange = { vm.ruleLockDurationMinutes = it },
    onOpenQuickStart = { showQuickStartSheet(true) },
    onDismissQuickStart = { showQuickStartSheet(false) },
    onStartManualSession = {
        vm.startManualSession()
        showQuickStartSheet(false)
    },
    onManualHoursChange = { vm.manualHours = it },
    onManualMinutesChange = { vm.manualMinutes = it },
    onManualMessageChange = { vm.manualMessage = it },
    onManualWhitelistChange = { vm.manualWhitelistApps = it },
    onManualIsLockedChange = { vm.manualIsLocked = it },
    onManualLockDurationChange = { vm.manualLockDurationMinutes = it },
    onToggleRuleEnabled = { vm.toggleRuleEnabled(it) },
    onDeleteRule = { vm.deleteRule(it) },
    onStartQuickRule = { vm.startQuickRule(it) },
    onStopManualSession = { vm.stopManualSession() },
    onRemoveFromSessionWhitelist = { vm.removeFromSessionWhitelist(it) },
    onOpenRuleWhitelistPicker = {
        whitelistPickerMode("rule")
        showWhitelistPicker(true)
    },
    onOpenManualWhitelistPicker = {
        whitelistPickerMode("manual")
        showWhitelistPicker(true)
    },
    onDismissWhitelistPicker = { showWhitelistPicker(false) },
    onWhitelistConfirm = { selected ->
        if (currentWhitelistPickerMode() == "rule") {
            vm.ruleWhitelistApps = selected
        } else {
            vm.manualWhitelistApps = selected
        }
        showWhitelistPicker(false)
    },
    onWhitelistSearchQueryChange = { vm.whitelistSearchQuery = it },
    onClearWhitelistSearchQuery = { vm.whitelistSearchQuery = "" },
    onShowSystemAppsInWhitelistChange = { vm.showSystemAppsInWhitelist = it },
    onOpenRuleLock = { rule ->
        lockTargetRule(rule)
        showLockSheet(true)
    },
    onDismissRuleLock = {
        showLockSheet(false)
        lockTargetRule(null)
    },
    onLockRuleTarget = {
        currentLockTargetRule()?.let { vm.lockRule(it) }
        showLockSheet(false)
        lockTargetRule(null)
    },
    onSelectedLockDurationChange = { vm.selectedLockDuration = it },
    onCustomLockDurationChange = { vm.isCustomLockDuration = it },
    onCustomLockDaysTextChange = { vm.customLockDaysText = it },
    onCustomLockHoursTextChange = { vm.customLockHoursText = it },
)
