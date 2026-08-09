@file:JvmName("UrlBlockerDialogs0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.ui.component.AppPickerDialog
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.util.appInfoMapFlow

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
