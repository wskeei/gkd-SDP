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
                title = { Text("使用申请") },
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
        title = "规则与申请偏好",
        subtitle = "把默认模式、理由门槛和常用时长收紧到你真正会用的那组值。",
    ) {
        PreferenceBlock(
            title = "生效范围",
            supporting = "选中应用适合精细控制；全局模式适合高压场景。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                    onClick = { vm.updateScopeMode(UsageGuardPolicy.SCOPE_SELECTED_ONLY) },
                    label = { Text("仅选中应用") },
                )
                FilterChip(
                    selected = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
                    onClick = { vm.updateScopeMode(UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST) },
                    label = { Text("全局生效") },
                )
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = "默认授权模式",
            supporting = "严格模式离开即失效，普通模式在到时前可继续返回。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT,
                    onClick = { vm.updateDefaultGrantMode(UsageGuardPolicy.GRANT_MODE_STRICT) },
                    label = { Text("严格模式") },
                )
                FilterChip(
                    selected = settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                    onClick = { vm.updateDefaultGrantMode(UsageGuardPolicy.GRANT_MODE_RESUMABLE) },
                    label = { Text("普通模式") },
                )
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = "理由门槛",
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
                    label = { Text("最少字数") },
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
                    Text("保存")
                }
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = "四个快速时长",
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
                        label = { Text("选项 ${index + 1}") },
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
                Text("保存时长选项")
            }
        }
        HorizontalDivider()
        PreferenceBlock(
            title = "常用标签库",
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
                                if (tag.isPreset) tag.name else "${tag.name} ×",
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
                    label = { Text("新增标签") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        vm.addCustomTag(customTagText.value)
                        customTagText.value = ""
                    },
                ) {
                    Text("添加")
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
        title = "应用管理",
        subtitle = "图标化浏览优先于长列表，点按图标后再切换模式或移出列表。",
    ) {
        if (settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("仅选中应用", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                TextButton(onClick = { showSelectedPicker.value = true }) { Text("选择受控应用") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SelectedAppModeBoard(
                title = "严格模式",
                subtitle = "离开应用后重新申请",
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
                title = "普通模式",
                subtitle = "到时前可继续返回",
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
                    Text("白名单应用", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "这些应用在全局模式下可直接跳过使用申请。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showWhitelistPicker.value = true }) { Text("选择白名单") }
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
                    Text("模式覆盖", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "为个别全局受控应用单独指定严格或普通模式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showOverridePicker.value = true }) { Text("选择覆盖应用") }
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
        title = "记录浏览",
        subtitle = "默认查看今天的申请记录，切换日期时只保留所选那一天的内容。",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("按日期筛选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "当前查看 ${selectedDate.value.format(usageGuardDateFormatter)}",
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
            Text("所选日期暂无记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
