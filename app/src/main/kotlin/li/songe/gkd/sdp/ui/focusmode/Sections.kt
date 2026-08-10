@file:JvmName("FocusModeSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable
fun FocusModePageSections() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<FocusModeVm>()
    val allRules by vm.allRulesFlow.collectAsStateWithLifecycle()
    val quickStartRules = remember(allRules) { allRules.filter { it.isQuickStart } }
    val scheduledRules = remember(allRules) { allRules.filterNot { it.isQuickStart } }
    val activeSession by vm.activeSessionFlow.collectAsStateWithLifecycle()
    val isActive by vm.isActiveFlow.collectAsStateWithLifecycle()
    val currentWhitelist by vm.currentWhitelistFlow.collectAsStateWithLifecycle()

    var showQuickStartSheet by remember { mutableStateOf(false) }
    var showRuleEditorSheet by remember { mutableStateOf(false) }
    var showWhitelistPicker by remember { mutableStateOf(false) }
    var showLockSheet by remember { mutableStateOf(false) }
    var lockTargetRule by remember { mutableStateOf<FocusRule?>(null) }
    var whitelistPickerMode by remember { mutableStateOf("rule") } // "rule" or "manual"

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popPage() },
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
        FocusModeRulesList(
            padding = padding,
            vm = vm,
            quickStartRules = quickStartRules,
            scheduledRules = scheduledRules,
            activeSession = activeSession,
            isActive = isActive,
            currentWhitelist = currentWhitelist,
            onQuickStart = { showQuickStartSheet = true },
            onEdit = { rule -> vm.loadRuleForEdit(rule); showRuleEditorSheet = true },
            onLock = { rule -> lockTargetRule = rule; showLockSheet = true },
        )
    }

    FocusModeSheets(
        vm = vm,
        showQuickStartSheet = showQuickStartSheet,
        showRuleEditorSheet = showRuleEditorSheet,
        showWhitelistPicker = showWhitelistPicker,
        showLockSheet = showLockSheet,
        lockTargetRule = lockTargetRule,
        whitelistPickerMode = whitelistPickerMode,
        setQuickStartSheet = { showQuickStartSheet = it },
        setRuleEditorSheet = { showRuleEditorSheet = it },
        setWhitelistPicker = { showWhitelistPicker = it },
        setLockSheet = { showLockSheet = it },
        setLockTargetRule = { lockTargetRule = it },
        setWhitelistPickerMode = { whitelistPickerMode = it },
    )
}

@Composable
private fun FocusModeRulesList(
    padding: androidx.compose.foundation.layout.PaddingValues,
    vm: FocusModeVm,
    quickStartRules: List<FocusRule>,
    scheduledRules: List<FocusRule>,
    activeSession: FocusSession?,
    isActive: Boolean,
    currentWhitelist: List<String>,
    onQuickStart: () -> Unit,
    onEdit: (FocusRule) -> Unit,
    onLock: (FocusRule) -> Unit,
) {
    LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
        item(key = "status") {
            ActiveSessionCard(
                session = activeSession,
                isActive = isActive,
                currentWhitelist = currentWhitelist,
                onStop = { vm.stopManualSession() },
                onRemoveWhitelist = vm::removeFromSessionWhitelist,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (!isActive) {
            item(key = "quick_start") {
                Button(onClick = onQuickStart, modifier = Modifier.fillMaxWidth().itemPadding()) { Text("立即开始专注") }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        item(key = "quick_rules_header") {
            Text("快速启动模板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.itemPadding())
            Spacer(modifier = Modifier.height(8.dp))
        }
        FocusModeRuleItems(
            rules = quickStartRules,
            emptyText = "暂无快速启动模板，点击右上角 + 添加",
            vm = vm,
            onEdit = onEdit,
            onLock = onLock,
        )
        item(key = "scheduled_rules_header") {
            Text("定时规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.itemPadding())
            Spacer(modifier = Modifier.height(8.dp))
        }
        FocusModeRuleItems(
            rules = scheduledRules,
            emptyText = "暂无定时规则，点击右上角 + 添加",
            vm = vm,
            onEdit = onEdit,
            onLock = onLock,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.FocusModeRuleItems(
    rules: List<FocusRule>,
    emptyText: String,
    vm: FocusModeVm,
    onEdit: (FocusRule) -> Unit,
    onLock: (FocusRule) -> Unit,
) {
    if (rules.isEmpty()) {
        item {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.itemPadding())
            Spacer(modifier = Modifier.height(16.dp))
        }
    } else {
        items(rules, key = { "rule_${it.id}" }) { rule ->
            FocusRuleCard(
                rule = rule,
                onToggleEnabled = { vm.toggleRuleEnabled(rule) },
                onEdit = { onEdit(rule) },
                onDelete = { vm.deleteRule(rule) },
                onLock = { onLock(rule) },
                onStart = { vm.startQuickRule(rule) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FocusModeSheets(
    vm: FocusModeVm,
    showQuickStartSheet: Boolean,
    showRuleEditorSheet: Boolean,
    showWhitelistPicker: Boolean,
    showLockSheet: Boolean,
    lockTargetRule: FocusRule?,
    whitelistPickerMode: String,
    setQuickStartSheet: (Boolean) -> Unit,
    setRuleEditorSheet: (Boolean) -> Unit,
    setWhitelistPicker: (Boolean) -> Unit,
    setLockSheet: (Boolean) -> Unit,
    setLockTargetRule: (FocusRule?) -> Unit,
    setWhitelistPickerMode: (String) -> Unit,
) {
    if (showQuickStartSheet) {
        QuickStartSheet(
            vm = vm,
            onDismiss = { setQuickStartSheet(false) },
            onShowWhitelistPicker = { setWhitelistPickerMode("manual"); setWhitelistPicker(true) },
            onStart = { vm.startManualSession(); setQuickStartSheet(false) },
        )
    }
    if (showRuleEditorSheet || vm.showRuleEditor) {
        RuleEditorSheet(
            vm = vm,
            onDismiss = { setRuleEditorSheet(false); vm.resetRuleForm() },
            onShowWhitelistPicker = { setWhitelistPickerMode("rule"); setWhitelistPicker(true) },
            onSave = { vm.saveRule(); setRuleEditorSheet(false) },
        )
    }
    if (showWhitelistPicker) {
        WhitelistPickerDialog(
            currentWhitelist = if (whitelistPickerMode == "rule") vm.ruleWhitelistApps else vm.manualWhitelistApps,
            onDismiss = { setWhitelistPicker(false) },
            onConfirm = { selected ->
                if (whitelistPickerMode == "rule") vm.ruleWhitelistApps = selected else vm.manualWhitelistApps = selected
                setWhitelistPicker(false)
            },
        )
    }
    if (showLockSheet && lockTargetRule != null) {
        LockRuleSheet(
            vm = vm,
            rule = lockTargetRule,
            onDismiss = { setLockSheet(false); setLockTargetRule(null) },
            onLock = { vm.lockRule(lockTargetRule); setLockSheet(false); setLockTargetRule(null) },
        )
    }
}


@Composable
internal fun ActiveSessionCard(
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
internal fun WhitelistAppRow(
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
