package li.songe.gkd.sdp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
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
import li.songe.gkd.sdp.util.appInfoMapFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Destination<RootGraph>
@Composable
fun UsageGuardPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UsageGuardVm>()
    val settings by storeFlow.collectAsState()
    val appProfiles by vm.appProfilesFlow.collectAsState()
    val tags by vm.tagsFlow.collectAsState()
    val history by vm.historyFlow.collectAsState()
    val appInfoMap by appInfoMapFlow.collectAsState()

    val selectedTargetApps = appProfiles.filter { it.selectedTarget }.map { it.appId }
    val whitelistApps = appProfiles.filter { it.globalWhitelist }.map { it.appId }
    val profileMap = appProfiles.associateBy { it.appId }
    val globalOverrideApps = appProfiles.filter {
        !it.globalWhitelist && it.grantMode != settings.usageGuardDefaultGrantMode
    }.map { it.appId }.distinct()

    var minReasonLengthText by remember(settings.usageGuardMinReasonLength) {
        mutableStateOf(settings.usageGuardMinReasonLength.toString())
    }
    var customTagText by remember { mutableStateOf("") }
    var showSelectedPicker by remember { mutableStateOf(false) }
    var showWhitelistPicker by remember { mutableStateOf(false) }
    var showOverridePicker by remember { mutableStateOf(false) }

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
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            item(key = "enabled") {
                SettingCard(
                    title = "功能总开关",
                    subtitle = if (settings.usageGuardEnabled) "已启用" else "未启用",
                ) {
                    Switch(
                        checked = settings.usageGuardEnabled,
                        onCheckedChange = vm::updateEnabled,
                    )
                }
            }

            item(key = "scope") {
                ChoiceCard(title = "生效范围") {
                    FilterChip(
                        selected = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                        onClick = { vm.updateScopeMode(UsageGuardPolicy.SCOPE_SELECTED_ONLY) },
                        label = { Text("仅选中应用") },
                    )
                    FilterChip(
                        selected = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
                        onClick = { vm.updateScopeMode(UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST) },
                        label = { Text("全局生效（白名单跳过）") },
                    )
                }
            }

            item(key = "default_grant") {
                ChoiceCard(title = "默认授权模式") {
                    FilterChip(
                        selected = settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT,
                        onClick = { vm.updateDefaultGrantMode(UsageGuardPolicy.GRANT_MODE_STRICT) },
                        label = { Text("严格") },
                    )
                    FilterChip(
                        selected = settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                        onClick = { vm.updateDefaultGrantMode(UsageGuardPolicy.GRANT_MODE_RESUMABLE) },
                        label = { Text("普通") },
                    )
                }
            }

            item(key = "min_reason") {
                ElevatedCard(
                    colors = surfaceCardColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("最少理由字数", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = minReasonLengthText,
                                onValueChange = {
                                    if (it.all(Char::isDigit)) {
                                        minReasonLengthText = it
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("字数") },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    vm.updateMinReasonLength(minReasonLengthText.toIntOrNull() ?: settings.usageGuardMinReasonLength)
                                },
                            ) {
                                Text("保存")
                            }
                        }
                    }
                }
            }

            item(key = "scope_apps") {
                ElevatedCard(
                    colors = surfaceCardColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val isSelectedMode = settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY
                        Text(
                            text = if (isSelectedMode) "选中应用列表" else "白名单列表",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Button(
                            onClick = {
                                if (isSelectedMode) {
                                    showSelectedPicker = true
                                } else {
                                    showWhitelistPicker = true
                                }
                            }
                        ) {
                            Text(if (isSelectedMode) "选择受控应用" else "选择白名单应用")
                        }
                        val currentApps = if (isSelectedMode) selectedTargetApps else whitelistApps
                        if (currentApps.isEmpty()) {
                            Text(
                                text = "尚未选择应用",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            currentApps.forEach { appId ->
                                val appName = appInfoMap[appId]?.name ?: appId
                                AppProfileRow(
                                    appId = appId,
                                    appName = appName,
                                    grantMode = profileMap[appId]?.grantMode ?: settings.usageGuardDefaultGrantMode,
                                    onGrantModeChange = { vm.saveAppGrantMode(appId, it) },
                                )
                            }
                        }

                        if (!isSelectedMode) {
                            HorizontalDivider()
                            Text(
                                text = "授权模式覆盖",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "为全局受控应用单独指定严格/普通模式",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = { showOverridePicker = true }) {
                                Text("选择覆盖应用")
                            }
                            if (globalOverrideApps.isEmpty()) {
                                Text("尚未添加覆盖应用")
                            } else {
                                globalOverrideApps.forEach { appId ->
                                    val appName = appInfoMap[appId]?.name ?: appId
                                    AppProfileRow(
                                        appId = appId,
                                        appName = appName,
                                        grantMode = profileMap[appId]?.grantMode ?: settings.usageGuardDefaultGrantMode,
                                        onGrantModeChange = { vm.saveAppGrantMode(appId, it) },
                                        onClearOverride = { vm.clearAppGrantModeOverride(appId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "tags") {
                ElevatedCard(
                    colors = surfaceCardColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("全局标签库管理", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = { if (!tag.isPreset) vm.deleteCustomTag(tag) },
                                    label = { Text(if (tag.isPreset) tag.name else "${tag.name} ×") },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customTagText,
                                onValueChange = { customTagText = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("新增标签") },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    vm.addCustomTag(customTagText)
                                    customTagText = ""
                                }
                            ) {
                                Text("添加")
                            }
                        }
                    }
                }
            }

            item(key = "history") {
                ElevatedCard(
                    colors = surfaceCardColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("最近申请记录", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (history.isEmpty()) {
                            Text("暂无记录")
                        } else {
                            history.forEachIndexed { index, record ->
                                HistoryRow(record = record, appName = appInfoMap[record.appId]?.name ?: record.appName)
                                if (index != history.lastIndex) {
                                    HorizontalDivider()
                                }
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
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
            trailing()
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AppProfileRow(
    appId: String,
    appName: String,
    grantMode: Int,
    onGrantModeChange: (Int) -> Unit,
    onClearOverride: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(appId = appId)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(appId, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = grantMode == UsageGuardPolicy.GRANT_MODE_STRICT,
                onClick = { onGrantModeChange(UsageGuardPolicy.GRANT_MODE_STRICT) },
                label = { Text("严格") },
            )
            FilterChip(
                selected = grantMode == UsageGuardPolicy.GRANT_MODE_RESUMABLE,
                onClick = { onGrantModeChange(UsageGuardPolicy.GRANT_MODE_RESUMABLE) },
                label = { Text("普通") },
            )
            if (onClearOverride != null) {
                FilterChip(
                    selected = false,
                    onClick = onClearOverride,
                    label = { Text("移除覆盖") },
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: UsageGuardRecord,
    appName: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(appName, fontWeight = FontWeight.SemiBold)
        Text(
            text = record.tagNames.joinToString(" · "),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        )
        Text(record.reasonText)
        Text("时长 ${record.requestedDurationMinutes} 分钟")
        Text(record.endStateText(), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
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
