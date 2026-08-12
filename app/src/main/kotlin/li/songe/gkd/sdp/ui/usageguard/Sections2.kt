@file:JvmName("UsageGuardSections2")
package li.songe.gkd.sdp.ui
import android.content.Context
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import li.songe.gkd.sdp.data.*
import li.songe.gkd.sdp.ui.component.*
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.UsageGuardPolicy
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
@Composable
internal fun UsageGuardSettingsList(
    state: UsageGuardSettingsRenderState,
    context: Context,
) {
    UsageGuardSettingsScaffold(state, context)
    UsageGuardDialogs(state)
}
@Composable
private fun UsageGuardSettingsScaffold(
    state: UsageGuardSettingsRenderState,
    context: Context,
) {
    Scaffold(
        modifier = Modifier.testTag("usage_guard_settings_list"),
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = state.onBack,
                    )
                },
                title = { Text(li.songe.gkd.sdp.app.getString(R.string.s_356c996618)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "status_section") { UsageGuardStatusSection(state) }
            item(key = "rules_section") { UsageGuardRulesSection(state) }
            item(key = "app_section") { UsageGuardAppsSection(state) }
            item(key = "history_section") { UsageGuardHistorySection(state, context) }
        }
    }
}
@Composable
private fun UsageGuardRulesSection(state: UsageGuardSettingsRenderState) {
    val settings = state.state.settings
    val onDispatch = state.onDispatch
    val tags = state.state.tags
    val minReasonLengthText = remember(settings.usageGuardMinReasonLength) {
        mutableStateOf(settings.usageGuardMinReasonLength.toString())
    }
    val durationOptionTexts = remember(state.state.durationOptions) {
        mutableStateOf(state.state.durationOptions.map(Int::toString))
    }
    val customTagText = remember { mutableStateOf("") }
    SectionCard(
        title = stringResource(R.string.s_4b7e05a71a),
        subtitle = stringResource(R.string.s_0c81c7ca27),
    ) {
        PreferenceBlock(
            title = li.songe.gkd.sdp.app.getString(R.string.s_a6a2d4845d),
            supporting = li.songe.gkd.sdp.app.getString(R.string.usage_guard_scope_hint),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                    onClick = { onDispatch(UsageGuardAction.UpdateScopeMode(UsageGuardPolicy.SCOPE_SELECTED_ONLY)) },
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_2a5a0db475)) },
                )
                FilterChip(
                    selected = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
                    onClick = { onDispatch(UsageGuardAction.UpdateScopeMode(UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST)) },
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_3af2ad9aac)) },
                )
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = li.songe.gkd.sdp.app.getString(R.string.s_cb1d1e7bde),
            supporting = li.songe.gkd.sdp.app.getString(R.string.usage_guard_strict_mode_hint),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT,
                    onClick = { onDispatch(UsageGuardAction.UpdateDefaultGrantMode(UsageGuardPolicy.GRANT_MODE_STRICT)) },
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_cce3d12ecc)) },
                )
                FilterChip(
                    selected = settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                    onClick = { onDispatch(UsageGuardAction.UpdateDefaultGrantMode(UsageGuardPolicy.GRANT_MODE_RESUMABLE)) },
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_e8a4554eb3)) },
                )
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = li.songe.gkd.sdp.app.getString(R.string.s_be695b05b4),
            supporting = li.songe.gkd.sdp.app.getString(R.string.usage_guard_min_reason_hint),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = minReasonLengthText.value,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) minReasonLengthText.value = value
                    },
                    modifier = Modifier.width(132.dp),
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_dec2ec4618)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = {
                        onDispatch(
                            UsageGuardAction.UpdateMinReasonLength(
                                minReasonLengthText.value.toIntOrNull() ?: settings.usageGuardMinReasonLength,
                            ),
                        )
                    },
                ) {
                    Text(li.songe.gkd.sdp.app.getString(R.string.s_fadf24dbc5))
                }
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = li.songe.gkd.sdp.app.getString(R.string.s_067f4e9588),
            supporting = li.songe.gkd.sdp.app.getString(R.string.usage_guard_duration_options_hint),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                durationOptionTexts.value.forEachIndexed { index, text ->
                    OutlinedTextField(
                        value = text,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit)) {
                                durationOptionTexts.value = durationOptionTexts.value.toMutableList().also {
                                    it[index] = value
                                }
                            }
                        },
                        modifier = Modifier.width(112.dp),
                        label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_fc0d628dd8, (index + 1).toString())) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    onDispatch(
                        UsageGuardAction.UpdateDurationOptions(
                            durationOptionTexts.value.map { it.toIntOrNull() ?: 0 },
                        ),
                    )
                },
            ) {
                Text(li.songe.gkd.sdp.app.getString(R.string.s_b0871a4a6b))
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = li.songe.gkd.sdp.app.getString(R.string.s_9d227c591a),
            supporting = li.songe.gkd.sdp.app.getString(R.string.usage_guard_tag_hint),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = false,
                        onClick = { if (!tag.isPreset) state.onDeleteCustomTag(tag) },
                        label = {
                            Text(
                                if (tag.isPreset) tag.name else li.songe.gkd.sdp.app.getString(R.string.s_fd325af405, (tag.name).toString()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = customTagText.value,
                    onValueChange = { customTagText.value = it },
                    modifier = Modifier.width(132.dp),
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_71f86583e1)) },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        onDispatch(UsageGuardAction.AddCustomTag(customTagText.value))
                        customTagText.value = ""
                    },
                ) {
                    Text(li.songe.gkd.sdp.app.getString(R.string.s_94191ce210))
                }
            }
        }
    }
}
@Composable
private fun UsageGuardAppsSection(state: UsageGuardSettingsRenderState) {
    val settings = state.state.settings
    val groupedApps = state.state.groupedApps
    val appInfoMap = state.state.appInfoMap
    val whitelistApps = state.state.whitelistApps
    val globalOverrideApps = state.state.globalOverrideApps
    val profileMap = state.state.profileMap
    val draggingAppId = state.state.draggingAppId
    SectionCard(
        title = stringResource(R.string.s_52fa962ab3),
        subtitle = stringResource(R.string.s_34a4bd22d5),
    ) {
        if (settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(li.songe.gkd.sdp.app.getString(R.string.s_2a5a0db475), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (draggingAppId == null) {
                            stringResource(R.string.usage_guard_drag_instruction_tap)
                        } else {
                            stringResource(R.string.usage_guard_drag_instruction_drop)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = state.onOpenSelectedPicker) { Text(li.songe.gkd.sdp.app.getString(R.string.s_e0c8442c8f)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SelectedAppModeBoard(
                title = stringResource(R.string.usage_guard_strict_mode_title),
                subtitle = stringResource(R.string.usage_guard_strict_mode_subtitle),
                appIds = groupedApps.strictAppIds,
                appInfoMap = appInfoMap,
                onBoardBoundsChanged = state.onStrictBoardBounds,
                onAppClick = {
                    state.onOpenAppAction(it, UsageGuardActionScope.Selected)
                },
                onDragStart = state.onDraggingAppId,
                onDragEnd = { appId, dropPosition ->
                    val targetMode = when {
                        state.state.strictBoardBounds?.contains(dropPosition) == true ->
                            UsageGuardPolicy.GRANT_MODE_STRICT
                        state.state.resumableBoardBounds?.contains(dropPosition) == true ->
                            UsageGuardPolicy.GRANT_MODE_RESUMABLE
                        else -> null
                    }
                    state.onDraggingAppId(null)
                    if (targetMode != null && profileMap[appId]?.grantMode != targetMode) {
                        state.onDispatch(UsageGuardAction.MoveSelectedAppToGrantMode(appId, targetMode))
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            SelectedAppModeBoard(
                title = stringResource(R.string.usage_guard_normal_mode_title),
                subtitle = stringResource(R.string.usage_guard_normal_mode_subtitle),
                appIds = groupedApps.resumableAppIds,
                appInfoMap = appInfoMap,
                onBoardBoundsChanged = state.onResumableBoardBounds,
                onAppClick = {
                    state.onOpenAppAction(it, UsageGuardActionScope.Selected)
                },
                onDragStart = state.onDraggingAppId,
                onDragEnd = { appId, dropPosition ->
                    val targetMode = when {
                        state.state.strictBoardBounds?.contains(dropPosition) == true ->
                            UsageGuardPolicy.GRANT_MODE_STRICT
                        state.state.resumableBoardBounds?.contains(dropPosition) == true ->
                            UsageGuardPolicy.GRANT_MODE_RESUMABLE
                        else -> null
                    }
                    state.onDraggingAppId(null)
                    if (targetMode != null && profileMap[appId]?.grantMode != targetMode) {
                        state.onDispatch(UsageGuardAction.MoveSelectedAppToGrantMode(appId, targetMode))
                    }
                },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(li.songe.gkd.sdp.app.getString(R.string.s_8a87deaa49), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        li.songe.gkd.sdp.app.getString(R.string.s_4c57a177b2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = state.onOpenWhitelistPicker) { Text(li.songe.gkd.sdp.app.getString(R.string.s_4adcd23b06)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            IconAppFlow(
                appIds = whitelistApps,
                appInfoMap = appInfoMap,
                emptyText = stringResource(R.string.usage_guard_whitelist_empty),
                onAppClick = {
                    state.onOpenAppAction(it, UsageGuardActionScope.Whitelist)
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(li.songe.gkd.sdp.app.getString(R.string.s_3bddb65762), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        li.songe.gkd.sdp.app.getString(R.string.s_a597b31e88),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = state.onOpenOverridePicker) { Text(li.songe.gkd.sdp.app.getString(R.string.s_3ab400ba10)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            IconAppFlow(
                appIds = globalOverrideApps,
                appInfoMap = appInfoMap,
                emptyText = stringResource(R.string.usage_guard_override_empty),
                onAppClick = {
                    state.onOpenAppAction(it, UsageGuardActionScope.Override)
                },
            )
        }
    }
}
@Composable
private fun UsageGuardHistorySection(
    state: UsageGuardSettingsRenderState,
    context: Context,
) {
    val selectedDate = LocalDate.ofEpochDay(state.state.selectedHistoryDateEpochDay)
    val history = state.state.history
    val appInfoMap = state.state.appInfoMap
    SectionCard(
        title = stringResource(R.string.s_242a10d8a9),
        subtitle = stringResource(R.string.s_20b86aa7ab),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(li.songe.gkd.sdp.app.getString(R.string.s_2cf75123ae), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    li.songe.gkd.sdp.app.getString(R.string.s_5a46f954cd, (selectedDate.format(usageGuardDateFormatter)).toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            state.onSelectDate(LocalDate.of(year, month + 1, dayOfMonth))
                        },
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth,
                    ).show()
                },
            ) {
                Text(selectedDate.format(usageGuardDateFormatter))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (history.isEmpty()) {
            Text(li.songe.gkd.sdp.app.getString(R.string.s_3e964b109c), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            history.forEachIndexed { index, record ->
                HistoryRow(record = record, appName = appInfoMap[record.appId]?.name ?: record.appName)
                if (index != history.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}
