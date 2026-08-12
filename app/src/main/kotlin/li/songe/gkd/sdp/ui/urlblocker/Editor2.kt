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
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimeRuleEditorSheet(
    editingRule: UrlTimeRule?,
    targetType: Int,
    targetId: Long,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (UrlTimeRuleDraft) -> Unit
) {
    val formKey = editingRule?.id ?: -targetId
    var startTime by remember(formKey) { mutableStateOf(editingRule?.startTime ?: "22:00") }
    var endTime by remember(formKey) { mutableStateOf(editingRule?.endTime ?: "08:00") }
    var daysOfWeek by remember(formKey) {
        mutableStateOf(editingRule?.getDaysOfWeekList() ?: listOf(1, 2, 3, 4, 5, 6, 7))
    }
    var isAllowMode by remember(formKey) { mutableStateOf(editingRule?.isAllowMode ?: false) }
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
                TimeRuleEditorContent(
                    editingRule = editingRule,
                    isLocked = isLocked,
                    startTime = startTime,
                    endTime = endTime,
                    daysOfWeek = daysOfWeek,
                    isAllowMode = isAllowMode,
                    onStartTimeChange = { startTime = it },
                    onEndTimeChange = { endTime = it },
                    onDaysChange = { daysOfWeek = it },
                    onAllowModeChange = { isAllowMode = it },
                    onOpenTemplate = { showTemplateDialog = true },
                    onSave = {
                        onSave(
                            UrlTimeRuleDraft(
                                targetType = targetType,
                                targetId = targetId,
                                startTime = startTime,
                                endTime = endTime,
                                daysOfWeek = daysOfWeek,
                                isAllowMode = isAllowMode,
                            )
                        )
                    },
                    onDismiss = onDismiss,
                )
            }
        }
    }

    if (showTemplateDialog) {
        UrlBlockerTemplatePickerDialog(
            onDismiss = { showTemplateDialog = false },
            onSelect = { template ->
                startTime = template.startTime
                endTime = template.endTime
                daysOfWeek = template.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
                showTemplateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeRuleEditorContent(
    editingRule: UrlTimeRule?,
    isLocked: Boolean,
    startTime: String,
    endTime: String,
    daysOfWeek: List<Int>,
    isAllowMode: Boolean,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onDaysChange: (List<Int>) -> Unit,
    onAllowModeChange: (Boolean) -> Unit,
    onOpenTemplate: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = if (editingRule != null) {
            if (isLocked) li.songe.gkd.sdp.app.getString(R.string.s_3352552afe) else li.songe.gkd.sdp.app.getString(R.string.s_3fb9d5b75c)
        } else li.songe.gkd.sdp.app.getString(R.string.s_ca22cd537c),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(24.dp))

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
            onClick = onOpenTemplate,
            enabled = !isLocked
        ) {
            Text(li.songe.gkd.sdp.app.getString(R.string.s_860cb31951))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(li.songe.gkd.sdp.app.getString(R.string.s_a89571c669), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !isAllowMode,
            onClick = { onAllowModeChange(false) },
            label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_837212d5ad)) },
            enabled = !isLocked
        )
        FilterChip(
            selected = isAllowMode,
            onClick = { onAllowModeChange(true) },
            label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_78bb3ad69e)) },
            enabled = !isLocked
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = startTime,
            onValueChange = onStartTimeChange,
            label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_e8868af6eb)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked
        )
        OutlinedTextField(
            value = endTime,
            onValueChange = onEndTimeChange,
            label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_a0bb9f49ab)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(li.songe.gkd.sdp.app.getString(R.string.s_d642f8ef29), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                selected = daysOfWeek.contains(day),
                onClick = {
                    onDaysChange(
                        if (daysOfWeek.contains(day)) {
                            daysOfWeek - day
                        } else {
                            (daysOfWeek + day).sorted()
                        }
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
