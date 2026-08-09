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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlBlockerLock
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.ui.component.AppPickerDialog
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.appInfoMapFlow

@Composable
@android.annotation.SuppressLint("NonObservableLocale")
fun UrlGroupCard(
    group: UrlRuleGroup,
    rules: List<UrlTimeRule>,
    urlRules: List<UrlBlockRule>,
    globalLock: UrlBlockerLock?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onAddTimeRule: () -> Unit,
    onTimeRuleEdit: (UrlTimeRule) -> Unit,
    onTimeRuleDelete: (UrlTimeRule) -> Unit,
    onTimeRuleLock: (UrlTimeRule) -> Unit,
    onAddUrlRule: () -> Unit,
    onEditUrlRule: (UrlBlockRule) -> Unit,
    onDeleteUrlRule: (UrlBlockRule) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 组头
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
                    if (group.isCurrentlyLocked) {
                        val lockEndTime = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(group.lockEndTime))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🔒 锁定至 $lockEndTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Switch(
                    checked = group.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    enabled = !group.isCurrentlyLocked && globalLock?.isCurrentlyLocked != true
                )
            }

            // 网址规则列表
            if (urlRules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "包含的网址 (${urlRules.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                urlRules.forEach { urlRule ->
                    UrlInGroupRow(
                        rule = urlRule,
                        onEdit = { onEditUrlRule(urlRule) },
                        onDelete = { onDeleteUrlRule(urlRule) }
                    )
                }
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

                rules.forEach { rule ->
                    TimeRuleRow(
                        rule = rule,
                        onEdit = { onTimeRuleEdit(rule) },
                        onDelete = { onTimeRuleDelete(rule) },
                        onLock = { onTimeRuleLock(rule) }
                    )
                }
            }

            // 操作按钮
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!group.isCurrentlyLocked) {
                    TextButton(onClick = onAddUrlRule) {
                        Icon(PerfIcon.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("网址")
                    }
                    
                    TextButton(onClick = onAddTimeRule) {
                        Icon(PerfIcon.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("时间")
                    }
                }
                
                TextButton(onClick = onLock) {
                    Icon(
                        PerfIcon.Lock, 
                        contentDescription = null,
                        tint = if (group.isCurrentlyLocked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (group.isCurrentlyLocked) "延长锁定" else "锁定")
                }
                
                if (!group.isCurrentlyLocked) {
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
            title = { Text("删除规则组") },
            text = { Text("确定要删除规则组「${group.name}」吗？组内的所有规则也会被删除。") },
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
@android.annotation.SuppressLint("NonObservableLocale")
fun UrlItemCard(
    rule: UrlBlockRule,
    timeRules: List<UrlTimeRule>,
    globalLock: UrlBlockerLock?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onAddTimeRule: () -> Unit,
    onTimeRuleEdit: (UrlTimeRule) -> Unit,
    onTimeRuleDelete: (UrlTimeRule) -> Unit,
    onTimeRuleLock: (UrlTimeRule) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 规则头
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.name.ifBlank { rule.pattern },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (rule.isCurrentlyLocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                PerfIcon.Lock,
                                contentDescription = "已锁定",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        text = rule.pattern,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.isCurrentlyLocked) {
                        val lockEndTime = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(rule.lockEndTime))
                        Text(
                            text = "🔒 锁定至 $lockEndTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    enabled = !rule.isCurrentlyLocked && globalLock?.isCurrentlyLocked != true
                )
            }

            // 时间规则列表
            if (timeRules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "时间规则 (${timeRules.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                timeRules.forEach { tr ->
                    TimeRuleRow(
                        rule = tr,
                        onEdit = { onTimeRuleEdit(tr) },
                        onDelete = { onTimeRuleDelete(tr) },
                        onLock = { onTimeRuleLock(tr) }
                    )
                }
            }

            // 操作按钮
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!rule.isCurrentlyLocked) {
                    TextButton(onClick = onAddTimeRule) {
                        Icon(PerfIcon.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("时间规则")
                    }
                }
                
                TextButton(onClick = onLock) {
                    Icon(
                        PerfIcon.Lock,
                        contentDescription = null,
                        tint = if (rule.isCurrentlyLocked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (rule.isCurrentlyLocked) "延长锁定" else "锁定")
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
            text = { Text("确定要删除这条规则吗？") },
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
fun UrlInGroupRow(
    rule: UrlBlockRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name.ifBlank { rule.pattern },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = rule.pattern,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!rule.isCurrentlyLocked) {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    PerfIcon.Delete,
                    contentDescription = "从组中删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }

        Switch(
            checked = rule.enabled,
            onCheckedChange = { /* 内部逻辑处理 */ },
            enabled = false // 只读展示，通过 Edit 修改
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("移除规则") },
            text = { Text("确定要将此规则「${rule.pattern}」删除吗？") },
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
@android.annotation.SuppressLint("NonObservableLocale")
fun TimeRuleRow(
    rule: UrlTimeRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (rule.isAllowMode) "✓" else "🚫",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${rule.formatTimeRange()} ${rule.formatDaysOfWeek()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (rule.isAllowMode) {
                Text(
                    text = "允许时间段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (rule.isCurrentlyLocked) {
                val lockEndTime = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(rule.lockEndTime))
                Text(
                    text = "🔒 锁定至 $lockEndTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        IconButton(onClick = onLock) {
            Icon(
                PerfIcon.Lock,
                contentDescription = if (rule.isCurrentlyLocked) "延长锁定" else "锁定",
                tint = if (rule.isCurrentlyLocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
        }

        if (!rule.isCurrentlyLocked) {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    PerfIcon.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        
        Switch(
            checked = rule.enabled,
            onCheckedChange = { /* 内部逻辑处理 */ },
            enabled = false // 只读展示，通过 Edit 修改
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除时间规则") },
            text = { Text("确定要删除这条时间规则吗？") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlGroupEditorSheet(
    vm: UrlBlockVm,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
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
                text = if (vm.editingGroup != null) {
                    if (isLocked) "查看规则组 (已锁定)" else "编辑规则组"
                } else "添加规则组",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = vm.groupName,
                onValueChange = { vm.groupName = it },
                label = { Text("规则组名称") },
                placeholder = { Text("如：视频网站") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = vm.groupQuickUrls,
                onValueChange = { vm.groupQuickUrls = it },
                label = { Text("批量添加网址 (可选)") },
                placeholder = { Text("每行一个，如：\nbilibili.com\nyoutube.com") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                enabled = !isLocked
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isLocked) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("确定")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UrlRuleEditorSheet(
    vm: UrlBlockVm,
    allGroups: List<UrlRuleGroup>,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
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
                    text = if (vm.editingUrlRule != null) {
                        if (isLocked) "查看规则 (已锁定)" else "编辑规则"
                    } else "添加规则",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = vm.urlPattern,
                    onValueChange = { vm.urlPattern = it },
                    label = { Text("网址匹配模式") },
                    placeholder = { Text("如：bilibili.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLocked
                )
                Text(
                    text = "不带 http://，支持域名或前缀",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = vm.urlName,
                    onValueChange = { vm.urlName = it },
                    label = { Text("规则名称 (可选)") },
                    placeholder = { Text("如：B站") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLocked
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 匹配模式
                Text("匹配方式", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = vm.urlMatchType == UrlBlockRule.MATCH_TYPE_DOMAIN,
                        onClick = { vm.urlMatchType = UrlBlockRule.MATCH_TYPE_DOMAIN },
                        label = { Text("域名匹配") },
                        enabled = !isLocked
                    )
                    FilterChip(
                        selected = vm.urlMatchType == UrlBlockRule.MATCH_TYPE_PREFIX,
                        onClick = { vm.urlMatchType = UrlBlockRule.MATCH_TYPE_PREFIX },
                        label = { Text("前缀匹配") },
                        enabled = !isLocked
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 所属组
                Text("所属规则组", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = vm.urlGroupId == 0L,
                        onClick = { vm.urlGroupId = 0L },
                        label = { Text("未分组") },
                        enabled = !isLocked
                    )
                    allGroups.forEach { group ->
                        FilterChip(
                            selected = vm.urlGroupId == group.id,
                            onClick = { vm.urlGroupId = group.id },
                            label = { Text(group.name) },
                            enabled = !isLocked
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // ================== 时间规则设置 ==================
                Text("时间设置 (拦截生效时间)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(16.dp))

                // 模式选择
                Text("规则模式", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !vm.timeRuleIsAllowMode,
                        onClick = { vm.timeRuleIsAllowMode = false },
                        label = { Text("🚫 禁止时间段") },
                        enabled = !isLocked
                    )
                    FilterChip(
                        selected = vm.timeRuleIsAllowMode,
                        onClick = { vm.timeRuleIsAllowMode = true },
                        label = { Text("✓ 允许时间段") },
                        enabled = !isLocked
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 时间段
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = vm.timeRuleStartTime,
                        onValueChange = { vm.timeRuleStartTime = it },
                        label = { Text("开始时间") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isLocked
                    )
                    OutlinedTextField(
                        value = vm.timeRuleEndTime,
                        onValueChange = { vm.timeRuleEndTime = it },
                        label = { Text("结束时间") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isLocked
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 星期选择
                Text("生效日期", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
                    val currentDays = vm.timeRuleDaysOfWeek
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = currentDays.contains(day),
                            onClick = {
                                val newDays = if (currentDays.contains(day)) {
                                    currentDays - day
                                } else {
                                    (currentDays + day).sorted()
                                }
                                vm.timeRuleDaysOfWeek = newDays
                            },
                            label = { Text("周${dayNames[day - 1]}") },
                            enabled = !isLocked
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!isLocked) {
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                        Text("保存")
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("确定")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimeRuleEditorSheet(
    vm: UrlBlockVm,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
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
                    text = if (vm.editingTimeRule != null) {
                        if (isLocked) "查看时间规则 (已锁定)" else "编辑时间规则"
                    } else "添加时间规则",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                    TextButton(
                        onClick = { showTemplateDialog = true },
                        enabled = !isLocked
                    ) {
                        Text("选择模板")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 模式选择
                Text("规则模式", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !vm.timeRuleIsAllowMode,
                        onClick = { vm.timeRuleIsAllowMode = false },
                        label = { Text("🚫 禁止时间段") },
                        enabled = !isLocked
                    )
                    FilterChip(
                        selected = vm.timeRuleIsAllowMode,
                        onClick = { vm.timeRuleIsAllowMode = true },
                        label = { Text("✓ 允许时间段") },
                        enabled = !isLocked
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 时间段
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = vm.timeRuleStartTime,
                        onValueChange = { vm.timeRuleStartTime = it },
                        label = { Text("开始时间") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isLocked
                    )
                    OutlinedTextField(
                        value = vm.timeRuleEndTime,
                        onValueChange = { vm.timeRuleEndTime = it },
                        label = { Text("结束时间") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isLocked
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 星期选择
                Text("生效日期", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
                    val currentDays = vm.timeRuleDaysOfWeek
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = currentDays.contains(day),
                            onClick = {
                                val newDays = if (currentDays.contains(day)) {
                                    currentDays - day
                                } else {
                                    (currentDays + day).sorted()
                                }
                                vm.timeRuleDaysOfWeek = newDays
                            },
                            label = { Text("周${dayNames[day - 1]}") },
                            enabled = !isLocked
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!isLocked) {
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                        Text("保存")
                    }
                } else {
                    Button(
                        onClick = onDismiss, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("确定")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showTemplateDialog) {
        TemplatePickerDialog(
            onDismiss = { showTemplateDialog = false },
            onSelect = { template ->
                vm.applyTimeTemplate(template)
                showTemplateDialog = false
            }
        )
    }
}

@Composable
fun UrlLockSheet(
    title: String,
    description: String,
    currentLockEndTime: Long?,
    vm: UrlBlockVm,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserListSheet(
    browsers: List<BrowserConfig>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (BrowserConfig) -> Unit,
    onDelete: (BrowserConfig) -> Unit,
    onToggle: (BrowserConfig) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp) // Fixed height or use logic to fill appropriately
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "浏览器适配",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAdd) {
                    Icon(PerfIcon.Add, contentDescription = "添加浏览器")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!browser.isBuiltin) {
                            IconButton(onClick = { onDelete(browser) }) {
                                Icon(
                                    PerfIcon.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Switch(
                            checked = browser.enabled,
                            onCheckedChange = { onToggle(browser) }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserEditSheet(
    vm: UrlBlockVm,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val isEditing = vm.editingBrowser != null
    val isBuiltin = vm.editingBrowser?.isBuiltin == true
    var showAppPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.browserPackageName,
                    onValueChange = { if (!isBuiltin) vm.browserPackageName = it },
                    label = { Text("包名 *") },
                    placeholder = { Text("如: com.android.chrome") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isBuiltin
                )
                if (!isBuiltin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showAppPicker = true }) {
                        Text("选择应用")
                    }
                }
            }

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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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

    if (showAppPicker) {
        val appInfoMap by appInfoMapFlow.collectAsStateWithLifecycle()
        AppPickerDialog(
            currentApps = emptyList(),
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                val pkg = selected.firstOrNull()
                if (pkg != null) {
                    vm.browserPackageName = pkg
                    if (vm.browserName.isBlank()) {
                        vm.browserName = appInfoMap[pkg]?.name ?: ""
                    }
                }
                showAppPicker = false
            },
            singleSelect = true
        )
    }
}

@Composable
private fun TemplatePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (UrlTimeRule.Companion.TimeTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间模板") },
        text = {
            LazyColumn {
                items(UrlTimeRule.Companion.TEMPLATES) { template ->
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
