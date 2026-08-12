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
    state: AppBlockerUiState,
    callbacks: AppBlockerCallbacks,
    isLocked: Boolean = false,
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
        onDismissRequest = callbacks.onDismissRuleEditor,
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
                    state = state,
                    callbacks = callbacks,
                    isLocked = isLocked,
                    onShowAppPicker = { showAppPicker = true },
                    onShowTemplateDialog = { showTemplateDialog = true },
                )
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            currentApps = if (state.ruleTargetId.isBlank()) emptyList() else listOf(state.ruleTargetId),
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                callbacks.onRuleTargetIdChange(selected.firstOrNull() ?: "")
                showAppPicker = false
            },
            singleSelect = true
        )
    }

    if (showTemplateDialog) {
        AppBlockerTemplatePickerDialog(
            onDismiss = { showTemplateDialog = false },
            onSelect = { template ->
                callbacks.onApplyTimeTemplate(template)
                showTemplateDialog = false
            }
        )
    }
}

@Composable
private fun RuleEditorContent(
    state: AppBlockerUiState,
    callbacks: AppBlockerCallbacks,
    isLocked: Boolean,
    onShowAppPicker: () -> Unit,
    onShowTemplateDialog: () -> Unit,
) {
    Text(
        text = if (state.editingRule != null) {
            if (isLocked) stringResource(R.string.s_f387d20cb8) else stringResource(R.string.s_13794d2141)
        } else {
            stringResource(R.string.s_d2fc32282a)
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(24.dp))
    RuleTargetEditor(
        state = state,
        callbacks = callbacks,
        isLocked = isLocked,
        onShowAppPicker = onShowAppPicker,
    )
    Spacer(modifier = Modifier.height(16.dp))
    RuleScheduleEditor(
        state = state,
        callbacks = callbacks,
        isLocked = isLocked,
        onShowTemplateDialog = onShowTemplateDialog,
    )
    Spacer(modifier = Modifier.height(16.dp))
    RuleEditorActions(
        state = state,
        callbacks = callbacks,
        isLocked = isLocked,
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleTargetEditor(
    state: AppBlockerUiState,
    callbacks: AppBlockerCallbacks,
    isLocked: Boolean,
    onShowAppPicker: () -> Unit,
) {
    Text(text = stringResource(R.string.s_cbd16b0221), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.ruleTargetType == BlockTimeRule.TARGET_TYPE_APP,
            onClick = { callbacks.onRuleTargetTypeChange(BlockTimeRule.TARGET_TYPE_APP) },
            label = { Text(stringResource(R.string.s_74c7776c98)) },
            enabled = !isLocked,
        )
        FilterChip(
            selected = state.ruleTargetType == BlockTimeRule.TARGET_TYPE_GROUP,
            onClick = { callbacks.onRuleTargetTypeChange(BlockTimeRule.TARGET_TYPE_GROUP) },
            label = { Text(stringResource(R.string.s_c46c8c9e4d)) },
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (state.ruleTargetType == BlockTimeRule.TARGET_TYPE_APP) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.ruleTargetId.isBlank()) {
                    stringResource(R.string.s_496e1f9b69)
                } else {
                    try {
                        val appInfo = app.packageManager.getApplicationInfo(state.ruleTargetId, 0)
                        app.packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        state.ruleTargetId
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowAppPicker, enabled = !isLocked) {
                Text(stringResource(R.string.s_9ec480c1e4))
            }
        }
    } else if (state.allGroups.isEmpty()) {
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
            state.allGroups.forEach { group ->
                FilterChip(
                    selected = state.ruleTargetId == group.id.toString(),
                    onClick = { callbacks.onRuleTargetIdChange(group.id.toString()) },
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
    state: AppBlockerUiState,
    callbacks: AppBlockerCallbacks,
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
            selected = !state.ruleIsAllowMode,
            onClick = { callbacks.onRuleIsAllowModeChange(false) },
            label = { Text(stringResource(R.string.s_837212d5ad)) },
            enabled = !isLocked,
        )
        FilterChip(
            selected = state.ruleIsAllowMode,
            onClick = { callbacks.onRuleIsAllowModeChange(true) },
            label = { Text(stringResource(R.string.s_78bb3ad69e)) },
            enabled = !isLocked,
        )
    }
    if (state.ruleIsAllowMode) {
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
            value = state.ruleStartTime,
            onValueChange = callbacks.onRuleStartTimeChange,
            label = { Text(stringResource(R.string.s_e8868af6eb)) },
            placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_9f82f6d52b)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
        OutlinedTextField(
            value = state.ruleEndTime,
            onValueChange = callbacks.onRuleEndTimeChange,
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
        val dayNames = mapOf(
            1 to R.string.day_monday,
            2 to R.string.day_tuesday,
            3 to R.string.day_wednesday,
            4 to R.string.day_thursday,
            5 to R.string.day_friday,
            6 to R.string.day_saturday,
            7 to R.string.day_sunday,
        )
        (1..7).forEach { day ->
            FilterChip(
                selected = state.ruleDaysOfWeek.contains(day),
                onClick = {
                    callbacks.onRuleDaysOfWeekChange(
                        if (state.ruleDaysOfWeek.contains(day)) {
                            state.ruleDaysOfWeek - day
                        } else {
                            (state.ruleDaysOfWeek + day).sorted()
                        },
                    )
                },
                label = {
                    Text(
                        li.songe.gkd.sdp.app.getString(
                            R.string.s_a94243a9c8,
                            li.songe.gkd.sdp.app.getString(dayNames.getValue(day)),
                        ),
                    )
                },
                enabled = !isLocked,
            )
        }
    }
}

@Composable
private fun RuleEditorActions(
    state: AppBlockerUiState,
    callbacks: AppBlockerCallbacks,
    isLocked: Boolean,
) {
    OutlinedTextField(
        value = state.ruleInterceptMessage,
        onValueChange = callbacks.onRuleInterceptMessageChange,
        label = { Text(stringResource(R.string.s_f82dffbf08)) },
        placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_b3d972565c)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isLocked,
    )
    Spacer(modifier = Modifier.height(24.dp))
    if (!isLocked) {
        Button(onClick = callbacks.onSaveRule, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.s_fadf24dbc5))
        }
    } else {
        Button(
            onClick = callbacks.onDismissRuleEditor,
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
