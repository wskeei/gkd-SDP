@file:JvmName("AppBlockerSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun AppBlockerPageSections() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AppBlockerVm>()
    val allGroups by vm.allGroupsFlow.collectAsStateWithLifecycle()
    val allRules by vm.allRulesFlow.collectAsStateWithLifecycle()
    val globalLock by vm.globalLockFlow.collectAsStateWithLifecycle()

    var showGlobalLockSheet by remember { mutableStateOf(false) }
    var showGroupLockSheet by remember { mutableStateOf(false) }
    var showRuleLockSheet by remember { mutableStateOf(false) }
    var lockTargetGroup by remember { mutableStateOf<AppGroup?>(null) }
    var lockTargetRule by remember { mutableStateOf<BlockTimeRule?>(null) }

    AppBlockerPageScaffold(
        globalLock = globalLock,
        allGroups = allGroups,
        allRules = allRules,
        vm = vm,
        onNavigateBack = { mainVm.popPage() },
        onShowGlobalLock = { showGlobalLockSheet = true },
        onLockGroup = { group ->
            lockTargetGroup = group
            showGroupLockSheet = true
        },
        onLockRule = { rule ->
            lockTargetRule = rule
            showRuleLockSheet = true
        },
    )

    // 应用组编辑器
    if (vm.showGroupEditor) {
        val isLocked = globalLock?.isCurrentlyLocked == true || vm.editingGroup?.isCurrentlyLocked == true
        GroupEditorSheet(
            vm = vm,
            isLocked = isLocked,
            onDismiss = { vm.resetGroupForm() },
            onSave = { vm.saveGroup() }
        )
    }

    // 规则编辑器
    if (vm.showRuleEditor) {
        val targetIsLocked = if (vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_GROUP) {
            allGroups.find { it.id == vm.ruleTargetId.toLongOrNull() }?.isCurrentlyLocked == true
        } else false
        val isLocked = globalLock?.isCurrentlyLocked == true || vm.editingRule?.isCurrentlyLocked == true || targetIsLocked
        RuleEditorSheet(
            vm = vm,
            allGroups = allGroups,
            isLocked = isLocked,
            onDismiss = { vm.resetRuleForm() },
            onSave = { vm.saveRule() }
        )
    }

    // 全局锁定 Sheet
    if (showGlobalLockSheet) {
        LockSheet(
            title = if (globalLock?.isCurrentlyLocked == true) stringResource(R.string.s_a04aff06d0) else stringResource(R.string.s_0261a6c710),
            description = stringResource(R.string.s_109aa38d7b),
            currentLockEndTime = globalLock?.lockEndTime,
            vm = vm,
            onDismiss = { showGlobalLockSheet = false },
            onLock = {
                vm.lockGlobal()
                showGlobalLockSheet = false
            }
        )
    }

    // 应用组锁定 Sheet
    if (showGroupLockSheet && lockTargetGroup != null) {
        LockSheet(
            title = if (lockTargetGroup!!.isCurrentlyLocked) stringResource(R.string.s_eae5fd957e) else stringResource(R.string.s_e13f066999),
            description = stringResource(R.string.s_77e7c35757),
            currentLockEndTime = if (lockTargetGroup!!.isCurrentlyLocked) lockTargetGroup!!.lockEndTime else null,
            vm = vm,
            onDismiss = {
                showGroupLockSheet = false
                lockTargetGroup = null
            },
            onLock = {
                vm.lockGroup(lockTargetGroup!!)
                showGroupLockSheet = false
                lockTargetGroup = null
            }
        )
    }

    // 规则锁定 Sheet
    if (showRuleLockSheet && lockTargetRule != null) {
        LockSheet(
            title = if (lockTargetRule!!.isCurrentlyLocked) stringResource(R.string.s_eae5fd957e) else stringResource(R.string.s_2201b864c9),
            description = stringResource(R.string.s_dd98d5a5d3),
            currentLockEndTime = if (lockTargetRule!!.isCurrentlyLocked) lockTargetRule!!.lockEndTime else null,
            vm = vm,
            onDismiss = {
                showRuleLockSheet = false
                lockTargetRule = null
            },
            onLock = {
                vm.lockRule(lockTargetRule!!)
                showRuleLockSheet = false
                lockTargetRule = null
            }
        )
    }
}

