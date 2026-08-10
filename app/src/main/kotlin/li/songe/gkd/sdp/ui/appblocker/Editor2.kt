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
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

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
            if (isLocked) stringResource(R.string.s_f387d20cb8) else stringResource(R.string.s_13794d2141)
        } else {
            stringResource(R.string.s_d2fc32282a)
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
    Text(text = stringResource(R.string.s_cbd16b0221), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_APP,
            onClick = {
                vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_APP
                vm.ruleTargetId = ""
            },
            label = { Text(stringResource(R.string.s_74c7776c98)) },
            enabled = !isLocked,
        )
        FilterChip(
            selected = vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_GROUP,
            onClick = {
                vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_GROUP
                vm.ruleTargetId = ""
            },
            label = { Text(stringResource(R.string.s_c46c8c9e4d)) },
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
                    stringResource(R.string.s_496e1f9b69)
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
                Text(stringResource(R.string.s_9ec480c1e4))
            }
        }
    } else if (allGroups.isEmpty()) {
        Text(
            text = stringResource(R.string.s_56eaee3096),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Text(text = li.songe.gkd.sdp.app.getString(R.string.s_e3e710b8a5), style = MaterialTheme.typography.bodySmall)
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
            text = stringResource(R.string.s_a36a90e3d1),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowTemplateDialog, enabled = !isLocked) {
            Text(stringResource(R.string.s_860cb31951))
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = stringResource(R.string.s_a89571c669), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !vm.ruleIsAllowMode,
            onClick = { vm.ruleIsAllowMode = false },
            label = { Text(stringResource(R.string.s_837212d5ad)) },
            enabled = !isLocked,
        )
        FilterChip(
            selected = vm.ruleIsAllowMode,
            onClick = { vm.ruleIsAllowMode = true },
            label = { Text(stringResource(R.string.s_78bb3ad69e)) },
            enabled = !isLocked,
        )
    }
    if (vm.ruleIsAllowMode) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.s_8a4ee1e5ae),
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
            label = { Text(stringResource(R.string.s_e8868af6eb)) },
            placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_9f82f6d52b)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
        OutlinedTextField(
            value = vm.ruleEndTime,
            onValueChange = { vm.ruleEndTime = it },
            label = { Text(stringResource(R.string.s_a0bb9f49ab)) },
            placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_a843b2d4ca)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = stringResource(R.string.s_d642f8ef29), style = MaterialTheme.typography.bodyMedium)
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
                label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_a94243a9c8, dayNames[day - 1])) },
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
        label = { Text(stringResource(R.string.s_f82dffbf08)) },
        placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_b3d972565c)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isLocked,
    )
    Spacer(modifier = Modifier.height(24.dp))
    if (!isLocked) {
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.s_fadf24dbc5))
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
            Text(li.songe.gkd.sdp.app.getString(R.string.s_f526c89937))
        }
    }
}
