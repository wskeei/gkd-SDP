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
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

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
        text = if (vm.editingRule != null) stringResource(R.string.s_13794d2141) else stringResource(R.string.s_d2fc32282a),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(
        value = vm.ruleName,
        onValueChange = { vm.ruleName = it },
        label = { Text(stringResource(R.string.s_1937bcb105)) },
        placeholder = { Text(app.getString(R.string.s_1eb2bb81e0)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_7655f477f1), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = vm.ruleType == FocusRule.RULE_TYPE_QUICK_START,
            onClick = { vm.ruleType = FocusRule.RULE_TYPE_QUICK_START },
            label = { Text(stringResource(R.string.s_bac4c36ee7)) },
        )
        FilterChip(
            selected = vm.ruleType == FocusRule.RULE_TYPE_SCHEDULED,
            onClick = { vm.ruleType = FocusRule.RULE_TYPE_SCHEDULED },
            label = { Text(stringResource(R.string.s_a497f76289)) },
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun RuleEditorTypeFields(vm: FocusModeVm) {
    if (vm.ruleType == FocusRule.RULE_TYPE_QUICK_START) {
        Text(stringResource(R.string.s_427069f0a2), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = vm.ruleDurationHours.toString(),
                onValueChange = { vm.ruleDurationHours = it.toIntOrNull()?.coerceIn(0, 48) ?: 0 },
                label = { Text(stringResource(R.string.s_99f6904ff3)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = vm.ruleDurationMinutes.toString(),
                onValueChange = { vm.ruleDurationMinutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                label = { Text(stringResource(R.string.s_28bf227b9b)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        if (vm.ruleTotalDurationMinutes < 5) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.s_09c309db65), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = vm.ruleIsLocked, onCheckedChange = { vm.ruleIsLocked = it })
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.s_9c66857925))
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = vm.ruleStartTime,
                onValueChange = { vm.ruleStartTime = it },
                label = { Text(app.getString(R.string.s_e8868af6eb)) },
                placeholder = { Text(app.getString(R.string.s_9f82f6d52b)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = vm.ruleEndTime,
                onValueChange = { vm.ruleEndTime = it },
                label = { Text(app.getString(R.string.s_a0bb9f49ab)) },
                placeholder = { Text(app.getString(R.string.s_df4803c166)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(app.getString(R.string.s_d642f8ef29), style = MaterialTheme.typography.bodyMedium)
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
                    label = { Text(app.getString(R.string.s_a94243a9c8, dayNames[day - 1])) },
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
        label = { Text(stringResource(R.string.s_f82dffbf08)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.s_a322744a5c, vm.ruleWhitelistApps.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onShowWhitelistPicker) { Text(stringResource(R.string.s_70b208202c)) }
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
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.s_fadf24dbc5)) }
    Spacer(modifier = Modifier.height(16.dp))
}
