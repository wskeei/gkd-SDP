@file:JvmName("UrlBlockerEditor21")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        UrlBlockerTemplatePickerDialog(
            onDismiss = { showTemplateDialog = false },
            onSelect = { template ->
                vm.applyTimeTemplate(template)
                showTemplateDialog = false
            }
        )
    }
}
