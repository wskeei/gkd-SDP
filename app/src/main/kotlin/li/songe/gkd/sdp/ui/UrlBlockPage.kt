package li.songe.gkd.sdp.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun UrlBlockPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UrlBlockVm>()
    
    val allGroups by vm.allGroupsFlow.collectAsState()
    val allUrlRules by vm.allUrlRulesFlow.collectAsState()
    val allTimeRules by vm.allTimeRulesFlow.collectAsState()
    val globalLock by vm.globalLockFlow.collectAsState()
    val browsers by vm.browsersFlow.collectAsState()

    var showGlobalLockSheet by remember { mutableStateOf(false) }
    var showGroupLockSheet by remember { mutableStateOf(false) }
    var showTimeRuleLockSheet by remember { mutableStateOf(false) }
    
    var lockTargetGroup by remember { mutableStateOf<UrlRuleGroup?>(null) }
    var lockTargetTimeRule by remember { mutableStateOf<UrlTimeRule?>(null) }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popBackStack() },
                    )
                },
                title = { Text(text = "网址拦截") },
                actions = {
                    // 浏览器适配按钮
                    IconButton(onClick = { vm.showBrowserList = true }) {
                        Icon(
                            PerfIcon.Settings,
                            contentDescription = "浏览器适配",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 全局锁定按钮
                    IconButton(onClick = { showGlobalLockSheet = true }) {
                        Icon(
                            PerfIcon.Lock,
                            contentDescription = "全局锁定",
                            tint = if (globalLock?.isCurrentlyLocked == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            // 全局锁定状态提示
            if (globalLock?.isCurrentlyLocked == true) {
                item(key = "global_lock_status") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .itemPadding(),
                        colors = surfaceCardColors
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(PerfIcon.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "全局锁定中",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val remainingMinutes = ((globalLock!!.lockEndTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                                Text(
                                    text = "剩余 ${if (remainingMinutes >= 60) "${remainingMinutes / 60}小时${remainingMinutes % 60}分钟" else "${remainingMinutes}分钟"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ================== 规则组 ==================
            item(key = "groups_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "规则组 (${allGroups.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        vm.resetGroupForm()
                        vm.showGroupEditor = true
                    }) {
                        Icon(PerfIcon.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            }

            if (allGroups.isEmpty()) {
                item(key = "no_groups") {
                    Text(
                        text = "暂无规则组",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                items(allGroups, key = { "group_${it.id}" }) { group ->
                    val groupTimeRules = allTimeRules.filter {
                        it.targetType == UrlTimeRule.TARGET_TYPE_GROUP &&
                        it.targetId == group.id
                    }
                    
                    UrlGroupCard(
                        group = group,
                        rules = groupTimeRules,
                        onToggleEnabled = { vm.toggleGroupEnabled(group) },
                        onEdit = {
                            vm.loadGroupForEdit(group)
                            vm.showGroupEditor = true
                        },
                        onDelete = { vm.deleteGroup(group) },
                        onLock = {
                            lockTargetGroup = group
                            showGroupLockSheet = true
                        },
                        onAddTimeRule = {
                            vm.resetTimeRuleForm()
                            vm.timeRuleTargetType = UrlTimeRule.TARGET_TYPE_GROUP
                            vm.timeRuleTargetId = group.id
                            vm.showTimeRuleEditor = true
                        },
                        onTimeRuleEdit = { tr ->
                            vm.loadTimeRuleForEdit(tr)
                            vm.showTimeRuleEditor = true
                        },
                        onTimeRuleDelete = { vm.deleteTimeRule(it) },
                        onTimeRuleLock = { tr ->
                            lockTargetTimeRule = tr
                            showTimeRuleLockSheet = true
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item(key = "spacer_groups") {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ================== 独立规则 ==================
            item(key = "rules_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "独立规则",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        vm.resetUrlForm()
                        vm.showUrlEditor = true
                    }) {
                        Icon(PerfIcon.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            }

            // 过滤出未分组的规则 (groupId = 0)
            val standaloneRules = allUrlRules.filter { it.groupId == 0L }
            
            if (standaloneRules.isEmpty()) {
                item(key = "no_rules") {
                    Text(
                        text = "暂无独立规则",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                items(standaloneRules, key = { "rule_${it.id}" }) { rule ->
                    val ruleTimeRules = allTimeRules.filter {
                        it.targetType == UrlTimeRule.TARGET_TYPE_RULE &&
                        it.targetId == rule.id
                    }
                    
                    UrlItemCard(
                        rule = rule,
                        timeRules = ruleTimeRules,
                        onToggleEnabled = { vm.toggleUrlRuleEnabled(rule) },
                        onEdit = {
                            vm.loadUrlForEdit(rule)
                            vm.showUrlEditor = true
                        },
                        onDelete = { vm.deleteUrlRule(rule) },
                        onAddTimeRule = {
                            vm.resetTimeRuleForm()
                            vm.timeRuleTargetType = UrlTimeRule.TARGET_TYPE_RULE
                            vm.timeRuleTargetId = rule.id
                            vm.showTimeRuleEditor = true
                        },
                        onTimeRuleEdit = { tr ->
                            vm.loadTimeRuleForEdit(tr)
                            vm.showTimeRuleEditor = true
                        },
                        onTimeRuleDelete = { vm.deleteTimeRule(it) },
                        onTimeRuleLock = { tr ->
                            lockTargetTimeRule = tr
                            showTimeRuleLockSheet = true
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // ================== Editors ==================

    if (vm.showGroupEditor) {
        UrlGroupEditorSheet(
            vm = vm,
            onDismiss = { vm.showGroupEditor = false },
            onSave = { 
                vm.saveGroup()
                vm.showGroupEditor = false 
            }
        )
    }

    if (vm.showUrlEditor) {
        UrlRuleEditorSheet(
            vm = vm,
            allGroups = allGroups,
            onDismiss = { vm.showUrlEditor = false },
            onSave = { 
                vm.saveUrlRule()
                vm.showUrlEditor = false
            }
        )
    }
    
    if (vm.showTimeRuleEditor) {
        TimeRuleEditorSheet(
            vm = vm,
            onDismiss = { vm.showTimeRuleEditor = false },
            onSave = {
                vm.saveTimeRule()
                vm.showTimeRuleEditor = false
            }
        )
    }

    if (vm.showBrowserList) {
        BrowserListSheet(
            browsers = browsers,
            onDismiss = { vm.showBrowserList = false },
            onAdd = {
                vm.resetBrowserForm()
                vm.showBrowserEditor = true
            },
            onEdit = { browser ->
                vm.loadBrowserForEdit(browser)
                vm.showBrowserEditor = true
            },
            onDelete = { vm.deleteBrowser(it) },
            onToggle = { vm.toggleBrowserEnabled(it) }
        )
    }

    if (vm.showBrowserEditor) {
        BrowserEditSheet(
            vm = vm,
            onDismiss = { vm.showBrowserEditor = false },
            onSave = {
                vm.saveBrowser()
                vm.showBrowserEditor = false
            }
        )
    }

    // ================== Locking Sheets ==================

    // 全局锁定 Sheet
    if (showGlobalLockSheet) {
        UrlLockSheet(
            title = if (globalLock?.isCurrentlyLocked == true) "延长全局锁定" else "全局锁定",
            description = "锁定后无法删除或修改任何规则/组，但可以新增。",
            currentLockEndTime = globalLock?.lockEndTime,
            vm = vm,
            onDismiss = { showGlobalLockSheet = false },
            onLock = {
                vm.lockGlobal()
                showGlobalLockSheet = false
            }
        )
    }

    // 规则组锁定 Sheet
    if (showGroupLockSheet && lockTargetGroup != null) {
        UrlLockSheet(
            title = if (lockTargetGroup!!.isCurrentlyLocked) "延长锁定" else "锁定规则组",
            description = "锁定后无法关闭、删除或修改此规则组。",
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

    // 时间规则锁定 Sheet
    if (showTimeRuleLockSheet && lockTargetTimeRule != null) {
        UrlLockSheet(
            title = if (lockTargetTimeRule!!.isCurrentlyLocked) "延长锁定" else "锁定时间规则",
            description = "锁定后无法关闭、删除或修改此时间规则。",
            currentLockEndTime = if (lockTargetTimeRule!!.isCurrentlyLocked) lockTargetTimeRule!!.lockEndTime else null,
            vm = vm,
            onDismiss = {
                showTimeRuleLockSheet = false
                lockTargetTimeRule = null
            },
            onLock = {
                // UrlBlockVm needs a lockTimeRule method that accepts just the rule
                // Wait, UrlBlockVm.lockTimeRule in Task 2 was defined but needs to check its signature.
                // In Task 2 result: `lockTimeRule(rule: UrlTimeRule)` uses internal `calculateLockEndTime()`.
                // So it just takes the rule.
                vm.lockTimeRule(lockTargetTimeRule!!)
                showTimeRuleLockSheet = false
                lockTargetTimeRule = null
            }
        )
    }
}