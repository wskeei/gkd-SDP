package li.songe.gkd.sdp.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
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

    val scope = rememberCoroutineScope()
    val ruleSheetState = rememberModalBottomSheetState()
    val browserSheetState = rememberModalBottomSheetState()

    var showRuleSheet by remember { mutableStateOf(false) }
    var showBrowserSheet by remember { mutableStateOf(false) }
    var showBrowserListDialog by remember { mutableStateOf(false) }
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
                            onCheckedChange = { vm.toggleUrlBlockerEnabled(it) }
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
