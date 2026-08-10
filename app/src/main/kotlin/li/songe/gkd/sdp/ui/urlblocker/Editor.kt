@file:JvmName("UrlBlockerEditor0")

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlGroupEditorSheet(
    vm: UrlBlockVm,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = if (vm.editingGroup != null) {
                    if (isLocked) stringResource(R.string.s_769abeb44a) else stringResource(R.string.s_6cede3740a)
                } else stringResource(R.string.s_1658680c1d),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = vm.groupName,
                onValueChange = { vm.groupName = it },
                label = { Text(stringResource(R.string.s_8b779bd414)) },
                placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_62a6243ff9)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = vm.groupQuickUrls,
                onValueChange = { vm.groupQuickUrls = it },
                label = { Text(stringResource(R.string.s_beea7e4cae)) },
                placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_115793186c)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                enabled = !isLocked,
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (!isLocked) {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.s_fadf24dbc5)) }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) { Text(li.songe.gkd.sdp.app.getString(R.string.s_f526c89937)) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UrlRuleEditorSheet(
    vm: UrlBlockVm,
    allGroups: List<UrlRuleGroup>,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            item {
                UrlRuleIdentityFields(vm = vm, isLocked = isLocked)
                UrlRuleGroupFields(vm = vm, allGroups = allGroups, isLocked = isLocked)
                UrlRuleTimeFields(vm = vm, isLocked = isLocked)
                UrlRuleSaveButton(isLocked = isLocked, onDismiss = onDismiss, onSave = onSave)
            }
        }
    }
}

@Composable
private fun UrlRuleIdentityFields(vm: UrlBlockVm, isLocked: Boolean) {
    Text(
        text = if (vm.editingUrlRule != null) {
            if (isLocked) stringResource(R.string.s_f387d20cb8) else stringResource(R.string.s_13794d2141)
        } else stringResource(R.string.s_d2fc32282a),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(
        value = vm.urlPattern,
        onValueChange = { vm.urlPattern = it },
        label = { Text(stringResource(R.string.s_86629471c3)) },
        placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_29aa760a1e)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isLocked,
    )
    Text(
        text = stringResource(R.string.s_6662277b90),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = vm.urlName,
        onValueChange = { vm.urlName = it },
        label = { Text(stringResource(R.string.s_5f519ca1d8)) },
        placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_f4fb517c9c)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isLocked,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_6a05dbdc88), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = vm.urlMatchType == UrlBlockRule.MATCH_TYPE_DOMAIN,
            onClick = { vm.urlMatchType = UrlBlockRule.MATCH_TYPE_DOMAIN },
            label = { Text(stringResource(R.string.s_8cda652587)) },
            enabled = !isLocked,
        )
        FilterChip(
            selected = vm.urlMatchType == UrlBlockRule.MATCH_TYPE_PREFIX,
            onClick = { vm.urlMatchType = UrlBlockRule.MATCH_TYPE_PREFIX },
            label = { Text(stringResource(R.string.s_036f009257)) },
            enabled = !isLocked,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UrlRuleGroupFields(vm: UrlBlockVm, allGroups: List<UrlRuleGroup>, isLocked: Boolean) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_8e9d261743), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = vm.urlGroupId == 0L,
            onClick = { vm.urlGroupId = 0L },
            label = { Text(stringResource(R.string.s_8de957a7b7)) },
            enabled = !isLocked,
        )
        allGroups.forEach { group ->
            FilterChip(
                selected = vm.urlGroupId == group.id,
                onClick = { vm.urlGroupId = group.id },
                label = { Text(group.name) },
                enabled = !isLocked,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UrlRuleTimeFields(vm: UrlBlockVm, isLocked: Boolean) {
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_9748286edf), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_a89571c669), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !vm.timeRuleIsAllowMode,
            onClick = { vm.timeRuleIsAllowMode = false },
            label = { Text(stringResource(R.string.s_837212d5ad)) },
            enabled = !isLocked,
        )
        FilterChip(
            selected = vm.timeRuleIsAllowMode,
            onClick = { vm.timeRuleIsAllowMode = true },
            label = { Text(stringResource(R.string.s_78bb3ad69e)) },
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = vm.timeRuleStartTime,
            onValueChange = { vm.timeRuleStartTime = it },
            label = { Text(stringResource(R.string.s_e8868af6eb)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
        OutlinedTextField(
            value = vm.timeRuleEndTime,
            onValueChange = { vm.timeRuleEndTime = it },
            label = { Text(stringResource(R.string.s_a0bb9f49ab)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_d642f8ef29), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val currentDays = vm.timeRuleDaysOfWeek
        (1..7).forEach { day ->
            FilterChip(
                selected = currentDays.contains(day),
                onClick = {
                    vm.timeRuleDaysOfWeek = if (currentDays.contains(day)) currentDays - day else (currentDays + day).sorted()
                },
                label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_a94243a9c8, dayNames[day - 1])) },
                enabled = !isLocked,
            )
        }
    }
}

@Composable
private fun UrlRuleSaveButton(isLocked: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    Spacer(modifier = Modifier.height(24.dp))
    if (!isLocked) {
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.s_fadf24dbc5)) }
    } else {
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) { Text(li.songe.gkd.sdp.app.getString(R.string.s_f526c89937)) }
    }
    Spacer(modifier = Modifier.height(16.dp))
}
