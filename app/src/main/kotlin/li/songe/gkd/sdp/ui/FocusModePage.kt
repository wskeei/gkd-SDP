package li.songe.gkd.sdp.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.appInfoMapFlow

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun FocusModePage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<FocusModeVm>()
    val allRules by vm.allRulesFlow.collectAsState()
    val activeSession by vm.activeSessionFlow.collectAsState()
    val isActive by vm.isActiveFlow.collectAsState()
    val currentWhitelist by vm.currentWhitelistFlow.collectAsState()

    var showQuickStartSheet by remember { mutableStateOf(false) }
    var showRuleEditorSheet by remember { mutableStateOf(false) }
    var showWhitelistPicker by remember { mutableStateOf(false) }
    var showLockSheet by remember { mutableStateOf(false) }
    var lockTargetRule by remember { mutableStateOf<FocusRule?>(null) }
    var whitelistPickerMode by remember { mutableStateOf("rule") } // "rule" or "manual"

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popBackStack() },
                    )
                },
                title = { Text(text = "涓撴敞妯″紡") },
                actions = {
                    IconButton(onClick = {
                        vm.resetRuleForm()
                        showRuleEditorSheet = true
                    }) {
                        Icon(PerfIcon.Add, contentDescription = "娣诲姞瑙勫垯")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            // 褰撳墠鐘舵€佸崱鐗?
            item(key = "status") {
                ActiveSessionCard(
                    session = activeSession,
                    isActive = isActive,
                    currentWhitelist = currentWhitelist,
                    onStop = { vm.stopManualSession() },
                    onRemoveWhitelist = { vm.removeFromSessionWhitelist(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 蹇€熷惎鍔ㄦ寜閽?
            if (!isActive) {
                item(key = "quick_start") {
                    Button(
                        onClick = { showQuickStartSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .itemPadding()
                    ) {
                        Text("绔嬪嵆寮€濮嬩笓娉?)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 瑙勫垯鍒楄〃鏍囬
            item(key = "rules_header") {
                Text(
                    text = "瀹氭椂瑙勫垯",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.itemPadding()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (allRules.isEmpty()) {
                item(key = "no_rules") {
                    Text(
                        text = "鏆傛棤瀹氭椂瑙勫垯锛岀偣鍑诲彸涓婅 + 娣诲姞",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                items(allRules, key = { "rule_${it.id}" }) { rule ->
                    FocusRuleCard(
                        rule = rule,
                        onToggleEnabled = { vm.toggleRuleEnabled(rule) },
                        onEdit = {
                            vm.loadRuleForEdit(rule)
                            showRuleEditorSheet = true
                        },
                        onDelete = { vm.deleteRule(rule) },
                        onLock = {
                            lockTargetRule = rule
                            showLockSheet = true
                        },
                        onStart = { vm.startQuickRule(rule) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // 蹇€熷惎鍔?Sheet
    if (showQuickStartSheet) {
        QuickStartSheet(
            vm = vm,
            onDismiss = { showQuickStartSheet = false },
            onShowWhitelistPicker = {
                whitelistPickerMode = "manual"
                showWhitelistPicker = true
            },
            onStart = {
                vm.startManualSession()
                showQuickStartSheet = false
            }
        )
    }

    // 瑙勫垯缂栬緫 Sheet
    if (showRuleEditorSheet || vm.showRuleEditor) {
        RuleEditorSheet(
            vm = vm,
            onDismiss = {
                showRuleEditorSheet = false
                vm.resetRuleForm()
            },
            onShowWhitelistPicker = {
                whitelistPickerMode = "rule"
                showWhitelistPicker = true
            },
            onSave = {
                vm.saveRule()
                showRuleEditorSheet = false
            }
        )
    }

    // 鐧藉悕鍗曢€夋嫨鍣?
    if (showWhitelistPicker) {
        WhitelistPickerDialog(
            currentWhitelist = if (whitelistPickerMode == "rule") vm.ruleWhitelistApps else vm.manualWhitelistApps,
            onDismiss = { showWhitelistPicker = false },
            onConfirm = { selected ->
                if (whitelistPickerMode == "rule") {
                    vm.ruleWhitelistApps = selected
                } else {
                    vm.manualWhitelistApps = selected
                }
                showWhitelistPicker = false
            }
        )
    }

    // 閿佸畾 Sheet
    if (showLockSheet && lockTargetRule != null) {
        LockRuleSheet(
            vm = vm,
            rule = lockTargetRule!!,
            onDismiss = {
                showLockSheet = false
                lockTargetRule = null
            },
            onLock = {
                vm.lockRule(lockTargetRule!!)
                showLockSheet = false
                lockTargetRule = null
            }
        )
    }
}

@Composable
private fun ActiveSessionCard(
    session: FocusSession?,
    isActive: Boolean,
    currentWhitelist: List<String>,
    onStop: () -> Unit,
    onRemoveWhitelist: (String) -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    PerfIcon.Mindful,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActive) "涓撴敞妯″紡杩涜涓? else "涓撴敞妯″紡鏈惎鍔?,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isActive && session?.isValidNow() == true) {
                        val remainingMinutes = session.getRemainingTime() / 60000
                        Text(
                            text = if (remainingMinutes >= 60) {
                                "鍓╀綑 ${remainingMinutes / 60} 灏忔椂 ${remainingMinutes % 60} 鍒嗛挓"
                            } else {
                                "鍓╀綑 $remainingMinutes 鍒嗛挓"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (session.isCurrentlyLocked) {
                            Text(
                                text = "锛堝凡閿佸畾锛?,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                if (isActive && session?.isManual == true && !session.isCurrentlyLocked) {
                    OutlinedButton(onClick = onStop) {
                        Text("缁撴潫")
                    }
                }
            }

            if (isActive && currentWhitelist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "鐧藉悕鍗曞簲鐢紙鐐瑰嚮绉婚櫎锛?,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                currentWhitelist.forEach { packageName ->
                    WhitelistAppRow(
                        packageName = packageName,
                        canRemove = session?.isCurrentlyLocked != true,
                        onRemove = { onRemoveWhitelist(packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WhitelistAppRow(
    packageName: String,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val appName = remember(packageName) {
        try {
            val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
            app.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        AppIcon(appId = packageName)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = appName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(PerfIcon.Close, contentDescription = "绉婚櫎")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusRuleCard(
    rule: FocusRule,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onStart: () -> Unit  // 蹇€熷惎鍔?
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .itemPadding()
            .clickable(onClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (rule.isQuickStart) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "蹇€熷惎鍔?,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (rule.isCurrentlyLocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                PerfIcon.Lock,
                                contentDescription = "宸查攣瀹?,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        if (!rule.isQuickStart && rule.isActiveNow()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "杩涜涓?,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // 鏍规嵁瑙勫垯绫诲瀷鏄剧ず涓嶅悓淇℃伅
                    if (rule.isQuickStart) {
                        Text(
                            text = "鏃堕暱锛?{rule.formatDuration()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (rule.isLocked) {
                            Text(
                                text = "鍚姩鍚庨攣瀹?,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Text(
                            text = "${rule.startTime} - ${rule.endTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = rule.formatDaysOfWeek(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (rule.isQuickStart) {
                    // 蹇€熷惎鍔細鏄剧ず寮€濮嬫寜閽?
                    Button(onClick = onStart) {
                        Text("寮€濮?)
                    }
                } else {
                    // 瀹氭椂瑙勫垯锛氭樉绀哄紑鍏?
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggleEnabled() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!rule.isQuickStart) {
                    TextButton(onClick = onLock) {
                        Icon(PerfIcon.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (rule.isCurrentlyLocked) "寤堕暱閿佸畾" else "閿佸畾")
                    }
                }
                if (!rule.isCurrentlyLocked) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Icon(PerfIcon.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("鍒犻櫎")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("鍒犻櫎瑙勫垯") },
            text = { Text("纭畾瑕佸垹闄よ鍒欍€?{rule.name}銆嶅悧锛?) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("鍒犻櫎")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("鍙栨秷")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickStartSheet(
    vm: FocusModeVm,
    onDismiss: () -> Unit,
    onShowWhitelistPicker: () -> Unit,
    onStart: () -> Unit
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
            Text(
                text = "绔嬪嵆寮€濮嬩笓娉?,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 鏃堕暱閫夋嫨
            Text(
                text = "涓撴敞鏃堕暱",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.manualHours.toString(),
                    onValueChange = {
                        val hours = it.toIntOrNull()?.coerceIn(0, 48) ?: 0
                        vm.manualHours = hours
                    },
                    label = { Text("灏忔椂") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = vm.manualMinutes.toString(),
                    onValueChange = {
                        val minutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        vm.manualMinutes = minutes
                    },
                    label = { Text("鍒嗛挓") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // 鏄剧ず楠岃瘉鎻愮ず
            if (vm.totalDurationMinutes < 5) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "鏈€鐭椂闀夸负 5 鍒嗛挓",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 鎷︽埅娑堟伅
            OutlinedTextField(
                value = vm.manualMessage,
                onValueChange = { vm.manualMessage = it },
                label = { Text("鎷︽埅鎻愮ず璇?) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 鐧藉悕鍗?
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "鐧藉悕鍗曞簲鐢?(${vm.manualWhitelistApps.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShowWhitelistPicker) {
                    Text("閫夋嫨")
                }
            }

            if (vm.manualWhitelistApps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.manualWhitelistApps.forEach { packageName ->
                        val appName = remember(packageName) {
                            try {
                                val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
                                app.packageManager.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                packageName.split(".").lastOrNull() ?: packageName
                            }
                        }
                        FilterChip(
                            selected = true,
                            onClick = { vm.removeFromManualWhitelist(packageName) },
                            label = { Text(appName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 閿佸畾閫夐」
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = vm.manualIsLocked,
                    onCheckedChange = { vm.manualIsLocked = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("閿佸畾锛堟棤娉曟彁鍓嶇粨鏉燂級")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("寮€濮嬩笓娉?)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorSheet(
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
            Text(
                text = if (vm.editingRule != null) "缂栬緫瑙勫垯" else "娣诲姞瑙勫垯",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 瑙勫垯鍚嶇О
            OutlinedTextField(
                value = vm.ruleName,
                onValueChange = { vm.ruleName = it },
                label = { Text("瑙勫垯鍚嶇О") },
                placeholder = { Text("濡傦細鏅氶棿澶嶇洏") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 瑙勫垯绫诲瀷閫夋嫨
            Text(
                text = "瑙勫垯绫诲瀷",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = vm.ruleType == FocusRule.RULE_TYPE_QUICK_START,
                    onClick = { vm.ruleType = FocusRule.RULE_TYPE_QUICK_START },
                    label = { Text("蹇€熷惎鍔?) }
                )
                FilterChip(
                    selected = vm.ruleType == FocusRule.RULE_TYPE_SCHEDULED,
                    onClick = { vm.ruleType = FocusRule.RULE_TYPE_SCHEDULED },
                    label = { Text("瀹氭椂瑙勫垯") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 鏍规嵁瑙勫垯绫诲瀷鏄剧ず涓嶅悓鐨勮緭鍏?
            if (vm.ruleType == FocusRule.RULE_TYPE_QUICK_START) {
                // 蹇€熷惎鍔細鏃堕暱杈撳叆
                Text(
                    text = "涓撴敞鏃堕暱",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = vm.ruleDurationHours.toString(),
                        onValueChange = {
                            vm.ruleDurationHours = it.toIntOrNull()?.coerceIn(0, 48) ?: 0
                        },
                        label = { Text("灏忔椂") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = vm.ruleDurationMinutes.toString(),
                        onValueChange = {
                            vm.ruleDurationMinutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        },
                        label = { Text("鍒嗛挓") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                if (vm.ruleTotalDurationMinutes < 5) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "鏈€鐭椂闀夸负 5 鍒嗛挓",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 閿佸畾閫夐」
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = vm.ruleIsLocked,
                        onCheckedChange = { vm.ruleIsLocked = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("閿佸畾锛堟棤娉曟彁鍓嶇粨鏉燂級")
                }
            } else {
                // 瀹氭椂瑙勫垯锛氭椂闂存
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = vm.ruleStartTime,
                        onValueChange = { vm.ruleStartTime = it },
                        label = { Text("寮€濮嬫椂闂?) },
                        placeholder = { Text("22:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = vm.ruleEndTime,
                        onValueChange = { vm.ruleEndTime = it },
                        label = { Text("缁撴潫鏃堕棿") },
                        placeholder = { Text("23:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 鏄熸湡閫夋嫨
                Text(
                    text = "鐢熸晥鏃ユ湡",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dayNames = listOf("涓€", "浜?, "涓?, "鍥?, "浜?, "鍏?, "鏃?)
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
                            label = { Text("鍛?{dayNames[day - 1]}") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 鎷︽埅娑堟伅
            OutlinedTextField(
                value = vm.ruleInterceptMessage,
                onValueChange = { vm.ruleInterceptMessage = it },
                label = { Text("鎷︽埅鎻愮ず璇?) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 鐧藉悕鍗?
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "鐧藉悕鍗曞簲鐢?(${vm.ruleWhitelistApps.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShowWhitelistPicker) {
                    Text("閫夋嫨")
                }
            }

            if (vm.ruleWhitelistApps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.ruleWhitelistApps.forEach { packageName ->
                        val appName = remember(packageName) {
                            try {
                                val appInfo = app.packageManager.getApplicationInfo(packageName, 0)
                                app.packageManager.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                packageName.split(".").lastOrNull() ?: packageName
                            }
                        }
                        FilterChip(
                            selected = true,
                            onClick = { vm.removeFromRuleWhitelist(packageName) },
                            label = { Text(appName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("淇濆瓨")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WhitelistPickerDialog(
    currentWhitelist: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selectedApps by remember { mutableStateOf(currentWhitelist.toSet()) }
    val appInfoMap by appInfoMapFlow.collectAsState()
    val vm = viewModel<FocusModeVm>()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("閫夋嫨鐧藉悕鍗曞簲鐢?) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 鎼滅储妗?
                OutlinedTextField(
                    value = vm.whitelistSearchQuery,
                    onValueChange = { vm.whitelistSearchQuery = it },
                    placeholder = { Text("鎼滅储搴旂敤") },
                    leadingIcon = { Icon(PerfIcon.Search, null) },
                    trailingIcon = {
                        if (vm.whitelistSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.whitelistSearchQuery = "" }) {
                                Icon(PerfIcon.Close, "娓呴櫎")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 绯荤粺搴旂敤寮€鍏?
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.showSystemAppsInWhitelist = !vm.showSystemAppsInWhitelist }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("鏄剧ず绯荤粺搴旂敤")
                    Switch(
                        checked = vm.showSystemAppsInWhitelist,
                        onCheckedChange = { vm.showSystemAppsInWhitelist = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 搴旂敤鍒楄〃
                LazyColumn {
                    items(
                        appInfoMap.values
                            .filterNot { !vm.showSystemAppsInWhitelist && it.isSystem }
                            .filter { appInfo ->
                                if (vm.whitelistSearchQuery.isBlank()) {
                                    !appInfo.hidden
                                } else {
                                    !appInfo.hidden && (
                                        appInfo.name.contains(vm.whitelistSearchQuery, ignoreCase = true) ||
                                        appInfo.id.contains(vm.whitelistSearchQuery, ignoreCase = true)
                                    )
                                }
                            }
                            .sortedBy { it.name }
                            .toList()
                    ) { appInfo ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedApps = if (selectedApps.contains(appInfo.id)) {
                                        selectedApps - appInfo.id
                                    } else {
                                        selectedApps + appInfo.id
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = selectedApps.contains(appInfo.id),
                                onCheckedChange = {
                                    selectedApps = if (it) {
                                        selectedApps + appInfo.id
                                    } else {
                                        selectedApps - appInfo.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppIcon(appId = appInfo.id)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = appInfo.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = appInfo.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedApps.toList()) }) {
                Text("纭畾")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("鍙栨秷")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockRuleSheet(
    vm: FocusModeVm,
    rule: FocusRule,
    onDismiss: () -> Unit,
    onLock: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (rule.isCurrentlyLocked) "寤堕暱閿佸畾" else "閿佸畾瑙勫垯",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "閿佸畾鍚庢棤娉曞叧闂垨鍒犻櫎姝よ鍒?,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (rule.isCurrentlyLocked) {
                val remainingMinutes = ((rule.lockEndTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                Text(
                    text = "褰撳墠鍓╀綑: ${if (remainingMinutes >= 60) "${remainingMinutes / 60}灏忔椂${remainingMinutes % 60}鍒嗛挓" else "${remainingMinutes}鍒嗛挓"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 棰勮鏃堕暱
            val presets = listOf(
                480 to "8灏忔椂",
                1440 to "1澶?,
                4320 to "3澶?
            )
            presets.forEach { (minutes, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.selectedLockDuration = minutes
                            vm.isCustomLockDuration = false
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Checkbox(
                        checked = !vm.isCustomLockDuration && vm.selectedLockDuration == minutes,
                        onCheckedChange = {
                            vm.selectedLockDuration = minutes
                            vm.isCustomLockDuration = false
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label)
                }
            }

            // 鑷畾涔夋椂闀?
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.isCustomLockDuration = true }
                    .padding(vertical = 12.dp)
            ) {
                Checkbox(
                    checked = vm.isCustomLockDuration,
                    onCheckedChange = { vm.isCustomLockDuration = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("鑷畾涔?)
            }

            if (vm.isCustomLockDuration) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = vm.customLockDaysText,
                        onValueChange = { vm.customLockDaysText = it },
                        label = { Text("澶?) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = vm.customLockHoursText,
                        onValueChange = { vm.customLockHoursText = it },
                        label = { Text("灏忔椂") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onLock,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (rule.isCurrentlyLocked) "寤堕暱閿佸畾" else "纭閿佸畾")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