@Composable
private fun AppBlockerPageScaffold(
    globalLock: li.songe.gkd.sdp.data.AppBlockerLock?,
    allGroups: List<AppGroup>,
    allRules: List<BlockTimeRule>,
    vm: AppBlockerVm,
    onNavigateBack: () -> Unit,
    onShowGlobalLock: () -> Unit,
    onLockGroup: (AppGroup) -> Unit,
    onLockRule: (BlockTimeRule) -> Unit,
) {
    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = onNavigateBack,
                    )
                },
                title = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_e6bbd743b3)) },
                actions = {
                    IconButton(onClick = onShowGlobalLock) {
                        Icon(
                            PerfIcon.Lock,
                            contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_0261a6c710),
                            tint = if (globalLock?.isCurrentlyLocked == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        AppBlockerPageList(
            contentPadding = padding,
            globalLock = globalLock,
            allGroups = allGroups,
            allRules = allRules,
            vm = vm,
            onLockGroup = onLockGroup,
            onLockRule = onLockRule,
        )
    }
}

@Composable
private fun AppBlockerPageList(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    globalLock: li.songe.gkd.sdp.data.AppBlockerLock?,
    allGroups: List<AppGroup>,
    allRules: List<BlockTimeRule>,
    vm: AppBlockerVm,
    onLockGroup: (AppGroup) -> Unit,
    onLockRule: (BlockTimeRule) -> Unit,
) {
    LazyColumn(modifier = Modifier.scaffoldPadding(contentPadding)) {
        item(key = "self_control_runtime_status") {
            SelfControlRuntimeStatusCard()
            Spacer(modifier = Modifier.height(12.dp))
        }
        item(key = "auto_reenable_notice") {
            AppBlockerAutoReenableNotice()
        }
        if (globalLock?.isCurrentlyLocked == true) {
            item(key = "global_lock_status") {
                AppBlockerGlobalLockStatus(globalLock)
            }
        }
        item(key = "groups_header") {
            AppBlockerSectionHeader(
                title = li.songe.gkd.sdp.app.getString(R.string.s_b5f6acf594, allGroups.size),
                onAdd = {
                    vm.resetGroupForm()
                    vm.showGroupEditor = true
                },
            )
        }
        if (allGroups.isEmpty()) {
            item(key = "no_groups") {
                AppBlockerEmptyText("暂无应用组")
            }
        } else {
            items(allGroups, key = { "group_${it.id}" }) { group ->
                AppGroupCard(
                    group = group,
                    rules = allRules.filter {
                        it.targetType == BlockTimeRule.TARGET_TYPE_GROUP &&
                            it.targetId == group.id.toString()
                    },
                    globalLock = globalLock,
                    onToggleEnabled = { vm.toggleGroupEnabled(group) },
                    onEdit = { vm.loadGroupForEdit(group) },
                    onDelete = { vm.deleteGroup(group) },
                    onLock = { onLockGroup(group) },
                    onAddRule = {
                        vm.resetRuleForm()
                        vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_GROUP
                        vm.ruleTargetId = group.id.toString()
                        vm.showRuleEditor = true
                    },
                    onAddApps = {
                        vm.loadGroupForEdit(group, AppBlockerVm.GroupEditorMode.AppendApps)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        item(key = "spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item(key = "app_rules_header") {
            AppBlockerSectionHeader(
                title = li.songe.gkd.sdp.app.getString(R.string.s_74c7776c98),
                onAdd = {
                    vm.resetRuleForm()
                    vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_APP
                    vm.showRuleEditor = true
                },
            )
        }
        val appRules = allRules.filter { it.targetType == BlockTimeRule.TARGET_TYPE_APP }
        if (appRules.isEmpty()) {
            item(key = "no_app_rules") {
                AppBlockerEmptyText("暂无单独应用规则")
            }
        } else {
            appRules.groupBy { it.targetId }.forEach { (packageName, rules) ->
                item(key = "app_$packageName") {
                    AppRulesCard(
                        packageName = packageName,
                        rules = rules,
                        onToggleEnabled = { vm.toggleRuleEnabled(it) },
                        onEdit = { vm.loadRuleForEdit(it) },
                        onDelete = { vm.deleteRule(it) },
                        onLock = onLockRule,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AppBlockerAutoReenableNotice() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        colors = surfaceCardColors,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.s_b2d1d6afd6),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.s_ebf718dc74),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun AppBlockerGlobalLockStatus(globalLock: li.songe.gkd.sdp.data.AppBlockerLock) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        colors = surfaceCardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PerfIcon.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.s_1640da6876),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val remainingMinutes =
                    ((globalLock.lockEndTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                Text(
                    text = stringResource(R.string.s_7c36cdf41a, if (remainingMinutes >= 60) "${remainingMinutes / 60}小时${remainingMinutes % 60}分钟" else "${remainingMinutes}分钟"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun AppBlockerSectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onAdd) {
            Icon(PerfIcon.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.s_94191ce210))
        }
    }
}

@Composable
private fun AppBlockerEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.itemPadding(),
    )
}
