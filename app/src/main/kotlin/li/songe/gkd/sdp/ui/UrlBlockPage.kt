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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
    val rules by vm.rulesFlow.collectAsState()
    val browsers by vm.browsersFlow.collectAsState()
    val groups by vm.groupsFlow.collectAsState()
    val timeRules by vm.timeRulesFlow.collectAsState()
    val enabledRuleCount by vm.enabledRuleCountFlow.collectAsState()
    val enabledBrowserCount by vm.enabledBrowserCountFlow.collectAsState()
    val urlBlockerEnabled by UrlBlockerEngine.enabledFlow.collectAsState()
    val isLocked by vm.isLockedFlow.collectAsState()
    val lockEndTime by vm.lockEndTimeFlow.collectAsState()

    val scope = rememberCoroutineScope()
    val ruleSheetState = rememberModalBottomSheetState()
    val browserSheetState = rememberModalBottomSheetState()
    val lockSheetState = rememberModalBottomSheetState()
    val groupSheetState = rememberModalBottomSheetState()
    val timeRuleSheetState = rememberModalBottomSheetState()

    var showRuleSheet by remember { mutableStateOf(false) }
    var showBrowserSheet by remember { mutableStateOf(false) }
    var showBrowserListDialog by remember { mutableStateOf(false) }
    var showLockSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showTimeRuleSheet by remember { mutableStateOf(false) }
    var showTimeTemplateDialog by remember { mutableStateOf(false) }
    var deleteConfirmRule by remember { mutableStateOf<UrlBlockRule?>(null) }
    var deleteConfirmGroup by remember { mutableStateOf<UrlRuleGroup?>(null) }
    var deleteConfirmTimeRule by remember { mutableStateOf<UrlTimeRule?>(null) }

    // 当前 Tab: 0=规则, 1=规则组, 2=时间规则
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
                    when (currentTab) {
                        0 -> {
                            vm.resetRuleForm()
                            showRuleSheet = true
                        }
                        1 -> {
                            vm.resetGroupForm()
                            showGroupSheet = true
                        }
                        2 -> {
                            vm.resetTimeRuleForm()
                            showTimeRuleSheet = true
                        }
                    }
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

            // Tab 切换
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SegmentedButton(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text("规则")
                    }
                    SegmentedButton(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text("规则组")
                    }
                    SegmentedButton(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text("时间规则")
                    }
                }
            }

            when (currentTab) {
                0 -> {
                    // 规则列表
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
                            groups = groups,
                            onToggleEnabled = { vm.toggleRuleEnabled(rule) },
                            onEdit = {
                                vm.loadRuleForEdit(rule)
                                showRuleSheet = true
                            },
                            onDelete = { deleteConfirmRule = rule }
                        )
                    }
                }
                1 -> {
                    // 规则组列表
                    if (groups.isEmpty()) {
                        item {
                            Text(
                                text = "暂无规则组，点击右下角添加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.itemPadding()
                            )
                        }
                    }

                    items(groups, key = { it.id }) { group ->
                        val groupRules = rules.filter { it.groupId == group.id }
                        val groupTimeRules = timeRules.filter { 
                            it.targetType == UrlTimeRule.TARGET_TYPE_GROUP && 
                            it.targetId == group.id.toString() 
                        }
                        UrlRuleGroupCard(
                            group = group,
                            ruleCount = groupRules.size,
                            timeRuleCount = groupTimeRules.size,
                            onToggleEnabled = { vm.toggleGroupEnabled(group) },
                            onEdit = {
                                vm.loadGroupForEdit(group)
                                showGroupSheet = true
                            },
                            onDelete = { deleteConfirmGroup = group },
                            onAddTimeRule = {
                                vm.resetTimeRuleForm()
                                vm.timeRuleTargetType = UrlTimeRule.TARGET_TYPE_GROUP
                                vm.timeRuleTargetId = group.id.toString()
                                showTimeRuleSheet = true
                            }
                        )
                    }
                }
                2 -> {
                    // 时间规则列表
                    if (timeRules.isEmpty()) {
                        item {
                            Text(
                                text = "暂无时间规则，点击右下角添加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.itemPadding()
                            )
                        }
                    }

                    items(timeRules, key = { it.id }) { timeRule ->
                        UrlTimeRuleCard(
                            timeRule = timeRule,
                            rules = rules,
                            groups = groups,
                            onToggleEnabled = { vm.toggleTimeRuleEnabled(timeRule) },
                            onEdit = {
                                vm.loadTimeRuleForEdit(timeRule)
                                showTimeRuleSheet = true
                            },
                            onDelete = { deleteConfirmTimeRule = timeRule }
                        )
                    }
                }
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
                    groups = groups,
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

        // 规则组编辑 Sheet
        if (showGroupSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGroupSheet = false },
                sheetState = groupSheetState
            ) {
                GroupEditSheet(
                    vm = vm,
                    onSave = {
                        vm.saveGroup()
                        scope.launch { groupSheetState.hide() }.invokeOnCompletion {
                            if (!groupSheetState.isVisible) showGroupSheet = false
                        }
                    }
                )
            }
        }

        // 时间规则编辑 Sheet
        if (showTimeRuleSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTimeRuleSheet = false },
                sheetState = timeRuleSheetState
            ) {
                TimeRuleEditSheet(
                    vm = vm,
                    rules = rules,
                    groups = groups,
                    onSave = {
                        vm.saveTimeRule()
                        scope.launch { timeRuleSheetState.hide() }.invokeOnCompletion {
                            if (!timeRuleSheetState.isVisible) showTimeRuleSheet = false
                        }
                    },
                    onShowTemplates = { showTimeTemplateDialog = true }
                )
            }
        }

        // 时间模板选择 Dialog
        if (showTimeTemplateDialog) {
            TimeTemplateDialog(
                onDismiss = { showTimeTemplateDialog = false },
                onSelect = { template ->
                    vm.applyTimeTemplate(template)
                    showTimeTemplateDialog = false
                }
            )
        }

        // 删除规则组确认 Dialog
        deleteConfirmGroup?.let { group ->
            AlertDialog(
                onDismissRequest = { deleteConfirmGroup = null },
                title = { Text("删除规则组") },
                text = { Text("确定要删除规则组 \"${group.name}\" 吗？相关的时间规则也会被删除。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.deleteGroup(group)
                            deleteConfirmGroup = null
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmGroup = null }) {
                        Text("取消")
                    }
                }
            )
        }

        // 删除时间规则确认 Dialog
        deleteConfirmTimeRule?.let { timeRule ->
            AlertDialog(
                onDismissRequest = { deleteConfirmTimeRule = null },
                title = { Text("删除时间规则") },
                text = { Text("确定要删除此时间规则吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.deleteTimeRule(timeRule)
                            deleteConfirmTimeRule = null
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmTimeRule = null }) {
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
    groups: List<UrlRuleGroup> = emptyList(),
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val groupName = groups.find { it.id == rule.groupId }?.name
    
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
                    if (groupName != null) {
                        Text(
                            text = " | 组: $groupName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
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
    groups: List<UrlRuleGroup> = emptyList(),
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

        // 规则组选择
        if (groups.isNotEmpty()) {
            Text(
                text = "所属规则组 (可选)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            val selectedGroupName = groups.find { it.id == vm.ruleGroupId }?.name ?: "无"
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "无",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (vm.ruleGroupId == 0L) FontWeight.Bold else FontWeight.Normal,
                    color = if (vm.ruleGroupId == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .border(
                            width = if (vm.ruleGroupId == 0L) 2.dp else 1.dp,
                            color = if (vm.ruleGroupId == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { vm.ruleGroupId = 0L }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                groups.forEach { group ->
                    val isSelected = vm.ruleGroupId == group.id
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { vm.ruleGroupId = group.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }

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

// ======================== 新增组件 ========================

@Composable
fun UrlRuleGroupCard(
    group: UrlRuleGroup,
    ruleCount: Int,
    timeRuleCount: Int,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddTimeRule: () -> Unit
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
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "包含 $ruleCount 条规则, $timeRuleCount 条时间规则",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onAddTimeRule, modifier = Modifier.size(36.dp)) {
                PerfIcon(
                    imageVector = PerfIcon.Schedule,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                PerfIcon(
                    imageVector = PerfIcon.Delete,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Switch(
                checked = group.enabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }
    }
}

@Composable
fun UrlTimeRuleCard(
    timeRule: UrlTimeRule,
    rules: List<UrlBlockRule>,
    groups: List<UrlRuleGroup>,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val targetName = when (timeRule.targetType) {
        UrlTimeRule.TARGET_TYPE_RULE -> {
            val rule = rules.find { it.id.toString() == timeRule.targetId }
            "规则: ${rule?.name ?: timeRule.targetId}"
        }
        UrlTimeRule.TARGET_TYPE_GROUP -> {
            val group = groups.find { it.id.toString() == timeRule.targetId }
            "规则组: ${group?.name ?: timeRule.targetId}"
        }
        else -> "未知对象"
    }

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
                    text = targetName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${timeRule.formatTimeRange()} | ${timeRule.formatDaysOfWeek()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    Text(
                        text = timeRule.formatModeDescription(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (timeRule.isAllowMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                    if (timeRule.isCurrentlyLocked) {
                        Text(
                            text = " | 已锁定",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
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
                checked = timeRule.enabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }
    }
}

@Composable
fun GroupEditSheet(
    vm: UrlBlockVm,
    onSave: () -> Unit
) {
    val isEditing = vm.editingGroup != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = if (isEditing) "编辑规则组" else "添加规则组",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = vm.groupName,
            onValueChange = { vm.groupName = it },
            label = { Text("规则组名称 *") },
            placeholder = { Text("如: 社交媒体") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEditing) "保存修改" else "添加规则组")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRuleEditSheet(
    vm: UrlBlockVm,
    rules: List<UrlBlockRule>,
    groups: List<UrlRuleGroup>,
    onSave: () -> Unit,
    onShowTemplates: () -> Unit
) {
    val isEditing = vm.editingTimeRule != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = if (isEditing) "编辑时间规则" else "添加时间规则",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 目标类型选择
        Text(
            text = "拦截对象类型",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = vm.timeRuleTargetType == UrlTimeRule.TARGET_TYPE_RULE,
                onClick = { 
                    vm.timeRuleTargetType = UrlTimeRule.TARGET_TYPE_RULE
                    vm.timeRuleTargetId = ""
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("单条规则")
            }
            SegmentedButton(
                selected = vm.timeRuleTargetType == UrlTimeRule.TARGET_TYPE_GROUP,
                onClick = { 
                    vm.timeRuleTargetType = UrlTimeRule.TARGET_TYPE_GROUP
                    vm.timeRuleTargetId = ""
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("规则组")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 目标选择
        Text(
            text = "选择${if (vm.timeRuleTargetType == UrlTimeRule.TARGET_TYPE_RULE) "规则" else "规则组"}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        val options = if (vm.timeRuleTargetType == UrlTimeRule.TARGET_TYPE_RULE) {
            rules.map { it.id.toString() to (it.name.ifBlank { it.pattern }) }
        } else {
            groups.map { it.id.toString() to it.name }
        }
        
        LazyColumn(modifier = Modifier.height(120.dp)) {
            items(options) { (id, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.timeRuleTargetId = id }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PerfIcon(
                        imageVector = if (vm.timeRuleTargetId == id) PerfIcon.CheckBox else PerfIcon.CheckBoxOutlineBlank,
                        tint = if (vm.timeRuleTargetId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 模式选择
        Text(
            text = "时间模式",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !vm.timeRuleIsAllowMode,
                onClick = { vm.timeRuleIsAllowMode = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("禁止时间段")
            }
            SegmentedButton(
                selected = vm.timeRuleIsAllowMode,
                onClick = { vm.timeRuleIsAllowMode = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("允许时间段")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 时间设置
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = vm.timeRuleStartTime,
                onValueChange = { vm.timeRuleStartTime = it },
                label = { Text("开始时间") },
                placeholder = { Text("如: 22:00") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = vm.timeRuleEndTime,
                onValueChange = { vm.timeRuleEndTime = it },
                label = { Text("结束时间") },
                placeholder = { Text("如: 08:00") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onShowTemplates,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("使用预设模板")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 星期选择
        Text(
            text = "生效星期 (当前: ${vm.timeRuleDaysOfWeek})",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        val daysLabels = listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7)
        val selectedDays = vm.timeRuleDaysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            daysLabels.forEach { (label, day) ->
                val isSelected = day in selectedDays
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable {
                            val newDays = if (isSelected) {
                                selectedDays - day
                            } else {
                                selectedDays + day
                            }
                            vm.timeRuleDaysOfWeek = newDays.sorted().joinToString(",")
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.timeRuleInterceptMessage,
            onValueChange = { vm.timeRuleInterceptMessage = it },
            label = { Text("拦截提示语") },
            placeholder = { Text("这真的重要吗？") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEditing) "保存修改" else "添加时间规则")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TimeTemplateDialog(
    onDismiss: () -> Unit,
    onSelect: (UrlTimeRule.Companion.TimeTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间模板") },
        text = {
            LazyColumn {
                items(UrlTimeRule.TEMPLATES) { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(template) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
