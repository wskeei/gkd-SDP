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
import li.songe.gkd.sdp.R

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
                        if (isLocked) li.songe.gkd.sdp.app.getString(R.string.s_3352552afe) else li.songe.gkd.sdp.app.getString(R.string.s_3fb9d5b75c)
                    } else li.songe.gkd.sdp.app.getString(R.string.s_ca22cd537c),
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
                        text = li.songe.gkd.sdp.app.getString(R.string.s_a36a90e3d1),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showTemplateDialog = true },
                        enabled = !isLocked
                    ) {
                        Text(li.songe.gkd.sdp.app.getString(R.string.s_860cb31951))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 模式选择
                Text(li.songe.gkd.sdp.app.getString(R.string.s_a89571c669), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !vm.timeRuleIsAllowMode,
                        onClick = { vm.timeRuleIsAllowMode = false },
                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_837212d5ad)) },
                        enabled = !isLocked
                    )
                    FilterChip(
                        selected = vm.timeRuleIsAllowMode,
                        onClick = { vm.timeRuleIsAllowMode = true },
                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_78bb3ad69e)) },
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
                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_e8868af6eb)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isLocked
                    )
                    OutlinedTextField(
                        value = vm.timeRuleEndTime,
                        onValueChange = { vm.timeRuleEndTime = it },
                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_a0bb9f49ab)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isLocked
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 星期选择
                Text(li.songe.gkd.sdp.app.getString(R.string.s_d642f8ef29), style = MaterialTheme.typography.bodyMedium)
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
                            label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_a94243a9c8, dayNames[day - 1])) },
                            enabled = !isLocked
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!isLocked) {
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                        Text(li.songe.gkd.sdp.app.getString(R.string.s_fadf24dbc5))
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
                        Text(li.songe.gkd.sdp.app.getString(R.string.s_f526c89937))
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
