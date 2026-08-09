@file:JvmName("UrlBlockerEditor0")

package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlGroupEditorSheet(
    vm: UrlBlockVm,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = if (vm.editingGroup != null) {
                    if (isLocked) "查看规则组 (已锁定)" else "编辑规则组"
                } else "添加规则组",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = vm.groupName,
                onValueChange = { vm.groupName = it },
                label = { Text("规则组名称") },
                placeholder = { Text("如：视频网站") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked,
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
                enabled = !isLocked,
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (!isLocked) {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存") }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) { Text("确定") }
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
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            item {
                UrlRuleIdentityFields(vm = vm, isLocked = isLocked)
                UrlRuleGroupFields(vm = vm, allGroups = allGroups, isLocked = isLocked)
                UrlRuleTimeFields(vm = vm, isLocked = isLocked)
                UrlRuleSaveButton(isLocked = isLocked, onDismiss = onDismiss, onSave = onSave)
            }
        }
    }
}

@Composable
private fun UrlRuleIdentityFields(vm: UrlBlockVm, isLocked: Boolean) {
    Text(
        text = if (vm.editingUrlRule != null) {
            if (isLocked) "查看规则 (已锁定)" else "编辑规则"
        } else "添加规则",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(
        value = vm.urlPattern,
        onValueChange = { vm.urlPattern = it },
        label = { Text("网址匹配模式") },
        placeholder = { Text("如：bilibili.com") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isLocked,
    )
    Text(
        text = "不带 http://，支持域名或前缀",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = vm.urlName,
        onValueChange = { vm.urlName = it },
        label = { Text("规则名称 (可选)") },
        placeholder = { Text("如：B站") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isLocked,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text("匹配方式", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = vm.urlMatchType == UrlBlockRule.MATCH_TYPE_DOMAIN,
            onClick = { vm.urlMatchType = UrlBlockRule.MATCH_TYPE_DOMAIN },
            label = { Text("域名匹配") },
            enabled = !isLocked,
        )
        FilterChip(
            selected = vm.urlMatchType == UrlBlockRule.MATCH_TYPE_PREFIX,
            onClick = { vm.urlMatchType = UrlBlockRule.MATCH_TYPE_PREFIX },
            label = { Text("前缀匹配") },
            enabled = !isLocked,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UrlRuleGroupFields(vm: UrlBlockVm, allGroups: List<UrlRuleGroup>, isLocked: Boolean) {
    Spacer(modifier = Modifier.height(16.dp))
    Text("所属规则组", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = vm.urlGroupId == 0L,
            onClick = { vm.urlGroupId = 0L },
            label = { Text("未分组") },
            enabled = !isLocked,
        )
        allGroups.forEach { group ->
            FilterChip(
                selected = vm.urlGroupId == group.id,
                onClick = { vm.urlGroupId = group.id },
                label = { Text(group.name) },
                enabled = !isLocked,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UrlRuleTimeFields(vm: UrlBlockVm, isLocked: Boolean) {
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
    Text("时间设置 (拦截生效时间)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    Text("规则模式", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !vm.timeRuleIsAllowMode,
            onClick = { vm.timeRuleIsAllowMode = false },
            label = { Text("🚫 禁止时间段") },
            enabled = !isLocked,
        )
        FilterChip(
            selected = vm.timeRuleIsAllowMode,
            onClick = { vm.timeRuleIsAllowMode = true },
            label = { Text("✓ 允许时间段") },
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = vm.timeRuleStartTime,
            onValueChange = { vm.timeRuleStartTime = it },
            label = { Text("开始时间") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
        OutlinedTextField(
            value = vm.timeRuleEndTime,
            onValueChange = { vm.timeRuleEndTime = it },
            label = { Text("结束时间") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text("生效日期", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val currentDays = vm.timeRuleDaysOfWeek
        (1..7).forEach { day ->
            FilterChip(
                selected = currentDays.contains(day),
                onClick = {
                    vm.timeRuleDaysOfWeek = if (currentDays.contains(day)) currentDays - day else (currentDays + day).sorted()
                },
                label = { Text("周${dayNames[day - 1]}") },
                enabled = !isLocked,
            )
        }
    }
}

@Composable
private fun UrlRuleSaveButton(isLocked: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    Spacer(modifier = Modifier.height(24.dp))
    if (!isLocked) {
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    } else {
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) { Text("确定") }
    }
    Spacer(modifier = Modifier.height(16.dp))
}
