@file:JvmName("AppBlockerCallbacks")

package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule

data class AppBlockerCallbacks(
    val onBack: () -> Unit,
    val onOpenGroupEditor: () -> Unit,
    val onEditGroup: (AppGroup) -> Unit,
    val onAddAppsToGroup: (AppGroup) -> Unit,
    val onDismissGroupEditor: () -> Unit,
    val onSaveGroup: () -> Unit,
    val onGroupNameChange: (String) -> Unit,
    val onGroupAppsPicked: (List<String>) -> Unit,
    val onRemoveGroupApp: (String) -> Unit,
    val onOpenAppRuleEditor: () -> Unit,
    val onOpenGroupRuleEditor: (AppGroup) -> Unit,
    val onEditRule: (BlockTimeRule) -> Unit,
    val onDismissRuleEditor: () -> Unit,
    val onSaveRule: () -> Unit,
    val onRuleTargetTypeChange: (Int) -> Unit,
    val onRuleTargetIdChange: (String) -> Unit,
    val onRuleStartTimeChange: (String) -> Unit,
    val onRuleEndTimeChange: (String) -> Unit,
    val onRuleDaysOfWeekChange: (List<Int>) -> Unit,
    val onRuleInterceptMessageChange: (String) -> Unit,
    val onRuleIsAllowModeChange: (Boolean) -> Unit,
    val onApplyTimeTemplate: (BlockTimeRule.Companion.TimeTemplate) -> Unit,
    val onToggleGroupEnabled: (AppGroup) -> Unit,
    val onDeleteGroup: (AppGroup) -> Unit,
    val onToggleRuleEnabled: (BlockTimeRule) -> Unit,
    val onDeleteRule: (BlockTimeRule) -> Unit,
    val onOpenGlobalLock: () -> Unit,
    val onDismissGlobalLock: () -> Unit,
    val onLockGlobal: () -> Unit,
    val onOpenGroupLock: (AppGroup) -> Unit,
    val onDismissGroupLock: () -> Unit,
    val onLockGroupTarget: () -> Unit,
    val onOpenRuleLock: (BlockTimeRule) -> Unit,
    val onDismissRuleLock: () -> Unit,
    val onLockRuleTarget: () -> Unit,
    val onSelectedLockDurationChange: (Int) -> Unit,
    val onCustomLockDurationChange: (Boolean) -> Unit,
    val onCustomLockDaysTextChange: (String) -> Unit,
    val onCustomLockHoursTextChange: (String) -> Unit,
)
