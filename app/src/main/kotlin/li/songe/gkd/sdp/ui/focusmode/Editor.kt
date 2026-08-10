@file:JvmName("FocusModeEditor0")

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.FocusRule

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RuleEditorSheet(
    vm: FocusModeVm,
    onDismiss: () -> Unit,
    onShowWhitelistPicker: () -> Unit,
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
            RuleEditorHeader(vm)
            RuleEditorTypeFields(vm)
            RuleEditorMessageAndWhitelist(vm, onShowWhitelistPicker)
            RuleEditorSaveButton(onSave)
        }
    }
}

@Composable
private fun RuleEditorHeader(vm: FocusModeVm) {
    Text(
        text = if (vm.editingRule != null) "编辑规则" else "添加规则",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(
        value = vm.ruleName,
        onValueChange = { vm.ruleName = it },
        label = { Text("规则名称") },
        placeholder = { Text("如：晚间复盘") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text("规则类型", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = vm.ruleType == FocusRule.RULE_TYPE_QUICK_START,
            onClick = { vm.ruleType = FocusRule.RULE_TYPE_QUICK_START },
            label = { Text("快速启动") },
        )
        FilterChip(
            selected = vm.ruleType == FocusRule.RULE_TYPE_SCHEDULED,
            onClick = { vm.ruleType = FocusRule.RULE_TYPE_SCHEDULED },
            label = { Text("定时规则") },
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun RuleEditorTypeFields(vm: FocusModeVm) {
    if (vm.ruleType == FocusRule.RULE_TYPE_QUICK_START) {
        Text("专注时长", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = vm.ruleDurationHours.toString(),
                onValueChange = { vm.ruleDurationHours = it.toIntOrNull()?.coerceIn(0, 48) ?: 0 },
                label = { Text("小时") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = vm.ruleDurationMinutes.toString(),
                onValueChange = { vm.ruleDurationMinutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                label = { Text("分钟") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        if (vm.ruleTotalDurationMinutes < 5) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("最短时长为 5 分钟", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = vm.ruleIsLocked, onCheckedChange = { vm.ruleIsLocked = it })
            Spacer(modifier = Modifier.width(8.dp))
            Text("锁定（无法提前结束）")
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = vm.ruleStartTime,
                onValueChange = { vm.ruleStartTime = it },
                label = { Text("开始时间") },
                placeholder = { Text("22:00") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = vm.ruleEndTime,
                onValueChange = { vm.ruleEndTime = it },
                label = { Text("结束时间") },
                placeholder = { Text("23:00") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("生效日期", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    label = { Text("周${dayNames[day - 1]}") },
                )
            }
        }
    }
}

@Composable
private fun RuleEditorMessageAndWhitelist(vm: FocusModeVm, onShowWhitelistPicker: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = vm.ruleInterceptMessage,
        onValueChange = { vm.ruleInterceptMessage = it },
        label = { Text("拦截提示语") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("白名单应用 (${vm.ruleWhitelistApps.size})", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onShowWhitelistPicker) { Text("选择") }
    }
    if (vm.ruleWhitelistApps.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.ruleWhitelistApps.forEach { packageName ->
                val appName = remember(packageName) {
                    try {
                        val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
                        app.packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        packageName.split(".").lastOrNull() ?: packageName
                    }
                }
                FilterChip(
                    selected = true,
                    onClick = { vm.removeFromRuleWhitelist(packageName) },
                    label = { Text(appName) },
                )
            }
        }
    }
}

@Composable
private fun RuleEditorSaveButton(onSave: () -> Unit) {
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    Spacer(modifier = Modifier.height(16.dp))
}
