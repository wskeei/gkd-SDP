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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlGroupEditorSheet(
    editingGroup: UrlRuleGroup?,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var groupName by remember(editingGroup?.id) { mutableStateOf(editingGroup?.name.orEmpty()) }
    var groupQuickUrls by remember(editingGroup?.id) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = if (editingGroup != null) {
                    if (isLocked) stringResource(R.string.s_769abeb44a) else stringResource(R.string.s_6cede3740a)
                } else stringResource(R.string.s_1658680c1d),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text(stringResource(R.string.s_8b779bd414)) },
                placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_62a6243ff9)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = groupQuickUrls,
                onValueChange = { groupQuickUrls = it },
                label = { Text(stringResource(R.string.s_beea7e4cae)) },
                placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_115793186c)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                enabled = !isLocked,
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (!isLocked) {
                Button(
                    onClick = { onSave(groupName, groupQuickUrls) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                ) { Text(li.songe.gkd.sdp.app.getString(R.string.s_f526c89937)) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UrlRuleEditorSheet(
    editingRule: UrlBlockRule?,
    allGroups: List<UrlRuleGroup>,
    initialGroupId: Long,
    initialTimeRule: UrlTimeRule?,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (UrlRuleDraft) -> Unit,
) {
    val formKey = editingRule?.id ?: -initialGroupId
    var pattern by remember(formKey) { mutableStateOf(editingRule?.pattern.orEmpty()) }
    var matchType by remember(formKey) {
        mutableStateOf(editingRule?.matchType ?: UrlBlockRule.MATCH_TYPE_DOMAIN)
    }
    var name by remember(formKey) { mutableStateOf(editingRule?.name.orEmpty()) }
    var redirectUrl by remember(formKey) {
        mutableStateOf(editingRule?.redirectUrl ?: UrlBlockRule.DEFAULT_REDIRECT_URL)
    }
    var showIntercept by remember(formKey) { mutableStateOf(editingRule?.showIntercept ?: true) }
    var interceptMessage by remember(formKey) {
        mutableStateOf(
            editingRule?.interceptMessage
                ?: li.songe.gkd.sdp.app.getString(R.string.common_default_intercept_message),
        )
    }
    var groupId by remember(formKey) { mutableStateOf(editingRule?.groupId ?: initialGroupId) }
    var startTime by remember(formKey) {
        mutableStateOf(initialTimeRule?.startTime ?: "00:00")
    }
    var endTime by remember(formKey) {
        mutableStateOf(initialTimeRule?.endTime ?: "23:59")
    }
    var daysOfWeek by remember(formKey) {
        mutableStateOf(initialTimeRule?.getDaysOfWeekList() ?: listOf(1, 2, 3, 4, 5, 6, 7))
    }
    var isAllowMode by remember(formKey) { mutableStateOf(initialTimeRule?.isAllowMode ?: false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            item {
                UrlRuleIdentityFields(
                    pattern = pattern,
                    matchType = matchType,
                    name = name,
                    editingRule = editingRule,
                    isLocked = isLocked,
                    onPatternChange = { pattern = it },
                    onMatchTypeChange = { matchType = it },
                    onNameChange = { name = it },
                )
                UrlRuleGroupFields(
                    selectedGroupId = groupId,
                    allGroups = allGroups,
                    isLocked = isLocked,
                    onSelectGroupId = { groupId = it },
                )
                UrlRuleTimeFields(
                    startTime = startTime,
                    endTime = endTime,
                    daysOfWeek = daysOfWeek,
                    isAllowMode = isAllowMode,
                    isLocked = isLocked,
                    onStartTimeChange = { startTime = it },
                    onEndTimeChange = { endTime = it },
                    onDaysChange = { daysOfWeek = it },
                    onAllowModeChange = { isAllowMode = it },
                )
                UrlRuleSaveButton(
                    isLocked = isLocked,
                    onDismiss = onDismiss,
                    onSave = {
                        onSave(
                            UrlRuleDraft(
                                pattern = pattern,
                                matchType = matchType,
                                name = name,
                                redirectUrl = redirectUrl,
                                showIntercept = showIntercept,
                                interceptMessage = interceptMessage,
                                groupId = groupId,
                                timeRuleStartTime = startTime,
                                timeRuleEndTime = endTime,
                                timeRuleDaysOfWeek = daysOfWeek,
                                timeRuleIsAllowMode = isAllowMode,
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun UrlRuleIdentityFields(
    pattern: String,
    matchType: Int,
    name: String,
    editingRule: UrlBlockRule?,
    isLocked: Boolean,
    onPatternChange: (String) -> Unit,
    onMatchTypeChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
) {
    Text(
        text = if (editingRule != null) {
            if (isLocked) stringResource(R.string.s_f387d20cb8) else stringResource(R.string.s_13794d2141)
        } else stringResource(R.string.s_d2fc32282a),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(
        value = pattern,
        onValueChange = onPatternChange,
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
        value = name,
        onValueChange = onNameChange,
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
            selected = matchType == UrlBlockRule.MATCH_TYPE_DOMAIN,
            onClick = { onMatchTypeChange(UrlBlockRule.MATCH_TYPE_DOMAIN) },
            label = { Text(stringResource(R.string.s_8cda652587)) },
            enabled = !isLocked,
        )
        FilterChip(
            selected = matchType == UrlBlockRule.MATCH_TYPE_PREFIX,
            onClick = { onMatchTypeChange(UrlBlockRule.MATCH_TYPE_PREFIX) },
            label = { Text(stringResource(R.string.s_036f009257)) },
            enabled = !isLocked,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UrlRuleGroupFields(
    selectedGroupId: Long,
    allGroups: List<UrlRuleGroup>,
    isLocked: Boolean,
    onSelectGroupId: (Long) -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_8e9d261743), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedGroupId == 0L,
            onClick = { onSelectGroupId(0L) },
            label = { Text(stringResource(R.string.s_8de957a7b7)) },
            enabled = !isLocked,
        )
        allGroups.forEach { group ->
            FilterChip(
                selected = selectedGroupId == group.id,
                onClick = { onSelectGroupId(group.id) },
                label = { Text(group.name) },
                enabled = !isLocked,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UrlRuleTimeFields(
    startTime: String,
    endTime: String,
    daysOfWeek: List<Int>,
    isAllowMode: Boolean,
    isLocked: Boolean,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onDaysChange: (List<Int>) -> Unit,
    onAllowModeChange: (Boolean) -> Unit,
) {
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_9748286edf), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.s_a89571c669), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !isAllowMode,
            onClick = { onAllowModeChange(false) },
            label = { Text(stringResource(R.string.s_837212d5ad)) },
            enabled = !isLocked,
        )
        FilterChip(
            selected = isAllowMode,
            onClick = { onAllowModeChange(true) },
            label = { Text(stringResource(R.string.s_78bb3ad69e)) },
            enabled = !isLocked,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = startTime,
            onValueChange = onStartTimeChange,
            label = { Text(stringResource(R.string.s_e8868af6eb)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !isLocked,
        )
        OutlinedTextField(
            value = endTime,
            onValueChange = onEndTimeChange,
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
                        if (daysOfWeek.contains(day)) daysOfWeek - day else (daysOfWeek + day).sorted(),
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
