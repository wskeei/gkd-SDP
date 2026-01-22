package li.songe.gkd.sdp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppBlockerPageDestination
import com.ramcosta.composedestinations.generated.destinations.FocusModePageDestination
import com.ramcosta.composedestinations.generated.destinations.UrlBlockPageDestination
import com.ramcosta.composedestinations.generated.destinations.AppInstallMonitorPageDestination
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.FocusModeEngine
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.data.InterceptConfig
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun FocusLockPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<FocusLockVm>()
    val subStates by vm.subStatesFlow.collectAsState()
    val expandedSubs by vm.expandedSubs.collectAsState()
    val expandedApps by vm.expandedApps.collectAsState()
    val context = LocalContext.current

    val lockSheetState = rememberModalBottomSheetState()
    val pauseSheetState = rememberModalBottomSheetState()
    
    var showLockSheet by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    var currentLockTarget by remember { mutableStateOf<LockTarget?>(null) }
    var currentPauseTarget by remember { mutableStateOf<PauseTarget?>(null) }
    
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
                title = { Text(text = "数字自律") }
            )
        }
    ) { padding ->
        val urlBlockerEnabled by UrlBlockerEngine.enabledFlow.collectAsState()
        val focusModeActive by FocusModeEngine.isActiveFlow.collectAsState()

        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            // 专注模式卡片
            item(key = "focus_mode") {
                FocusModeCard(
                    isActive = focusModeActive,
                    onClick = { mainVm.navigatePage(FocusModePageDestination) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // URL 拦截卡片 - 作为内置订阅
            item(key = "url_blocker") {
                UrlBlockerCard(
                    enabled = urlBlockerEnabled,
                    onClick = { mainVm.navigatePage(UrlBlockPageDestination) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 应用拦截卡片
            item(key = "app_blocker") {
                AppBlockerCard(
                    onClick = { mainVm.navigatePage(AppBlockerPageDestination) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 软件安装监测卡片
            item(key = "app_install_monitor") {
                AppInstallMonitorCard(
                    onClick = { mainVm.navigatePage(AppInstallMonitorPageDestination) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (subStates.isEmpty()) {
                item {
                    Text(
                        text = "当前没有已启用的规则组，请先前往订阅页面启用规则。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.itemPadding()
                    )
                }
            }

            subStates.forEach { subState ->
                item(key = "sub_${subState.subsId}") {
                    SubscriptionCard(
                        subState = subState,
                        isExpanded = expandedSubs.contains(subState.subsId),
                        expandedApps = expandedApps,
                        onExpandSubs = { vm.toggleExpandSubs(subState.subsId) },
                        onExpandApp = { appId -> vm.toggleExpandApp("${subState.subsId}_$appId") },
                        onLockClick = { target ->
                            currentLockTarget = target
                            showLockSheet = true
                        },
                        onPauseClick = { target ->
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                showPermissionDialog = true
                            } else {
                                currentPauseTarget = target
                                showPauseSheet = true
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // Lock Duration Sheet
        if (showLockSheet && currentLockTarget != null) {
            ModalBottomSheet(
                onDismissRequest = { showLockSheet = false },
                sheetState = lockSheetState
            ) {
                LockDurationSheet(
                    targetName = currentLockTarget!!.name,
                    currentEndTime = currentLockTarget!!.currentEndTime,
                    vm = vm,
                    onConfirm = {
                        vm.lockTarget(
                            currentLockTarget!!.type,
                            currentLockTarget!!.subsId,
                            currentLockTarget!!.appId,
                            currentLockTarget!!.groupKey
                        )
                        scope.launch { lockSheetState.hide() }.invokeOnCompletion {
                            if (!lockSheetState.isVisible) showLockSheet = false
                        }
                    }
                )
            }
        }

        // Mindful Pause Config Sheet
        if (showPauseSheet && currentPauseTarget != null) {
            ModalBottomSheet(
                onDismissRequest = { showPauseSheet = false },
                sheetState = pauseSheetState
            ) {
                MindfulPauseSheet(
                    target = currentPauseTarget!!,
                    onConfirm = { enabled, cooldown, msg ->
                        if (currentPauseTarget!!.groupKey != null) {
                            vm.updateInterceptConfig(
                                currentPauseTarget!!.subsId,
                                currentPauseTarget!!.appId,
                                currentPauseTarget!!.groupKey!!,
                                enabled,
                                cooldown,
                                msg
                            )
                        } else {
                            vm.batchUpdateInterceptConfig(
                                currentPauseTarget!!.subsId,
                                currentPauseTarget!!.appId,
                                enabled,
                                cooldown,
                                msg
                            )
                        }
                        scope.launch { pauseSheetState.hide() }.invokeOnCompletion {
                            if (!pauseSheetState.isVisible) showPauseSheet = false
                        }
                    }
                )
            }
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("需要悬浮窗权限") },
                text = { Text("全屏拦截功能需要悬浮窗权限才能正常显示。请前往设置开启。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionDialog = false
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text("去设置")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

// --- Data Models for Sheet Targets ---

data class LockTarget(
    val type: Int,
    val subsId: Long,
    val appId: String?,
    val groupKey: Int?,
    val name: String,
    val currentEndTime: Long = 0
)

data class PauseTarget(
    val subsId: Long,
    val appId: String?,
    val groupKey: Int?,
    val groupName: String,
    val config: InterceptConfig?,
    val isLocked: Boolean = false,
    val initialEnabled: Boolean = false
)

// --- Composable Components ---

@Composable
fun SubscriptionCard(
    subState: SubscriptionState,
    isExpanded: Boolean,
    expandedApps: Set<String>,
    onExpandSubs: () -> Unit,
    onExpandApp: (String) -> Unit,
    onLockClick: (LockTarget) -> Unit,
    onPauseClick: (PauseTarget) -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Subscription Header
        Row(
            modifier = Modifier
                .clickable { onExpandSubs() }
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = if (isExpanded) PerfIcon.ArrowDownward else PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subState.subsName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subState.isLocked) {
                    Text(
                        text = "已锁定 • 剩余 ${formatRemainingTime(subState.lockEndTime - System.currentTimeMillis())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Batch Pause Button (Subs)
            IconButton(
                onClick = {
                    onPauseClick(PauseTarget(subState.subsId, null, null, subState.subsName, null, isLocked = subState.isLocked, initialEnabled = subState.allInterceptEnabled))
                }
            ) {
                PerfIcon(
                    imageVector = PerfIcon.Mindful,
                    tint = if (subState.allInterceptEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Lock Button for Subscription
            IconButton(
                onClick = { 
                    onLockClick(LockTarget(ConstraintConfig.TYPE_SUBSCRIPTION, subState.subsId, null, null, subState.subsName, currentEndTime = subState.lockEndTime)) 
                }
            ) {
                PerfIcon(
                    imageVector = if (subState.isLocked) PerfIcon.Lock else PerfIcon.History,
                    tint = if (subState.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Expanded Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                
                // Global Rules
                if (subState.globalRules.isNotEmpty()) {
                    Text(
                        text = "全局规则",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 56.dp, top = 8.dp, bottom = 4.dp)
                    )
                    subState.globalRules.forEach { rule ->
                        RuleItem(
                            state = rule,
                            paddingStart = 40.dp, // Indented
                            onLockClick = { onLockClick(LockTarget(ConstraintConfig.TYPE_RULE_GROUP, subState.subsId, null, rule.group.group.key, rule.group.group.name, currentEndTime = rule.lockEndTime)) },
                            onPauseClick = { onPauseClick(PauseTarget(subState.subsId, "", rule.group.group.key, rule.group.group.name, rule.interceptConfig, isLocked = rule.isLocked)) }
                        )
                    }
                }

                // App Rules
                subState.apps.forEach { appState ->
                    val isAppExpanded = expandedApps.contains("${subState.subsId}_${appState.appId}")
                    
                    // App Header
                    Row(
                        modifier = Modifier
                            .clickable { onExpandApp(appState.appId) }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(24.dp)) // Indent
                        PerfIcon(
                            imageVector = if (isAppExpanded) PerfIcon.ArrowDownward else PerfIcon.KeyboardArrowRight,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appState.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (appState.isLocked) {
                                Text(
                                    text = "剩余 ${formatRemainingTime(appState.lockEndTime - System.currentTimeMillis())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // Batch Pause Button (App)
                        IconButton(
                            onClick = {
                                onPauseClick(PauseTarget(subState.subsId, appState.appId, null, appState.appName, null, isLocked = appState.isLocked, initialEnabled = appState.allInterceptEnabled))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            PerfIcon(
                                imageVector = PerfIcon.Mindful,
                                tint = if (appState.allInterceptEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { 
                                onLockClick(LockTarget(ConstraintConfig.TYPE_APP, subState.subsId, appState.appId, null, appState.appName, currentEndTime = appState.lockEndTime))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            PerfIcon(
                                imageVector = if (appState.isLocked) PerfIcon.Lock else PerfIcon.History,
                                tint = if (appState.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // App Rules List
                    AnimatedVisibility(
                        visible = isAppExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            appState.rules.forEach { rule ->
                                RuleItem(
                                    state = rule,
                                    paddingStart = 64.dp, // More indented
                                    onLockClick = { onLockClick(LockTarget(ConstraintConfig.TYPE_RULE_GROUP, subState.subsId, appState.appId, rule.group.group.key, rule.group.group.name, currentEndTime = rule.lockEndTime)) },
                                    onPauseClick = { onPauseClick(PauseTarget(subState.subsId, appState.appId, rule.group.group.key, rule.group.group.name, rule.interceptConfig, isLocked = rule.isLocked)) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun RuleItem(
    state: RuleState,
    paddingStart: androidx.compose.ui.unit.Dp,
    onLockClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.group.group.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val statusText = buildString {
                if (state.isLocked) {
                    val lockSource = when (state.lockedBy) {
                        2 -> "(应用)"
                        3 -> "(订阅)"
                        else -> ""
                    }
                    append("锁定中$lockSource ")
                }
                if (state.interceptConfig?.enabled == true) {
                    append("全屏拦截")
                }
            }
            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        // Action Buttons Row
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            // Mindful Pause Button (Eco Icon)
            IconButton(
                onClick = onPauseClick,
                modifier = Modifier.size(36.dp)
            ) {
                PerfIcon(
                    imageVector = PerfIcon.Mindful,
                    tint = if (state.interceptConfig?.enabled == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Lock Button
            IconButton(
                onClick = onLockClick,
                modifier = Modifier.size(36.dp)
            ) {
                PerfIcon(
                    imageVector = if (state.isLocked) PerfIcon.Lock else PerfIcon.History,
                    tint = if (state.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MindfulPauseSheet(
    target: PauseTarget,
    onConfirm: (Boolean, Int, String) -> Unit
) {
    var enabled by remember { mutableStateOf(target.config?.enabled ?: target.initialEnabled) }
    // Cooldown is hardcoded to 10s by request
    val cooldown = 10 
    var message by remember { mutableStateOf(target.config?.message ?: "这真的重要吗？") }
    
    val isBatch = target.groupKey == null
    
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = if (isBatch) "批量配置全屏拦截" else "配置全屏拦截",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = target.groupName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("启用拦截", style = MaterialTheme.typography.titleMedium)
            
            val switchInteractionEnabled = !target.isLocked || !enabled
            
            Switch(
                checked = enabled, 
                onCheckedChange = { enabled = it },
                enabled = switchInteractionEnabled
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Message
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("沉思语录") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "说明: 触发拦截后将显示全屏提示，10秒后自动退出。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onConfirm(enabled, cooldown, message) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存配置")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LockDurationSheet(
    targetName: String,
    currentEndTime: Long,
    vm: FocusLockVm,
    onConfirm: () -> Unit
) {
    val isLocked = currentEndTime > System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = if (isLocked) "延长锁定: $targetName" else "锁定: $targetName",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        if (isLocked) {
            val date = java.util.Date(currentEndTime)
            val formatter = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            Text(
                text = "当前锁定至: ${formatter.format(date)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Text(
            text = if (isLocked) "选择要延长的时长。锁定期间规则将无法关闭。" else "锁定期间规则将无法关闭。请谨慎操作。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    480 to "8小时",
                    1440 to "1天",
                    4320 to "3天"
                )
                options.forEach { (duration, label) ->
                    TextButton(
                        onClick = {
                            vm.selectedDuration = duration
                            vm.isCustomDuration = false
                        },
                        modifier = Modifier.weight(1f),
                        border = if (!vm.isCustomDuration && vm.selectedDuration == duration) 
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) 
                            else null
                    ) {
                        Text(
                            text = label,
                            color = if (!vm.isCustomDuration && vm.selectedDuration == duration)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { vm.isCustomDuration = true },
                    modifier = Modifier.width(100.dp),
                     border = if (vm.isCustomDuration) 
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) 
                            else null
                ) {
                    Text(
                        text = "自定义",
                        color = if (vm.isCustomDuration)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (vm.isCustomDuration) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = vm.customDaysText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    vm.customDaysText = newValue
                                }
                            },
                            label = { Text("天") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = vm.customHoursText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    vm.customHoursText = newValue
                                }
                            },
                            label = { Text("小时") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLocked) "确定延长" else "确定锁定")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatRemainingTime(millis: Long): String {
    if (millis <= 0) return "已结束"
    val minutes = millis / 60000
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    val days = hours / 24
    val remainingHours = hours % 24

    return if (days > 0) {
        "${days}天${remainingHours}小时"
    } else if (hours > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${minutes}分钟"
    }
}

@Composable
fun UrlBlockerCard(
    enabled: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Block,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "网址拦截",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) "已启用" else "未启用",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FocusModeCard(
    isActive: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Mindful,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "专注模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isActive) "进行中" else "未启动",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AppBlockerCard(
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Block,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "应用拦截",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "拦截指定应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AppInstallMonitorCard(
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Download,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "软件安装监测",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "记录分心软件安装历史",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
