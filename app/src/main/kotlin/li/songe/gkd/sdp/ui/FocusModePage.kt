package li.songe.gkd.sdp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.appInfoMapFlow

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun FocusModePage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<FocusModeVm>()
    val allRules by vm.allRulesFlow.collectAsState()
    val activeSession by vm.activeSessionFlow.collectAsState()
    val isActive by vm.isActiveFlow.collectAsState()
    val currentWhitelist by vm.currentWhitelistFlow.collectAsState()

    var showQuickStartSheet by remember { mutableStateOf(false) }
    var showRuleEditorSheet by remember { mutableStateOf(false) }
    var showWhitelistPicker by remember { mutableStateOf(false) }
    var showWechatContactPicker by remember { mutableStateOf(false) }
    var showLockSheet by remember { mutableStateOf(false) }
    var lockTargetRule by remember { mutableStateOf<FocusRule?>(null) }
    var whitelistPickerMode by remember { mutableStateOf("rule") } // "rule" or "manual"
    var wechatContactPickerMode by remember { mutableStateOf("rule") } // "rule" or "manual"

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popBackStack() },
                    )
                },
                title = { Text(text = "专注模式") },
                actions = {
                    IconButton(onClick = {
                        vm.resetRuleForm()
                        showRuleEditorSheet = true
                    }) {
                        Icon(PerfIcon.Add, contentDescription = "添加规则")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            // 当前状态卡片
            item(key = "status") {
                ActiveSessionCard(
                    session = activeSession,
                    isActive = isActive,
                    currentWhitelist = currentWhitelist,
                    onStop = { vm.stopManualSession() },
                    onRemoveWhitelist = { vm.removeFromSessionWhitelist(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 快速启动按钮
            if (!isActive) {
                item(key = "quick_start") {
                    Button(
                        onClick = { showQuickStartSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .itemPadding()
                    ) {
                        Text("立即开始专注")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 规则列表标题
            item(key = "rules_header") {
                Text(
                    text = "定时规则",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.itemPadding()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (allRules.isEmpty()) {
                item(key = "no_rules") {
                    Text(
                        text = "暂无定时规则，点击右上角 + 添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                items(allRules, key = { "rule_${it.id}" }) { rule ->
                    FocusRuleCard(
                        rule = rule,
                        onToggleEnabled = { vm.toggleRuleEnabled(rule) },
                        onEdit = {
                            vm.loadRuleForEdit(rule)
                            showRuleEditorSheet = true
                        },
                        onDelete = { vm.deleteRule(rule) },
                        onLock = {
                            lockTargetRule = rule
                            showLockSheet = true
                        },
                        onStart = { vm.startQuickRule(rule) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // 快速启动 Sheet
    if (showQuickStartSheet) {
        QuickStartSheet(
            vm = vm,
            onDismiss = { showQuickStartSheet = false },
            onShowWhitelistPicker = {
                whitelistPickerMode = "manual"
                showWhitelistPicker = true
            },
            onStart = {
                vm.startManualSession()
                showQuickStartSheet = false
            }
        )
    }

    // 规则编辑 Sheet
    if (showRuleEditorSheet || vm.showRuleEditor) {
        RuleEditorSheet(
            vm = vm,
            onDismiss = {
                showRuleEditorSheet = false
                vm.resetRuleForm()
            },
            onShowWhitelistPicker = {
                whitelistPickerMode = "rule"
                showWhitelistPicker = true
            },
            onShowWechatContactPicker = {
                wechatContactPickerMode = "rule"
                showWechatContactPicker = true
            },
            onSave = {
                vm.saveRule()
                showRuleEditorSheet = false
            }
        )
    }

    // 白名单选择器
    if (showWhitelistPicker) {
        WhitelistPickerDialog(
            currentWhitelist = if (whitelistPickerMode == "rule") vm.ruleWhitelistApps else vm.manualWhitelistApps,
            onDismiss = { showWhitelistPicker = false },
            onConfirm = { selected ->
                if (whitelistPickerMode == "rule") {
                    vm.ruleWhitelistApps = selected
                } else {
                    vm.manualWhitelistApps = selected
                }
                showWhitelistPicker = false
            }
        )
    }

    // 微信联系人选择器
    if (showWechatContactPicker) {
        WechatContactPickerDialog(
            currentWhitelist = if (wechatContactPickerMode == "rule") vm.ruleWechatWhitelist else vm.manualWechatWhitelist,
            allContacts = vm.allWechatContactsFlow.collectAsState().value,
            onDismiss = { showWechatContactPicker = false },
            onConfirm = { selected ->
                if (wechatContactPickerMode == "rule") {
                    vm.ruleWechatWhitelist = selected
                } else {
                    vm.manualWechatWhitelist = selected
                }
                showWechatContactPicker = false
            }
        )
    }

    // 锁定 Sheet
    if (showLockSheet && lockTargetRule != null) {
        LockRuleSheet(
            vm = vm,
            rule = lockTargetRule!!,
            onDismiss = {
                showLockSheet = false
                lockTargetRule = null
            },
            onLock = {
                vm.lockRule(lockTargetRule!!)
                showLockSheet = false
                lockTargetRule = null
            }
        )
    }
}

@Composable
private fun ActiveSessionCard(
    session: FocusSession?,
    isActive: Boolean,
    currentWhitelist: List<String>,
    onStop: () -> Unit,
    onRemoveWhitelist: (String) -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    PerfIcon.Mindful,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActive) "专注模式进行中" else "专注模式未启动",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isActive && session?.isValidNow() == true) {
                        val remainingMinutes = session.getRemainingTime() / 60000
                        Text(
                            text = if (remainingMinutes >= 60) {
                                "剩余 ${remainingMinutes / 60} 小时 ${remainingMinutes % 60} 分钟"
                            } else {
                                "剩余 $remainingMinutes 分钟"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (session.isCurrentlyLocked) {
                            Text(
                                text = "（已锁定）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                if (isActive && session?.isManual == true && !session.isCurrentlyLocked) {
                    OutlinedButton(onClick = onStop) {
                        Text("结束")
                    }
                }
            }

            if (isActive && currentWhitelist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "白名单应用（点击移除）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                currentWhitelist.forEach { packageName ->
                    WhitelistAppRow(
                        packageName = packageName,
                        canRemove = session?.isCurrentlyLocked != true,
                        onRemove = { onRemoveWhitelist(packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WhitelistAppRow(
    packageName: String,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val appName = remember(packageName) {
        try {
            val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
            app.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        AppIcon(appId = packageName)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = appName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(PerfIcon.Close, contentDescription = "移除")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusRuleCard(
    rule: FocusRule,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onStart: () -> Unit  // 快速启动
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
            .clickable(onClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (rule.isQuickStart) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "快速启动",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (rule.isCurrentlyLocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                PerfIcon.Lock,
                                contentDescription = "已锁定",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        if (!rule.isQuickStart && rule.isActiveNow()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "进行中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // 根据规则类型显示不同信息
                    if (rule.isQuickStart) {
                        Text(
                            text = "时长：${rule.formatDuration()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (rule.isLocked) {
                            Text(
                                text = "启动后锁定",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Text(
                            text = "${rule.startTime} - ${rule.endTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = rule.formatDaysOfWeek(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (rule.isQuickStart) {
                    // 快速启动：显示开始按钮
                    Button(onClick = onStart) {
                        Text("开始")
                    }
                } else {
                    // 定时规则：显示开关
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggleEnabled() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!rule.isQuickStart) {
                    TextButton(onClick = onLock) {
                        Icon(PerfIcon.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (rule.isCurrentlyLocked) "延长锁定" else "锁定")
                    }
                }
                if (!rule.isCurrentlyLocked) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Icon(PerfIcon.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除规则") },
            text = { Text("确定要删除规则「${rule.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickStartSheet(
    vm: FocusModeVm,
    onDismiss: () -> Unit,
    onShowWhitelistPicker: () -> Unit,
    onStart: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "立即开始专注",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 时长选择
            Text(
                text = "专注时长",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.manualHours.toString(),
                    onValueChange = {
                        val hours = it.toIntOrNull()?.coerceIn(0, 48) ?: 0
                        vm.manualHours = hours
                    },
                    label = { Text("小时") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = vm.manualMinutes.toString(),
                    onValueChange = {
                        val minutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        vm.manualMinutes = minutes
                    },
                    label = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // 显示验证提示
            if (vm.totalDurationMinutes < 5) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "最短时长为 5 分钟",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 拦截消息
            OutlinedTextField(
                value = vm.manualMessage,
                onValueChange = { vm.manualMessage = it },
                label = { Text("拦截提示语") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 白名单
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "白名单应用 (${vm.manualWhitelistApps.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShowWhitelistPicker) {
                    Text("选择")
                }
            }

            if (vm.manualWhitelistApps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.manualWhitelistApps.forEach { packageName ->
                        val appName = remember(packageName) {
                            try {
                                val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
                                app.packageManager.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                packageName.split(".").lastOrNull() ?: packageName
                            }
                        }
                        FilterChip(
                            selected = true,
                            onClick = { vm.removeFromManualWhitelist(packageName) },
                            label = { Text(appName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 锁定选项
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = vm.manualIsLocked,
                    onCheckedChange = { vm.manualIsLocked = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("锁定（无法提前结束）")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("开始专注")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorSheet(
    vm: FocusModeVm,
    onDismiss: () -> Unit,
    onShowWhitelistPicker: () -> Unit,
    onShowWechatContactPicker: () -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (vm.editingRule != null) "编辑规则" else "添加规则",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 规则名称
            OutlinedTextField(
                value = vm.ruleName,
                onValueChange = { vm.ruleName = it },
                label = { Text("规则名称") },
                placeholder = { Text("如：晚间复盘") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 规则类型选择
            Text(
                text = "规则类型",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = vm.ruleType == FocusRule.RULE_TYPE_QUICK_START,
                    onClick = { vm.ruleType = FocusRule.RULE_TYPE_QUICK_START },
                    label = { Text("快速启动") }
                )
                FilterChip(
                    selected = vm.ruleType == FocusRule.RULE_TYPE_SCHEDULED,
                    onClick = { vm.ruleType = FocusRule.RULE_TYPE_SCHEDULED },
                    label = { Text("定时规则") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 根据规则类型显示不同的输入
            if (vm.ruleType == FocusRule.RULE_TYPE_QUICK_START) {
                // 快速启动：时长输入
                Text(
                    text = "专注时长",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = vm.ruleDurationHours.toString(),
                        onValueChange = {
                            vm.ruleDurationHours = it.toIntOrNull()?.coerceIn(0, 48) ?: 0
                        },
                        label = { Text("小时") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = vm.ruleDurationMinutes.toString(),
                        onValueChange = {
                            vm.ruleDurationMinutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        },
                        label = { Text("分钟") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                if (vm.ruleTotalDurationMinutes < 5) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "最短时长为 5 分钟",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 锁定选项
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = vm.ruleIsLocked,
                        onCheckedChange = { vm.ruleIsLocked = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("锁定（无法提前结束）")
                }
            } else {
                // 定时规则：时间段
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = vm.ruleStartTime,
                        onValueChange = { vm.ruleStartTime = it },
                        label = { Text("开始时间") },
                        placeholder = { Text("22:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = vm.ruleEndTime,
                        onValueChange = { vm.ruleEndTime = it },
                        label = { Text("结束时间") },
                        placeholder = { Text("23:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 星期选择
                Text(
                    text = "生效日期",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = vm.ruleDaysOfWeek.contains(day),
                            onClick = {
                                vm.ruleDaysOfWeek = if (vm.ruleDaysOfWeek.contains(day)) {
                                    vm.ruleDaysOfWeek - day
                                } else {
                                    (vm.ruleDaysOfWeek + day).sorted()
                                }
                            },
                            label = { Text("周${dayNames[day - 1]}") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 拦截消息
            OutlinedTextField(
                value = vm.ruleInterceptMessage,
                onValueChange = { vm.ruleInterceptMessage = it },
                label = { Text("拦截提示语") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 白名单
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "白名单应用 (${vm.ruleWhitelistApps.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShowWhitelistPicker) {
                    Text("选择")
                }
            }

            if (vm.ruleWhitelistApps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.ruleWhitelistApps.forEach { packageName ->
                        val appName = remember(packageName) {
                            try {
                                val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
                                app.packageManager.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                packageName.split(".").lastOrNull() ?: packageName
                            }
                        }
                        FilterChip(
                            selected = true,
                            onClick = { vm.removeFromRuleWhitelist(packageName) },
                            label = { Text(appName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 微信联系人白名单
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "微信联系人白名单 (${vm.ruleWechatWhitelist.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShowWechatContactPicker) {
                    Text("选择")
                }
            }

            if (vm.ruleWechatWhitelist.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.ruleWechatWhitelist.forEach { wechatId ->
                        val contact = remember(wechatId) {
                            try {
                                kotlinx.coroutines.runBlocking {
                                    li.songe.gkd.sdp.db.DbSet.wechatContactDao.getByIds(listOf(wechatId)).firstOrNull()
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        val displayName = contact?.displayName ?: wechatId
                        FilterChip(
                            selected = true,
                            onClick = { vm.removeFromRuleWechatWhitelist(wechatId) },
                            label = { Text(displayName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WhitelistPickerDialog(
    currentWhitelist: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selectedApps by remember { mutableStateOf(currentWhitelist.toSet()) }
    val appInfoMap by appInfoMapFlow.collectAsState()
    val vm = viewModel<FocusModeVm>()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择白名单应用") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 搜索框
                OutlinedTextField(
                    value = vm.whitelistSearchQuery,
                    onValueChange = { vm.whitelistSearchQuery = it },
                    placeholder = { Text("搜索应用") },
                    leadingIcon = { Icon(PerfIcon.Search, null) },
                    trailingIcon = {
                        if (vm.whitelistSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.whitelistSearchQuery = "" }) {
                                Icon(PerfIcon.Close, "清除")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 系统应用开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.showSystemAppsInWhitelist = !vm.showSystemAppsInWhitelist }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("显示系统应用")
                    Switch(
                        checked = vm.showSystemAppsInWhitelist,
                        onCheckedChange = { vm.showSystemAppsInWhitelist = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 应用列表
                LazyColumn {
                    items(
                        appInfoMap.values
                            .filterNot { !vm.showSystemAppsInWhitelist && it.isSystem }
                            .filter { appInfo ->
                                if (vm.whitelistSearchQuery.isBlank()) {
                                    !appInfo.hidden
                                } else {
                                    !appInfo.hidden && (
                                        appInfo.name.contains(vm.whitelistSearchQuery, ignoreCase = true) ||
                                        appInfo.id.contains(vm.whitelistSearchQuery, ignoreCase = true)
                                    )
                                }
                            }
                            .sortedBy { it.name }
                            .toList()
                    ) { appInfo ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedApps = if (selectedApps.contains(appInfo.id)) {
                                        selectedApps - appInfo.id
                                    } else {
                                        selectedApps + appInfo.id
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = selectedApps.contains(appInfo.id),
                                onCheckedChange = {
                                    selectedApps = if (it) {
                                        selectedApps + appInfo.id
                                    } else {
                                        selectedApps - appInfo.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppIcon(appId = appInfo.id)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = appInfo.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = appInfo.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedApps.toList()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockRuleSheet(
    vm: FocusModeVm,
    rule: FocusRule,
    onDismiss: () -> Unit,
    onLock: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (rule.isCurrentlyLocked) "延长锁定" else "锁定规则",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "锁定后无法关闭或删除此规则",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (rule.isCurrentlyLocked) {
                val remainingMinutes = ((rule.lockEndTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                Text(
                    text = "当前剩余: ${if (remainingMinutes >= 60) "${remainingMinutes / 60}小时${remainingMinutes % 60}分钟" else "${remainingMinutes}分钟"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 预设时长
            val presets = listOf(
                480 to "8小时",
                1440 to "1天",
                4320 to "3天"
            )
            presets.forEach { (minutes, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.selectedLockDuration = minutes
                            vm.isCustomLockDuration = false
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Checkbox(
                        checked = !vm.isCustomLockDuration && vm.selectedLockDuration == minutes,
                        onCheckedChange = {
                            vm.selectedLockDuration = minutes
                            vm.isCustomLockDuration = false
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label)
                }
            }

            // 自定义时长
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.isCustomLockDuration = true }
                    .padding(vertical = 12.dp)
            ) {
                Checkbox(
                    checked = vm.isCustomLockDuration,
                    onCheckedChange = { vm.isCustomLockDuration = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("自定义")
            }

            if (vm.isCustomLockDuration) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = vm.customLockDaysText,
                        onValueChange = { vm.customLockDaysText = it },
                        label = { Text("天") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = vm.customLockHoursText,
                        onValueChange = { vm.customLockHoursText = it },
                        label = { Text("小时") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onLock,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (rule.isCurrentlyLocked) "延长锁定" else "确认锁定")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WechatContactPickerDialog(
    currentWhitelist: List<String>,
    allContacts: List<li.songe.gkd.sdp.data.WechatContact>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selectedContacts by remember { mutableStateOf(currentWhitelist.toSet()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(allContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            allContacts
        } else {
            allContacts.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                        it.wechatId.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择微信联系人") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索联系人") },
                    leadingIcon = { Icon(PerfIcon.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(PerfIcon.Close, "清除")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredContacts.isEmpty()) {
                    Text(
                        text = if (allContacts.isEmpty()) "暂无联系人，请先更新微信联系人" else "未找到匹配的联系人",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(filteredContacts, key = { it.wechatId }) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedContacts = if (contact.wechatId in selectedContacts) {
                                            selectedContacts - contact.wechatId
                                        } else {
                                            selectedContacts + contact.wechatId
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = contact.wechatId in selectedContacts,
                                    onCheckedChange = {
                                        selectedContacts = if (it) {
                                            selectedContacts + contact.wechatId
                                        } else {
                                            selectedContacts - contact.wechatId
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "微信号: ${contact.wechatId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedContacts.toList()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
