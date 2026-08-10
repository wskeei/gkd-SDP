@file:JvmName("AppBlockerEditor21")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.ui.component.AppPickerDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RuleEditorSheet(
    vm: AppBlockerVm,
    allGroups: List<AppGroup>,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val blockTopEdgeUpwardSwipe = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    AppBlockerEditorPolicy.shouldConsumeTopEdgeUpwardSwipe(
                        firstVisibleItemIndex = listState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                        availableY = available.y,
                    )
                ) {
                    return Offset(x = 0f, y = available.y)
                }
                return Offset.Zero
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(blockTopEdgeUpwardSwipe)
                .padding(16.dp)
        ) {
            item {
                RuleEditorContent(
                    vm = vm,
                    allGroups = allGroups,
                    isLocked = isLocked,
                    onShowAppPicker = { showAppPicker = true },
                    onShowTemplateDialog = { showTemplateDialog = true },
                    onDismiss = onDismiss,
                    onSave = onSave,
                )
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
        AppBlockerTemplatePickerDialog(
            onDismiss = { showTemplateDialog = false },
            onSelect = { template ->
                vm.applyTemplate(template)
                showTemplateDialog = false
            }
        )
    }
}

@Composable
private fun RuleEditorContent(
    vm: AppBlockerVm,
    allGroups: List<AppGroup>,
    isLocked: Boolean,
    onShowAppPicker: () -> Unit,
    onShowTemplateDialog: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Text(
        text = if (vm.editingRule != null) {
            if (isLocked) "查看规则 (已锁定)" else "编辑规则"
        } else {
            "添加规则"
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(24.dp))
    RuleTargetEditor(
        vm = vm,
        allGroups = allGroups,
        isLocked = isLocked,
        onShowAppPicker = onShowAppPicker,
    )
    Spacer(modifier = Modifier.height(16.dp))
    RuleScheduleEditor(
        vm = vm,
        isLocked = isLocked,
        onShowTemplateDialog = onShowTemplateDialog,
    )
    Spacer(modifier = Modifier.height(16.dp))
    RuleEditorActions(
        vm = vm,
        isLocked = isLocked,
        onDismiss = onDismiss,
        onSave = onSave,
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleTargetEditor(
    vm: AppBlockerVm,
    allGroups: List<AppGroup>,
    isLocked: Boolean,
    onShowAppPicker: () -> Unit,
) {
    Text(text = "拦截对象", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_APP,
            onClick = {
                vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_APP
                vm.ruleTargetId = ""
            },
            label = { Text("单独应用") },
            enabled = !isLocked,
        )
        FilterChip(
            selected = vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_GROUP,
            onClick = {
                vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_GROUP
                vm.ruleTargetId = ""
            },
            label = { Text("应用组") },
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_APP) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (vm.ruleTargetId.isBlank()) {
                    "未选择应用"
                } else {
                    try {
                        val appInfo = app.packageManager.getApplicationInfo(vm.ruleTargetId, 0)
                        app.packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        vm.ruleTargetId
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowAppPicker, enabled = !isLocked) {
                Text("选择应用")
            }
        }
    } else if (allGroups.isEmpty()) {
        Text(
            text = "暂无应用组，请先创建应用组",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Text(text = "选择应用组", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allGroups.forEach { group ->
                FilterChip(
                    selected = vm.ruleTargetId == group.id.toString(),
                    onClick = { vm.ruleTargetId = group.id.toString() },
                    label = { Text(group.name) },
                    enabled = !isLocked,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleScheduleEditor(
    vm: AppBlockerVm,
    isLocked: Boolean,
    onShowTemplateDialog: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "时间模板",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowTemplateDialog, enabled = !isLocked) {
            Text("选择模板")
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = "规则模式", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !vm.ruleIsAllowMode,
            onClick = { vm.ruleIsAllowMode = false },
            label = { Text("🚫 禁止时间段") },
            enabled = !isLocked,
        )
        FilterChip(
            selected = vm.ruleIsAllowMode,
            onClick = { vm.ruleIsAllowMode = true },
            label = { Text("✓ 允许时间段") },
            enabled = !isLocked,
        )
    }
    if (vm.ruleIsAllowMode) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "仅在设定的时间段内允许使用，其他时间拦截",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = vm.ruleStartTime,
            onValueChange = { vm.ruleStartTime = it },
            label = { Text("开始时间") },
            placeholder = { Text("22:00") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
        OutlinedTextField(
            value = vm.ruleEndTime,
            onValueChange = { vm.ruleEndTime = it },
            label = { Text("结束时间") },
            placeholder = { Text("08:00") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = "生效日期", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                label = { Text("周${dayNames[day - 1]}") },
                enabled = !isLocked,
            )
        }
    }
}

@Composable
private fun RuleEditorActions(
    vm: AppBlockerVm,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = vm.ruleInterceptMessage,
        onValueChange = { vm.ruleInterceptMessage = it },
        label = { Text("拦截提示语") },
        placeholder = { Text("这真的重要吗？") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isLocked,
    )
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
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text("确定")
        }
    }
}
