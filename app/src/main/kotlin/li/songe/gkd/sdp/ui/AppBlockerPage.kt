package li.songe.gkd.sdp.ui

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
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
fun AppBlockerPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AppBlockerVm>()
    val allGroups by vm.allGroupsFlow.collectAsState()
    val allRules by vm.allRulesFlow.collectAsState()
    val globalLock by vm.globalLockFlow.collectAsState()

    var showGlobalLockSheet by remember { mutableStateOf(false) }
    var showGroupLockSheet by remember { mutableStateOf(false) }
    var showRuleLockSheet by remember { mutableStateOf(false) }
    var lockTargetGroup by remember { mutableStateOf<AppGroup?>(null) }
    var lockTargetRule by remember { mutableStateOf<BlockTimeRule?>(null) }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popBackStack() },
                    )
                },
                title = { Text(text = "应用拦截") },
                actions = {
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

            // 应用组列表
            item(key = "groups_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "应用组 (${allGroups.size})",
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
                        text = "暂无应用组",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                items(allGroups, key = { "group_${it.id}" }) { group ->
                    AppGroupCard(
                        group = group,
                        rules = allRules.filter {
                            it.targetType == BlockTimeRule.TARGET_TYPE_GROUP &&
                            it.targetId == group.id.toString()
                        },
                        onToggleEnabled = { vm.toggleGroupEnabled(group) },
                        onEdit = {
                            vm.loadGroupForEdit(group)
                        },
                        onDelete = { vm.deleteGroup(group) },
                        onLock = {
                            lockTargetGroup = group
                            showGroupLockSheet = true
                        },
                        onAddRule = {
                            vm.resetRuleForm()
                            vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_GROUP
                            vm.ruleTargetId = group.id.toString()
                            vm.showRuleEditor = true
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item(key = "spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 单独应用规则列表
            item(key = "app_rules_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "单独应用",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        vm.resetRuleForm()
                        vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_APP
                        vm.showRuleEditor = true
                    }) {
                        Icon(PerfIcon.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            }

            val appRules = allRules.filter { it.targetType == BlockTimeRule.TARGET_TYPE_APP }
            if (appRules.isEmpty()) {
                item(key = "no_app_rules") {
                    Text(
                        text = "暂无单独应用规则",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                // 按应用分组显示
                val rulesByApp = appRules.groupBy { it.targetId }
                rulesByApp.forEach { (packageName, rules) ->
                    item(key = "app_$packageName") {
                        AppRulesCard(
                            packageName = packageName,
                            rules = rules,
                            onToggleEnabled = { vm.toggleRuleEnabled(it) },
                            onEdit = { vm.loadRuleForEdit(it) },
                            onDelete = { vm.deleteRule(it) },
                            onLock = {
                                lockTargetRule = it
                                showRuleLockSheet = true
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // 应用组编辑器
    if (vm.showGroupEditor) {
        GroupEditorSheet(
            vm = vm,
            onDismiss = { vm.resetGroupForm() },
            onSave = { vm.saveGroup() }
        )
    }

    // 规则编辑器
    if (vm.showRuleEditor) {
        RuleEditorSheet(
            vm = vm,
            allGroups = allGroups,
            onDismiss = { vm.resetRuleForm() },
            onSave = { vm.saveRule() }
        )
    }

    // 全局锁定 Sheet
    if (showGlobalLockSheet) {
        LockSheet(
            title = if (globalLock?.isCurrentlyLocked == true) "延长全局锁定" else "全局锁定",
            description = "锁定后无法删除或修改任何应用/组/规则，但可以新增。",
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
            title = if (lockTargetGroup!!.isCurrentlyLocked) "延长锁定" else "锁定应用组",
            description = "锁定后无法关闭、删除或修改此应用组。",
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
            title = if (lockTargetRule!!.isCurrentlyLocked) "延长锁定" else "锁定规则",
            description = "锁定后无法关闭、删除或修改此规则。",
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
private fun AppGroupCard(
    group: AppGroup,
    rules: List<BlockTimeRule>,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onAddRule: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 应用组头部
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (group.isCurrentlyLocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                PerfIcon.Lock,
                                contentDescription = "已锁定",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${group.getAppList().size} 个应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Switch(
                    checked = group.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            // 时间规则列表
            if (rules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "时间规则 (${rules.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                rules.forEach { rule ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${rule.formatTimeRange()} ${rule.formatDaysOfWeek()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (rule.isCurrentlyLocked) {
                                Text(
                                    text = "已锁定",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { /* 规则的开关在 AppRulesCard 中处理 */ },
                            enabled = false
                        )
                    }
                }
            }

            // 操作按钮
            if (!group.isCurrentlyLocked) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onAddRule) {
                        Icon(PerfIcon.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加规则")
                    }
                    TextButton(onClick = onLock) {
                        Icon(PerfIcon.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("锁定")
                    }
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
            title = { Text("删除应用组") },
            text = { Text("确定要删除应用组「${group.name}」吗？相关的时间规则也会被删除。") },
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

@Composable
private fun AppRulesCard(
    packageName: String,
    rules: List<BlockTimeRule>,
    onToggleEnabled: (BlockTimeRule) -> Unit,
    onEdit: (BlockTimeRule) -> Unit,
    onDelete: (BlockTimeRule) -> Unit,
    onLock: (BlockTimeRule) -> Unit
) {
    val appName = remember(packageName) {
        try {
            val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
            app.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

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
                AppIcon(appId = packageName)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${rules.size} 条时间规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // 显示规则列表
            rules.forEach { rule ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${rule.formatTimeRange()} ${rule.formatDaysOfWeek()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (rule.isCurrentlyLocked) {
                            Text(
                                text = "已锁定",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggleEnabled(rule) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GroupEditorSheet(
    vm: AppBlockerVm,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }

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
                text = if (vm.editingGroup != null) "编辑应用组" else "添加应用组",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 应用组名称
            OutlinedTextField(
                value = vm.groupName,
                onValueChange = { vm.groupName = it },
                label = { Text("应用组名称") },
                placeholder = { Text("如：娱乐应用") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 应用列表
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "应用列表 (${vm.groupApps.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showAppPicker = true }) {
                    Text("选择")
                }
            }

            if (vm.groupApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.groupApps.forEach { packageName ->
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
                            onClick = { vm.removeAppFromGroup(packageName) },
                            label = { Text(appName) }
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

    if (showAppPicker) {
        AppPickerDialog(
            currentApps = vm.groupApps,
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                vm.groupApps = selected
                showAppPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorSheet(
    vm: AppBlockerVm,
    allGroups: List<AppGroup>,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = if (vm.editingRule != null) "编辑规则" else "添加规则",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 拦截对象类型选择
                Text(
                    text = "拦截对象",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_APP,
                        onClick = {
                            vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_APP
                            vm.ruleTargetId = ""
                        },
                        label = { Text("单独应用") }
                    )
                    FilterChip(
                        selected = vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_GROUP,
                        onClick = {
                            vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_GROUP
                            vm.ruleTargetId = ""
                        },
                        label = { Text("应用组") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 选择具体对象
                if (vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_APP) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (vm.ruleTargetId.isBlank()) "未选择应用" else {
                                try {
                                    val appInfo = app.packageManager.getApplicationInfo(vm.ruleTargetId, 0)
                                    app.packageManager.getApplicationLabel(appInfo).toString()
                                } catch (e: Exception) {
                                    vm.ruleTargetId
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showAppPicker = true }) {
                            Text("选择应用")
                        }
                    }
                } else {
                    // 应用组选择
                    if (allGroups.isEmpty()) {
                        Text(
                            text = "暂无应用组，请先创建应用组",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "选择应用组",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allGroups.forEach { group ->
                                FilterChip(
                                    selected = vm.ruleTargetId == group.id.toString(),
                                    onClick = { vm.ruleTargetId = group.id.toString() },
                                    label = { Text(group.name) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 时间模板
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "时间模板",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showTemplateDialog = true }) {
                        Text("选择模板")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 时间段
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
                        placeholder = { Text("08:00") },
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

                Spacer(modifier = Modifier.height(16.dp))

                // 拦截消息
                OutlinedTextField(
                    value = vm.ruleInterceptMessage,
                    onValueChange = { vm.ruleInterceptMessage = it },
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
                    Text("保存")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            currentApps = if (vm.ruleTargetId.isBlank()) emptyList() else listOf(vm.ruleTargetId),
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                vm.ruleTargetId = selected.firstOrNull() ?: ""
                showAppPicker = false
            },
            singleSelect = true
        )
    }

    if (showTemplateDialog) {
        TemplatePickerDialog(
            onDismiss = { showTemplateDialog = false },
            onSelect = { template ->
                vm.applyTemplate(template)
                showTemplateDialog = false
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    currentApps: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    singleSelect: Boolean = false
) {
    var selectedApps by remember { mutableStateOf(currentApps.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    val appInfoMap by appInfoMapFlow.collectAsState()

    // 过滤应用列表
    val filteredApps = remember(appInfoMap, searchQuery, showSystemApps) {
        appInfoMap.values
            .filterNot { it.hidden }
            .filter { appInfo ->
                // 系统应用过滤
                if (!showSystemApps && appInfo.isSystem) {
                    false
                } else {
                    // 搜索过滤
                    if (searchQuery.isBlank()) {
                        true
                    } else {
                        appInfo.name.contains(searchQuery, ignoreCase = true) ||
                        appInfo.id.contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            .sortedBy { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (singleSelect) "选择应用" else "选择应用列表") },
        text = {
            Column {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索应用") },
                    placeholder = { Text("输入应用名称或包名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(PerfIcon.Search, contentDescription = "搜索")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(PerfIcon.Close, contentDescription = "清除")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 系统应用开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSystemApps = !showSystemApps }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "显示系统应用",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 应用列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Text(
                                text = "未找到匹配的应用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(filteredApps) { appInfo ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedApps = if (singleSelect) {
                                            setOf(appInfo.id)
                                        } else {
                                            if (selectedApps.contains(appInfo.id)) {
                                                selectedApps - appInfo.id
                                            } else {
                                                selectedApps + appInfo.id
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = selectedApps.contains(appInfo.id),
                                    onCheckedChange = {
                                        selectedApps = if (singleSelect) {
                                            setOf(appInfo.id)
                                        } else {
                                            if (it) {
                                                selectedApps + appInfo.id
                                            } else {
                                                selectedApps - appInfo.id
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AppIcon(appId = appInfo.id)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appInfo.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (appInfo.isSystem) {
                                        Text(
                                            text = "系统应用",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
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

@Composable
private fun TemplatePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (BlockTimeRule.Companion.TimeTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间模板") },
        text = {
            LazyColumn {
                items(BlockTimeRule.Companion.TEMPLATES) { template ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(template) }
                            .padding(vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockSheet(
    title: String,
    description: String,
    currentLockEndTime: Long?,
    vm: AppBlockerVm,
    onDismiss: () -> Unit,
    onLock: () -> Unit
) {
    val durationOptions = listOf(
        480 to "8小时",
        1440 to "1天",
        4320 to "3天"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (currentLockEndTime != null && currentLockEndTime > System.currentTimeMillis()) {
                val remaining = currentLockEndTime - System.currentTimeMillis()
                val remainingMinutes = (remaining / 60000).coerceAtLeast(0)
                Text(
                    text = "当前剩余: ${if (remainingMinutes >= 60) "${remainingMinutes / 60}小时${remainingMinutes % 60}分钟" else "${remainingMinutes}分钟"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Text(
                    text = description,
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
                    val isSelected = !vm.isCustomLockDuration && vm.selectedLockDuration == minutes
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
                                vm.isCustomLockDuration = false
                                vm.selectedLockDuration = minutes
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { vm.isCustomLockDuration = !vm.isCustomLockDuration }
            ) {
                Switch(
                    checked = vm.isCustomLockDuration,
                    onCheckedChange = { vm.isCustomLockDuration = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("自定义时长", style = MaterialTheme.typography.bodyMedium)
            }

            if (vm.isCustomLockDuration) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                Text("确认锁定")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
