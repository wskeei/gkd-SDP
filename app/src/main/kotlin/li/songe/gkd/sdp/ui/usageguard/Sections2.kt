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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.data.*
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.ui.component.*
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app
@Composable
internal fun UsageGuardSettingsList(
    mainVm: MainViewModel,
    vm: UsageGuardVm,
    settings: SettingsStore,
    appProfiles: List<UsageGuardAppProfile>,
    tags: List<UsageGuardTag>,
    history: List<UsageGuardRecord>,
    groupedApps: UsageGuardUiStatePolicy.SelectedAppSections,
    durationOptions: List<Int>,
    appInfoMap: Map<String, AppInfo>,
    context: Context,
) {
    val selectedTargetApps = remember(appProfiles) { appProfiles.filter { it.selectedTarget }.map { it.appId } }
    val whitelistApps = remember(appProfiles) { appProfiles.filter { it.globalWhitelist }.map { it.appId } }
    val profileMap = remember(appProfiles) { appProfiles.associateBy { it.appId } }
    val globalOverrideApps = remember(appProfiles, settings.usageGuardDefaultGrantMode) {
        appProfiles.filter { !it.globalWhitelist && it.grantMode != settings.usageGuardDefaultGrantMode }.map { it.appId }
    }
    val minReasonLengthText = remember(settings.usageGuardMinReasonLength) {
        mutableStateOf(settings.usageGuardMinReasonLength.toString())
    }
    val durationOptionTexts = remember(durationOptions) { mutableStateOf(durationOptions.map(Int::toString)) }
    val customTagText = remember { mutableStateOf("") }
    val selectedDate = remember { mutableStateOf(java.time.LocalDate.now()) }
    val showSelectedPicker = remember { mutableStateOf(false) }
    val showWhitelistPicker = remember { mutableStateOf(false) }
    val showOverridePicker = remember { mutableStateOf(false) }
    val appAction = remember { mutableStateOf<UsageGuardAppAction?>(null) }
    val strictBoardBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val resumableBoardBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val draggingAppId = remember { mutableStateOf<String?>(null) }
    val state = UsageGuardSettingsRenderState(
        mainVm, vm, settings, tags, history, groupedApps, durationOptions, appInfoMap, context,
        selectedTargetApps, whitelistApps, globalOverrideApps, profileMap,
        minReasonLengthText, durationOptionTexts, customTagText, selectedDate,
        showSelectedPicker, showWhitelistPicker, showOverridePicker, appAction,
        strictBoardBounds, resumableBoardBounds, draggingAppId,
    )
    LaunchedEffect(selectedDate.value) {
        vm.updateSelectedHistoryDate(selectedDate.value)
    }
    UsageGuardSettingsScaffold(state)
    UsageGuardDialogs(state)
}
@Composable
private fun UsageGuardSettingsScaffold(state: UsageGuardSettingsRenderState) {
    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { state.mainVm.popPage() },
                    )
                },
                title = { Text(app.getString(R.string.s_356c996618)) },
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
            item(key = "history_section") { UsageGuardHistorySection(state) }
        }
    }
}
@Composable
private fun UsageGuardRulesSection(state: UsageGuardSettingsRenderState) {
    val settings = state.settings
    val vm = state.vm
    val tags = state.tags
    val minReasonLengthText = state.minReasonLengthText
    val durationOptionTexts = state.durationOptionTexts
    val customTagText = state.customTagText
    SectionCard(
        title = stringResource(R.string.s_4b7e05a71a),
        subtitle = stringResource(R.string.s_0c81c7ca27),
    ) {
        PreferenceBlock(
            title = stringResource(R.string.s_a6a2d4845d),
            supporting = "选中应用适合精细控制；全局模式适合高压场景。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                    onClick = { vm.updateScopeMode(UsageGuardPolicy.SCOPE_SELECTED_ONLY) },
                    label = { Text(stringResource(R.string.s_2a5a0db475)) },
                )
                FilterChip(
                    selected = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
                    onClick = { vm.updateScopeMode(UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST) },
                    label = { Text(stringResource(R.string.s_3af2ad9aac)) },
                )
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = stringResource(R.string.s_cb1d1e7bde),
            supporting = "严格模式离开即失效，普通模式在到时前可继续返回。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT,
                    onClick = { vm.updateDefaultGrantMode(UsageGuardPolicy.GRANT_MODE_STRICT) },
                    label = { Text(stringResource(R.string.s_cce3d12ecc)) },
                )
                FilterChip(
                    selected = settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                    onClick = { vm.updateDefaultGrantMode(UsageGuardPolicy.GRANT_MODE_RESUMABLE) },
                    label = { Text(stringResource(R.string.s_e8a4554eb3)) },
                )
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = stringResource(R.string.s_be695b05b4),
            supporting = "保存后会直接影响申请弹窗的最少字数要求。",
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
                    label = { Text(stringResource(R.string.s_dec2ec4618)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = {
                        vm.updateMinReasonLength(
                            minReasonLengthText.value.toIntOrNull() ?: settings.usageGuardMinReasonLength,
                        )
                    },
                ) {
                    Text(stringResource(R.string.s_fadf24dbc5))
                }
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = stringResource(R.string.s_067f4e9588),
            supporting = "申请弹窗会优先展示这四个固定时长，把自定义留在次级入口。",
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
                        label = { Text(app.getString(R.string.s_fc0d628dd8, index + 1)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    vm.updateDurationOptions(durationOptionTexts.value.map { it.toIntOrNull() ?: 0 })
                },
            ) {
                Text(stringResource(R.string.s_b0871a4a6b))
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = stringResource(R.string.s_9d227c591a),
            supporting = "预设标签负责快速说明动机，自定义标签只保留你真正常用的词。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = false,
                        onClick = { if (!tag.isPreset) vm.deleteCustomTag(tag) },
                        label = {
                            Text(
                                if (tag.isPreset) tag.name else app.getString(R.string.s_fd325af405, tag.name),
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
                    label = { Text(stringResource(R.string.s_71f86583e1)) },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        vm.addCustomTag(customTagText.value)
                        customTagText.value = ""
                    },
                ) {
                    Text(stringResource(R.string.s_94191ce210))
                }
            }
        }
    }
}
@Composable
private fun UsageGuardAppsSection(state: UsageGuardSettingsRenderState) {
    val settings = state.settings
    val vm = state.vm
    val groupedApps = state.groupedApps
    val appInfoMap = state.appInfoMap
    val whitelistApps = state.whitelistApps
    val globalOverrideApps = state.globalOverrideApps
    val profileMap = state.profileMap
    val showSelectedPicker = state.showSelectedPicker
    val showWhitelistPicker = state.showWhitelistPicker
    val showOverridePicker = state.showOverridePicker
    val appAction = state.appAction
    val strictBoardBounds = state.strictBoardBounds
    val resumableBoardBounds = state.resumableBoardBounds
    val draggingAppId = state.draggingAppId
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
                    Text(stringResource(R.string.s_2a5a0db475), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (draggingAppId.value == null) {
                            "点按图标可改模式，长按拖到另一列可直接切换。"
                        } else {
                            "拖到另一列松手即可切换模式。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showSelectedPicker.value = true }) { Text(stringResource(R.string.s_e0c8442c8f)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SelectedAppModeBoard(
                title = stringResource(R.string.s_cce3d12ecc),
                subtitle = stringResource(R.string.s_a12b6a9ddd),
                appIds = groupedApps.strictAppIds,
                appInfoMap = appInfoMap,
                onBoardBoundsChanged = { strictBoardBounds.value = it },
                onAppClick = {
                    appAction.value = UsageGuardAppAction(it, UsageGuardActionScope.Selected)
                },
                onDragStart = { appId -> draggingAppId.value = appId },
                onDragEnd = { appId, dropPosition ->
                    val targetMode = when {
                        strictBoardBounds.value?.contains(dropPosition) == true ->
                            UsageGuardPolicy.GRANT_MODE_STRICT
                        resumableBoardBounds.value?.contains(dropPosition) == true ->
                            UsageGuardPolicy.GRANT_MODE_RESUMABLE
                        else -> null
                    }
                    draggingAppId.value = null
                    if (targetMode != null && profileMap[appId]?.grantMode != targetMode) {
                        vm.moveSelectedAppToGrantMode(appId, targetMode)
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            SelectedAppModeBoard(
                title = stringResource(R.string.s_e8a4554eb3),
                subtitle = stringResource(R.string.s_c288fd13e0),
                appIds = groupedApps.resumableAppIds,
                appInfoMap = appInfoMap,
                onBoardBoundsChanged = { resumableBoardBounds.value = it },
                onAppClick = {
                    appAction.value = UsageGuardAppAction(it, UsageGuardActionScope.Selected)
                },
                onDragStart = { appId -> draggingAppId.value = appId },
                onDragEnd = { appId, dropPosition ->
                    val targetMode = when {
                        strictBoardBounds.value?.contains(dropPosition) == true ->
                            UsageGuardPolicy.GRANT_MODE_STRICT
                        resumableBoardBounds.value?.contains(dropPosition) == true ->
                            UsageGuardPolicy.GRANT_MODE_RESUMABLE
                        else -> null
                    }
                    draggingAppId.value = null
                    if (targetMode != null && profileMap[appId]?.grantMode != targetMode) {
                        vm.moveSelectedAppToGrantMode(appId, targetMode)
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
                    Text(stringResource(R.string.s_8a87deaa49), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.s_4c57a177b2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showWhitelistPicker.value = true }) { Text(stringResource(R.string.s_4adcd23b06)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            IconAppFlow(
                appIds = whitelistApps,
                appInfoMap = appInfoMap,
                emptyText = "暂无白名单应用",
                onAppClick = {
                    appAction.value = UsageGuardAppAction(it, UsageGuardActionScope.Whitelist)
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.s_3bddb65762), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.s_a597b31e88),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showOverridePicker.value = true }) { Text(stringResource(R.string.s_3ab400ba10)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            IconAppFlow(
                appIds = globalOverrideApps,
                appInfoMap = appInfoMap,
                emptyText = "暂无模式覆盖应用",
                onAppClick = {
                    appAction.value = UsageGuardAppAction(it, UsageGuardActionScope.Override)
                },
            )
        }
    }
}
@Composable
private fun UsageGuardHistorySection(state: UsageGuardSettingsRenderState) {
    val selectedDate = state.selectedDate
    val context = state.context
    val history = state.history
    val appInfoMap = state.appInfoMap
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
                Text(stringResource(R.string.s_2cf75123ae), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.s_5a46f954cd, selectedDate.value.format(usageGuardDateFormatter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            selectedDate.value = LocalDate.of(year, month + 1, dayOfMonth)
                        },
                        selectedDate.value.year,
                        selectedDate.value.monthValue - 1,
                        selectedDate.value.dayOfMonth,
                    ).show()
                },
            ) {
                Text(selectedDate.value.format(usageGuardDateFormatter))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (history.isEmpty()) {
            Text(stringResource(R.string.s_3e964b109c), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
