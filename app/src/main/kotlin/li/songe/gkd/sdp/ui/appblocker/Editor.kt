@file:JvmName("AppBlockerEditor0")

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import li.songe.gkd.sdp.ui.component.AppPickerDialog
import li.songe.gkd.sdp.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GroupEditorSheet(
    vm: AppBlockerVm,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    val pickerConfig = remember(vm.groupApps, vm.groupEditorMode) {
        AppBlockerVm.buildGroupPickerConfig(
            currentApps = vm.groupApps,
            mode = vm.groupEditorMode,
        )
    }
    val isAppendMode = vm.groupEditorMode == AppBlockerVm.GroupEditorMode.AppendApps
    val isExistingGroup = vm.editingGroup != null
    val appsReadOnly = isExistingGroup
    val canOpenAppPicker = !isLocked && (!isExistingGroup || isAppendMode)
    val scrollState = rememberScrollState()
    val blockTopEdgeUpwardSwipe = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    AppBlockerEditorPolicy.shouldConsumeTopEdgeUpwardSwipe(
                        firstVisibleItemIndex = 0,
                        firstVisibleItemScrollOffset = scrollState.value,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(blockTopEdgeUpwardSwipe)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = when {
                    vm.editingGroup == null -> "添加应用组"
                    isLocked -> "查看应用组 (已锁定)"
                    isAppendMode -> "添加应用"
                    else -> "编辑应用组"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = vm.groupName,
                onValueChange = { vm.groupName = it },
                label = { Text(app.getString(R.string.s_67f4598335)) },
                placeholder = { Text(app.getString(R.string.s_6deabd286d)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked && !isAppendMode
            )

            if (isExistingGroup) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = app.getString(R.string.s_06e3aae567),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = app.getString(R.string.s_71e649ba01, vm.groupApps.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (canOpenAppPicker) {
                    TextButton(onClick = { showAppPicker = true }) {
                        Text(if (isAppendMode) app.getString(R.string.s_ea8e8dbcb9) else app.getString(R.string.s_70b208202c))
                    }
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
                            onClick = {
                                if (!appsReadOnly && !isLocked) {
                                    vm.removeAppFromGroup(packageName)
                                }
                            },
                            label = { Text(appName) },
                            enabled = !isLocked && !appsReadOnly
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isLocked) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(app.getString(R.string.s_fadf24dbc5))
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
                    Text(app.getString(R.string.s_f526c89937))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            currentApps = pickerConfig.initialSelection,
            excludedApps = pickerConfig.excludedApps,
            titleText = if (isAppendMode) "添加应用" else "选择应用列表",
            emptyText = if (isAppendMode) "没有可继续添加的应用" else "未找到匹配的应用",
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                vm.applyPickedApps(selected)
                showAppPicker = false
            }
        )
    }
}
