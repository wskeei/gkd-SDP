package li.songe.gkd.sdp.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.UrlBlockRule
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
    val rules by vm.rulesFlow.collectAsState()
    val browsers by vm.browsersFlow.collectAsState()
    val enabledRuleCount by vm.enabledRuleCountFlow.collectAsState()
    val enabledBrowserCount by vm.enabledBrowserCountFlow.collectAsState()
    val urlBlockerEnabled by UrlBlockerEngine.enabledFlow.collectAsState()
    val isLocked by vm.isLockedFlow.collectAsState()
    val lockEndTime by vm.lockEndTimeFlow.collectAsState()

    val scope = rememberCoroutineScope()
    val ruleSheetState = rememberModalBottomSheetState()
    val browserSheetState = rememberModalBottomSheetState()
    val lockSheetState = rememberModalBottomSheetState()

    var showRuleSheet by remember { mutableStateOf(false) }
    var showBrowserSheet by remember { mutableStateOf(false) }
    var showBrowserListDialog by remember { mutableStateOf(false) }
    var showLockSheet by remember { mutableStateOf(false) }
    var deleteConfirmRule by remember { mutableStateOf<UrlBlockRule?>(null) }

    // 当前 Tab: 0=规则, 1=浏览器
    var currentTab by remember { mutableIntStateOf(0) }

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
                    // 锁定按钮
                    IconButton(onClick = { showLockSheet = true }) {
                        PerfIcon(
                            imageVector = PerfIcon.Lock,
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showBrowserListDialog = true }) {
                        PerfIcon(
                            imageVector = PerfIcon.Settings,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    vm.resetRuleForm()
                    showRuleSheet = true
                }
            ) {
                PerfIcon(imageVector = PerfIcon.Add)
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            // 锁定状态提示
            if (isLocked) {
                item {
                    LockStatusCard(lockEndTime = lockEndTime)
                }
            }

            // 功能开关
            item {
                ElevatedCard(
                    colors = surfaceCardColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用网址拦截",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "已启用 $enabledRuleCount 条规则, $enabledBrowserCount 个浏览器",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = urlBlockerEnabled,
                            onCheckedChange = { vm.toggleUrlBlockerEnabled(it) },
                            enabled = !isLocked || !urlBlockerEnabled  // 锁定时只能开不能关
                        )
                    }
                }
            }

            // 规则列表标题
            item {
                Text(
                    text = "拦截规则",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        text = "暂无规则，点击右下角添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.itemPadding()
                    )
                }
            }

            items(rules, key = { it.id }) { rule ->
                UrlRuleItem(
                    rule = rule,
                    onToggleEnabled = { vm.toggleRuleEnabled(rule) },
                    onEdit = {
                        vm.loadRuleForEdit(rule)
                        showRuleSheet = true
                    },
                    onDelete = { deleteConfirmRule = rule }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // 规则编辑 Sheet
        if (showRuleSheet) {
            ModalBottomSheet(
                onDismissRequest = { showRuleSheet = false },
                sheetState = ruleSheetState
            ) {
                RuleEditSheet(
                    vm = vm,
                    onSave = {
                        vm.saveRule()
                        scope.launch { ruleSheetState.hide() }.invokeOnCompletion {
                            if (!ruleSheetState.isVisible) showRuleSheet = false
                        }
                    }
                )
            }
        }

        // 浏览器列表 Dialog
        if (showBrowserListDialog) {
            BrowserListDialog(
                browsers = browsers,
                onDismiss = { showBrowserListDialog = false },
                onToggleEnabled = { vm.toggleBrowserEnabled(it) },
                onEdit = { browser ->
                    vm.loadBrowserForEdit(browser)
                    showBrowserListDialog = false
                    showBrowserSheet = true
                },
                onDelete = { vm.deleteBrowser(it) },
                onAdd = {
                    vm.resetBrowserForm()
                    showBrowserListDialog = false
                    showBrowserSheet = true
                }
            )
        }

        // 浏览器编辑 Sheet
        if (showBrowserSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBrowserSheet = false },
                sheetState = browserSheetState
            ) {
                BrowserEditSheet(
                    vm = vm,
                    onSave = {
                        vm.saveBrowser()
                        scope.launch { browserSheetState.hide() }.invokeOnCompletion {
                            if (!browserSheetState.isVisible) showBrowserSheet = false
                        }
                    }
                )
            }
        }

        // 删除确认 Dialog
        deleteConfirmRule?.let { rule ->
            AlertDialog(
                onDismissRequest = { deleteConfirmRule = null },
                title = { Text("删除规则") },
                text = { Text("确定要删除规则 \"${rule.name}\" 吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.deleteRule(rule)
                            deleteConfirmRule = null
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmRule = null }) {
                        Text("取消")
                    }
                }
            )
        }

        // 锁定时长 Sheet
        if (showLockSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLockSheet = false },
                sheetState = lockSheetState
            ) {
                UrlBlockLockSheet(
                    vm = vm,
                    isLocked = isLocked,
                    lockEndTime = lockEndTime,
                    onConfirm = {
                        vm.lockUrlBlocker()
                        scope.launch { lockSheetState.hide() }.invokeOnCompletion {
                            if (!lockSheetState.isVisible) showLockSheet = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UrlRuleItem(
    rule: UrlBlockRule,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifBlank { rule.pattern },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    Text(
                        text = if (rule.matchType == UrlBlockRule.MATCH_TYPE_DOMAIN) "域名匹配" else "前缀匹配",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    if (rule.showIntercept) {
                        Text(
                            text = " | 全屏拦截",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                PerfIcon(
                    imageVector = PerfIcon.Delete,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditSheet(
    vm: UrlBlockVm,
    onSave: () -> Unit
) {
    val isEditing = vm.editingRule != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = if (isEditing) "编辑规则" else "添加规则",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = vm.ruleName,
            onValueChange = { vm.ruleName = it },
            label = { Text("规则名称") },
            placeholder = { Text("如: B站") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.rulePattern,
            onValueChange = { vm.rulePattern = it },
            label = { Text("匹配模式 *") },
            placeholder = { Text("如: bilibili.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "匹配类型",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = vm.ruleMatchType == UrlBlockRule.MATCH_TYPE_DOMAIN,
                onClick = { vm.ruleMatchType = UrlBlockRule.MATCH_TYPE_DOMAIN },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("域名匹配")
            }
            SegmentedButton(
                selected = vm.ruleMatchType == UrlBlockRule.MATCH_TYPE_PREFIX,
                onClick = { vm.ruleMatchType = UrlBlockRule.MATCH_TYPE_PREFIX },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("前缀匹配")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.ruleRedirectUrl,
            onValueChange = { vm.ruleRedirectUrl = it },
            label = { Text("跳转目标") },
            placeholder = { Text("https://www.google.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("显示全屏拦截", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = vm.ruleShowIntercept,
                onCheckedChange = { vm.ruleShowIntercept = it }
            )
        }

        if (vm.ruleShowIntercept) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.ruleInterceptMessage,
                onValueChange = { vm.ruleInterceptMessage = it },
                label = { Text("拦截提示语") },
                placeholder = { Text("这真的重要吗？") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEditing) "保存修改" else "添加规则")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BrowserListDialog(
    browsers: List<BrowserConfig>,
    onDismiss: () -> Unit,
    onToggleEnabled: (BrowserConfig) -> Unit,
    onEdit: (BrowserConfig) -> Unit,
    onDelete: (BrowserConfig) -> Unit,
    onAdd: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("浏览器配置")
                IconButton(onClick = onAdd) {
                    PerfIcon(imageVector = PerfIcon.Add)
                }
            }
        },
        text = {
            LazyColumn {
                items(browsers, key = { it.packageName }) { browser ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(browser) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = browser.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (browser.isBuiltin) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(内置)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            Text(
                                text = browser.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!browser.isBuiltin) {
                            IconButton(
                                onClick = { onDelete(browser) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                PerfIcon(
                                    imageVector = PerfIcon.Delete,
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Switch(
                            checked = browser.enabled,
                            onCheckedChange = { onToggleEnabled(browser) }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun BrowserEditSheet(
    vm: UrlBlockVm,
    onSave: () -> Unit
) {
    val isEditing = vm.editingBrowser != null
    val isBuiltin = vm.editingBrowser?.isBuiltin == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = if (isEditing) "编辑浏览器" else "添加浏览器",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = vm.browserName,
            onValueChange = { vm.browserName = it },
            label = { Text("浏览器名称") },
            placeholder = { Text("如: Chrome") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.browserPackageName,
            onValueChange = { if (!isBuiltin) vm.browserPackageName = it },
            label = { Text("包名 *") },
            placeholder = { Text("如: com.android.chrome") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isBuiltin
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.browserUrlBarId,
            onValueChange = { vm.browserUrlBarId = it },
            label = { Text("地址栏节点 ID *") },
            placeholder = { Text("如: com.android.chrome:id/url_bar") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "提示: 可使用 GKD 的快照功能查看浏览器地址栏的节点 ID",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEditing) "保存修改" else "添加浏览器")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LockStatusCard(lockEndTime: Long) {
    var remainingText by remember { mutableStateOf("") }

    LaunchedEffect(lockEndTime) {
        while (true) {
            val remaining = lockEndTime - System.currentTimeMillis()
            remainingText = if (remaining > 0) {
                formatRemainingTime(remaining)
            } else {
                "已结束"
            }
            delay(1000)
        }
    }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Lock,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "已锁定",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "剩余时间: $remainingText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun UrlBlockLockSheet(
    vm: UrlBlockVm,
    isLocked: Boolean,
    lockEndTime: Long,
    onConfirm: () -> Unit
) {
    val durationOptions = listOf(
        480 to "8小时",
        1440 to "1天",
        4320 to "3天"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = if (isLocked) "延长锁定" else "锁定网址拦截",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isLocked) {
            val remaining = lockEndTime - System.currentTimeMillis()
            Text(
                text = "当前剩余: ${formatRemainingTime(remaining)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            Text(
                text = "锁定后将无法关闭网址拦截功能",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Text(
            text = "选择时长",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            durationOptions.forEach { (minutes, label) ->
                val isSelected = !vm.isCustomDuration && vm.selectedDuration == minutes
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable {
                            vm.isCustomDuration = false
                            vm.selectedDuration = minutes
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { vm.isCustomDuration = !vm.isCustomDuration }
        ) {
            Switch(
                checked = vm.isCustomDuration,
                onCheckedChange = { vm.isCustomDuration = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("自定义时长", style = MaterialTheme.typography.bodyMedium)
        }

        if (vm.isCustomDuration) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = vm.customDaysText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            vm.customDaysText = newValue
                        }
                    },
                    label = { Text("天") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = vm.customHoursText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            vm.customHoursText = newValue
                        }
                    },
                    label = { Text("小时") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLocked) "确定延长" else "确定锁定")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatRemainingTime(millis: Long): String {
    if (millis <= 0) return "已结束"
    val minutes = millis / 60000
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    val days = hours / 24
    val remainingHours = hours % 24

    return if (days > 0) {
        "${days}天${remainingHours}小时"
    } else if (hours > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${minutes}分钟"
    }
}
