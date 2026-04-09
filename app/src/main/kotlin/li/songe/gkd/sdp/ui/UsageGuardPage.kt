package li.songe.gkd.sdp.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.AppPickerDialog
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import li.songe.gkd.sdp.util.appInfoMapFlow

private val usageGuardDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val usageGuardTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private enum class UsageGuardActionScope { Selected, Whitelist, Override }

private data class UsageGuardAppAction(val appId: String, val scope: UsageGuardActionScope)

// UI in this page must follow .impeccable.md:
// stronger grouping, less vertical bloat, deliberate feedback, and a more ritualized feel.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Destination<RootGraph>
@Composable
fun UsageGuardPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UsageGuardVm>()
    val settings by storeFlow.collectAsState()
    val appProfiles by vm.appProfilesFlow.collectAsState()
    val tags by vm.tagsFlow.collectAsState()
    val history by vm.historyFlow.collectAsState()
    val groupedApps by vm.selectedAppSectionsFlow.collectAsState()
    val durationOptions by vm.durationOptionsFlow.collectAsState()
    val appInfoMap by appInfoMapFlow.collectAsState()
    val context = LocalContext.current

    val selectedTargetApps = remember(appProfiles) {
        appProfiles.filter { it.selectedTarget }.map { it.appId }
    }
    val whitelistApps = remember(appProfiles) {
        appProfiles.filter { it.globalWhitelist }.map { it.appId }
    }
    val profileMap = remember(appProfiles) { appProfiles.associateBy { it.appId } }
    val globalOverrideApps = remember(appProfiles, settings.usageGuardDefaultGrantMode) {
        appProfiles.filter {
            !it.globalWhitelist && it.grantMode != settings.usageGuardDefaultGrantMode
        }.map { it.appId }
    }

    var minReasonLengthText by remember(settings.usageGuardMinReasonLength) {
        mutableStateOf(settings.usageGuardMinReasonLength.toString())
    }
    var durationOptionTexts by remember(durationOptions) {
        mutableStateOf(durationOptions.map(Int::toString))
    }
    var customTagText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showSelectedPicker by remember { mutableStateOf(false) }
    var showWhitelistPicker by remember { mutableStateOf(false) }
    var showOverridePicker by remember { mutableStateOf(false) }
    var appAction by remember { mutableStateOf<UsageGuardAppAction?>(null) }
    var strictBoardBounds by remember { mutableStateOf<Rect?>(null) }
    var resumableBoardBounds by remember { mutableStateOf<Rect?>(null) }
    var draggingAppId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDate) {
        vm.updateSelectedHistoryDate(selectedDate)
    }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popBackStack() },
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
            item(key = "status_section") {
                SectionCard(
                    title = "保护状态",
                    subtitle = "先确认总开关，再核对当前保护范围和默认策略。",
                ) {
                    SettingRow(
                        title = "使用申请总开关",
                        subtitle = if (settings.usageGuardEnabled) {
                            "已启用，打开受控应用前需要先申请"
                        } else {
                            "未启用，当前不会拦截受控应用"
                        },
                        trailing = {
                            Switch(
                                checked = settings.usageGuardEnabled,
                                onCheckedChange = vm::updateEnabled,
                            )
                        },
                    )
                    HorizontalDivider()
                    CompactInfoRow(
                        label = "当前范围",
                        value = if (settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY) {
                            "仅选中应用"
                        } else {
                            "全局生效（白名单跳过）"
                        },
                    )
                    CompactInfoRow(
                        label = "默认授权",
                        value = if (settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT) {
                            "严格模式"
                        } else {
                            "普通模式"
                        },
                    )
                    CompactInfoRow(
                        label = "快速时长",
                        value = durationOptions.joinToString(" / ") { "${it}分钟" },
                    )
                    Text(
                        text = UsageGuardUiStatePolicy.protectionStatusAutoReenableMessage(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item(key = "rules_section") {
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
                                value = minReasonLengthText,
                                onValueChange = { value ->
                                    if (value.all(Char::isDigit)) minReasonLengthText = value
                                },
                                modifier = Modifier.width(132.dp),
                                label = { Text("最少字数") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            Button(
                                onClick = {
                                    vm.updateMinReasonLength(
                                        minReasonLengthText.toIntOrNull() ?: settings.usageGuardMinReasonLength,
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
                            durationOptionTexts.forEachIndexed { index, text ->
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { value ->
                                        if (value.all(Char::isDigit)) {
                                            durationOptionTexts = durationOptionTexts.toMutableList().also {
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
                                vm.updateDurationOptions(durationOptionTexts.map { it.toIntOrNull() ?: 0 })
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
                                value = customTagText,
                                onValueChange = { customTagText = it },
                                modifier = Modifier.width(132.dp),
                                label = { Text("新增标签") },
                                singleLine = true,
                            )
                            Button(
                                onClick = {
                                    vm.addCustomTag(customTagText)
                                    customTagText = ""
                                },
                            ) {
                                Text("添加")
                            }
                        }
                    }
                }
            }

            item(key = "app_section") {
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
                                    if (draggingAppId == null) {
                                        "点按图标可改模式，长按拖到另一列可直接切换。"
                                    } else {
                                        "拖到另一列松手即可切换模式。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { showSelectedPicker = true }) { Text("选择受控应用") }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        SelectedAppModeBoard(
                            title = "严格模式",
                            subtitle = "离开应用后重新申请",
                            appIds = groupedApps.strictAppIds,
                            appInfoMap = appInfoMap,
                            onBoardBoundsChanged = { strictBoardBounds = it },
                            onAppClick = {
                                appAction = UsageGuardAppAction(it, UsageGuardActionScope.Selected)
                            },
                            onDragStart = { appId -> draggingAppId = appId },
                            onDragEnd = { appId, dropPosition ->
                                val targetMode = when {
                                    strictBoardBounds?.contains(dropPosition) == true ->
                                        UsageGuardPolicy.GRANT_MODE_STRICT
                                    resumableBoardBounds?.contains(dropPosition) == true ->
                                        UsageGuardPolicy.GRANT_MODE_RESUMABLE
                                    else -> null
                                }
                                draggingAppId = null
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
                            onBoardBoundsChanged = { resumableBoardBounds = it },
                            onAppClick = {
                                appAction = UsageGuardAppAction(it, UsageGuardActionScope.Selected)
                            },
                            onDragStart = { appId -> draggingAppId = appId },
                            onDragEnd = { appId, dropPosition ->
                                val targetMode = when {
                                    strictBoardBounds?.contains(dropPosition) == true ->
                                        UsageGuardPolicy.GRANT_MODE_STRICT
                                    resumableBoardBounds?.contains(dropPosition) == true ->
                                        UsageGuardPolicy.GRANT_MODE_RESUMABLE
                                    else -> null
                                }
                                draggingAppId = null
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
                            TextButton(onClick = { showWhitelistPicker = true }) { Text("选择白名单") }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        IconAppFlow(
                            appIds = whitelistApps,
                            appInfoMap = appInfoMap,
                            emptyText = "暂无白名单应用",
                            onAppClick = {
                                appAction = UsageGuardAppAction(it, UsageGuardActionScope.Whitelist)
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
                            TextButton(onClick = { showOverridePicker = true }) { Text("选择覆盖应用") }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        IconAppFlow(
                            appIds = globalOverrideApps,
                            appInfoMap = appInfoMap,
                            emptyText = "暂无模式覆盖应用",
                            onAppClick = {
                                appAction = UsageGuardAppAction(it, UsageGuardActionScope.Override)
                            },
                        )
                    }
                }
            }

            item(key = "history_section") {
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
                                "当前查看 ${selectedDate.format(usageGuardDateFormatter)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
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
        }
    }

    if (showSelectedPicker) {
        AppPickerDialog(
            currentApps = selectedTargetApps,
            onDismiss = { showSelectedPicker = false },
            onConfirm = {
                vm.saveSelectedTargets(it)
                showSelectedPicker = false
            },
        )
    }
    if (showWhitelistPicker) {
        AppPickerDialog(
            currentApps = whitelistApps,
            onDismiss = { showWhitelistPicker = false },
            onConfirm = {
                vm.saveWhitelist(it)
                showWhitelistPicker = false
            },
        )
    }
    if (showOverridePicker) {
        AppPickerDialog(
            currentApps = globalOverrideApps,
            onDismiss = { showOverridePicker = false },
            onConfirm = {
                vm.saveGrantModeOverrideApps(it)
                showOverridePicker = false
            },
        )
    }
    appAction?.let { target ->
        val currentGrantMode = profileMap[target.appId]?.grantMode ?: settings.usageGuardDefaultGrantMode
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { appAction = null }, sheetState = sheetState) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(appInfoMap[target.appId]?.name ?: target.appId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (target.scope != UsageGuardActionScope.Whitelist) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT,
                            onClick = {
                                vm.moveSelectedAppToGrantMode(target.appId, UsageGuardPolicy.GRANT_MODE_STRICT)
                                appAction = null
                            },
                            label = { Text("严格模式") },
                        )
                        FilterChip(
                            selected = currentGrantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                            onClick = {
                                vm.moveSelectedAppToGrantMode(target.appId, UsageGuardPolicy.GRANT_MODE_RESUMABLE)
                                appAction = null
                            },
                            label = { Text("普通模式") },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        when (target.scope) {
                            UsageGuardActionScope.Selected -> vm.saveSelectedTargets(selectedTargetApps - target.appId)
                            UsageGuardActionScope.Whitelist -> vm.saveWhitelist(whitelistApps - target.appId)
                            UsageGuardActionScope.Override -> vm.clearAppGrantModeOverride(target.appId)
                        }
                        appAction = null
                    },
                ) {
                    Text(
                        when (target.scope) {
                            UsageGuardActionScope.Selected -> "移出受控应用"
                            UsageGuardActionScope.Whitelist -> "移出白名单"
                            UsageGuardActionScope.Override -> "移除模式覆盖"
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier.fillMaxWidth().itemPadding(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun CompactInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PreferenceBlock(
    title: String,
    supporting: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SelectedAppModeBoard(
    title: String,
    subtitle: String,
    appIds: List<String>,
    appInfoMap: Map<String, AppInfo>,
    onBoardBoundsChanged: (Rect) -> Unit,
    onAppClick: (String) -> Unit,
    onDragStart: (String) -> Unit,
    onDragEnd: (String, Offset) -> Unit,
) {
    Column(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            onBoardBoundsChanged(coordinates.boundsInWindow())
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (appIds.isEmpty()) {
            Text("这一列还没有应用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                appIds.forEach { appId ->
                    DraggableAppIcon(
                        appId = appId,
                        appName = appInfoMap[appId]?.name ?: appId,
                        onClick = { onAppClick(appId) },
                        onDragStart = { onDragStart(appId) },
                        onDrop = { onDragEnd(appId, it) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconAppFlow(
    appIds: List<String>,
    appInfoMap: Map<String, AppInfo>,
    emptyText: String,
    onAppClick: (String) -> Unit,
) {
    if (appIds.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            appIds.forEach { appId ->
                Column(
                    modifier = Modifier.width(72.dp).clickable { onAppClick(appId) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        AppIcon(appId = appId, modifier = Modifier.size(48.dp))
                    }
                    Text(
                        text = appInfoMap[appId]?.name ?: appId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableAppIcon(
    appId: String,
    appName: String,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrop: (Offset) -> Unit,
) {
    var dragOffset by remember(appId) { mutableStateOf(Offset.Zero) }
    var originInWindow by remember(appId) { mutableStateOf(Offset.Zero) }
    var tileSize by remember(appId) { mutableStateOf(IntSize.Zero) }
    Column(
        modifier = Modifier
            .width(72.dp)
            .pointerInput(appId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = {
                        val center = Offset(tileSize.width / 2f, tileSize.height / 2f)
                        onDrop(originInWindow + dragOffset + center)
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = { dragOffset = Offset.Zero },
                ) { _, dragAmount ->
                    dragOffset += dragAmount
                }
            }
            .clickable(onClick = onClick)
            .onGloballyPositioned { coordinates ->
                originInWindow = coordinates.positionInWindow()
                tileSize = coordinates.size
            }
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
            }
            .zIndex(if (dragOffset != Offset.Zero) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            AppIcon(appId = appId, modifier = Modifier.size(48.dp))
        }
        Text(appName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HistoryRow(record: UsageGuardRecord, appName: String) {
    val requestedAt = remember(record.requestedAt) {
        Instant.ofEpochMilli(record.requestedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                requestedAt.format(usageGuardTimeFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(record.tagNames.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(record.reasonText, style = MaterialTheme.typography.bodyMedium)
        Text("申请 ${record.requestedDurationMinutes} 分钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(record.endStateText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun UsageGuardRecord.endStateText(): String {
    return when (endReason) {
        UsageGuardRecord.END_REASON_ACTIVE -> "进行中"
        UsageGuardRecord.END_REASON_EXPIRED -> "已到时"
        UsageGuardRecord.END_REASON_LEFT_APP -> "离开应用结束"
        UsageGuardRecord.END_REASON_REPLACED -> "已被新的申请替换"
        UsageGuardRecord.END_REASON_HOME_BUTTON -> "已回到桌面"
        else -> "未知状态"
    }
}
