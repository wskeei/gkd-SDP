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
import androidx.compose.ui.res.stringResource
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
    state: AppBlockerUiState,
    callbacks: AppBlockerCallbacks,
    isLocked: Boolean = false,
) {
    var showAppPicker by remember { mutableStateOf(false) }
    val pickerConfig = remember(state.groupApps, state.groupEditorMode) {
        buildGroupPickerConfig(
            currentApps = state.groupApps,
            mode = state.groupEditorMode,
        )
    }
    val isAppendMode = state.groupEditorMode == AppBlockerGroupEditorMode.AppendApps
    val isExistingGroup = state.editingGroup != null
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
        onDismissRequest = callbacks.onDismissGroupEditor,
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
                    state.editingGroup == null -> stringResource(R.string.editor_add_app_group)
                    isLocked -> stringResource(R.string.editor_view_app_group_locked)
                    isAppendMode -> stringResource(R.string.editor_add_app)
                    else -> stringResource(R.string.editor_edit_app_group)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.groupName,
                onValueChange = callbacks.onGroupNameChange,
                label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_67f4598335)) },
                placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_6deabd286d)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked && !isAppendMode
            )

            if (isExistingGroup) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_06e3aae567),
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
                    text = li.songe.gkd.sdp.app.getString(R.string.s_71e649ba01, (state.groupApps.size).toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (canOpenAppPicker) {
                    TextButton(onClick = { showAppPicker = true }) {
                        Text(if (isAppendMode) li.songe.gkd.sdp.app.getString(R.string.s_ea8e8dbcb9) else li.songe.gkd.sdp.app.getString(R.string.s_70b208202c))
                    }
                }
            }

            if (state.groupApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.groupApps.forEach { packageName ->
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
                                    callbacks.onRemoveGroupApp(packageName)
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
                    onClick = callbacks.onSaveGroup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(li.songe.gkd.sdp.app.getString(R.string.s_fadf24dbc5))
                }
            } else {
                Button(
                    onClick = callbacks.onDismissGroupEditor,
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

    if (showAppPicker) {
        AppPickerDialog(
            currentApps = pickerConfig.initialSelection,
            excludedApps = pickerConfig.excludedApps,
            titleText = if (isAppendMode) {
                stringResource(R.string.editor_add_app)
            } else {
                stringResource(R.string.editor_choose_app_list)
            },
            emptyText = if (isAppendMode) {
                stringResource(R.string.editor_no_more_apps)
            } else {
                stringResource(R.string.editor_no_matching_apps)
            },
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                callbacks.onGroupAppsPicked(selected)
                showAppPicker = false
            }
        )
    }
}

private data class AppGroupPickerConfig(
    val initialSelection: List<String>,
    val excludedApps: Set<String>,
)

private fun buildGroupPickerConfig(
    currentApps: List<String>,
    mode: AppBlockerGroupEditorMode,
): AppGroupPickerConfig = when (mode) {
    AppBlockerGroupEditorMode.Create -> AppGroupPickerConfig(
        initialSelection = currentApps,
        excludedApps = emptySet(),
    )
    AppBlockerGroupEditorMode.Edit,
    AppBlockerGroupEditorMode.AppendApps -> AppGroupPickerConfig(
        initialSelection = emptyList(),
        excludedApps = currentApps.toSet(),
    )
}
