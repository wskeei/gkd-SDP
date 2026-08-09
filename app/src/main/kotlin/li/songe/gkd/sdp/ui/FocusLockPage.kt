package li.songe.gkd.sdp.ui

import androidx.activity.compose.LocalActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.a11y.AppBlockerEngine
import li.songe.gkd.sdp.a11y.FocusModeEngine
import li.songe.gkd.sdp.a11y.sdpRuntimeFeatureCoordinator
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.data.InterceptConfig
import li.songe.gkd.sdp.service.AccessibilityGuardController
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.AutoReenablePolicy
import li.songe.gkd.sdp.util.format
import li.songe.gkd.sdp.util.toast
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Serializable
data object FocusLockRoute : NavKey

@Composable
fun FocusLockPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<FocusLockVm>()
    val subStates by vm.subStatesFlow.collectAsStateWithLifecycle()
    val expandedSubs by vm.expandedSubs.collectAsStateWithLifecycle()
    val expandedApps by vm.expandedApps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val settings by storeFlow.collectAsStateWithLifecycle()

    val lockSheetState = rememberModalBottomSheetState()
    val pauseSheetState = rememberModalBottomSheetState()
    
    var showLockSheet by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAccessibilityGuardDialog by remember { mutableStateOf(false) }
    var showAccessibilityGuardDisableDialog by remember { mutableStateOf(false) }
    var showAutoReenableDialog by remember { mutableStateOf(false) }
    
    var currentLockTarget by remember { mutableStateOf<LockTarget?>(null) }
    var currentPauseTarget by remember { mutableStateOf<PauseTarget?>(null) }
    
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popPage() },
                    )
                },
                title = { Text(text = "数字自律") }
            )
        }
    ) { padding ->
        val urlBlockerEnabled by UrlBlockerEngine.enabledFlow.collectAsStateWithLifecycle()
        val focusModeActive by FocusModeEngine.isActiveFlow.collectAsStateWithLifecycle()
        val appBlockerRules by AppBlockerEngine.enabledRulesFlow.collectAsStateWithLifecycle()
        val appBlockerGroups by AppBlockerEngine.enabledGroupsFlow.collectAsStateWithLifecycle()

        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            item(key = "self_control_runtime_status") {
                SelfControlRuntimeStatusCard()
                Spacer(modifier = Modifier.height(12.dp))
            }
            // 专注模式卡片
            item(key = "focus_mode") {
                FocusModeCard(
                    isActive = focusModeActive,
                    onClick = { mainVm.navigatePage(FocusModeRoute) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // URL 拦截卡片 - 作为内置订阅
            item(key = "url_blocker") {
                UrlBlockerCard(
                    enabled = urlBlockerEnabled,
                    onClick = { mainVm.navigatePage(UrlBlockRoute) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 应用拦截卡片
            item(key = "app_blocker") {
                AppBlockerCard(
                    enabledRuleCount = appBlockerRules.size,
                    enabledGroupCount = appBlockerGroups.size,
                    onClick = { mainVm.navigatePage(AppBlockerRoute) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item(key = "usage_guard") {
                UsageGuardCard(
                    enabled = settings.usageGuardEnabled,
                    scopeMode = settings.usageGuardScopeMode,
                    onClick = { mainVm.navigatePage(UsageGuardRoute) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (META.isGkdChannel) {
                item(key = "accessibility_guard") {
                    AccessibilityGuardCard(
                        enabled = settings.accessibilityGuardEnabled,
                        armed = settings.accessibilityGuardAutoReenableArmed,
                        onCheckedChange = { requestedEnabled ->
                            if (requestedEnabled) {
                                showAccessibilityGuardDialog = true
                            } else {
                                showAccessibilityGuardDisableDialog = true
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 软件安装监测卡片
            item(key = "app_install_monitor") {
                AppInstallMonitorCard(
                    onClick = { mainVm.navigatePage(AppInstallMonitorRoute) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 防卸载保护卡片
            item(key = "auto_reenable_guard") {
                AutoReenableGuardCard(
                    intervalMinutes = settings.autoReenableIntervalMinutes,
                    changedAt = settings.autoReenableIntervalChangedAt,
                    nextEnforceAt = settings.autoReenableNextEnforceAt,
                    dailyDisableLimit = settings.autoReenableDailyDisableLimit,
                    dailyDisableUsed = settings.autoReenableDailyDisableUsed,
                    dailyDisableDayStartAt = settings.autoReenableDailyDisableDayStartAt,
                    onClick = { showAutoReenableDialog = true }
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

        if (showAccessibilityGuardDialog) {
            AlertDialog(
                onDismissRequest = { showAccessibilityGuardDialog = false },
                title = { Text("开启无障碍权限守护") },
                text = {
                    Text(
                        "即使当前无障碍已经关闭，也可以先开启守护。检测到关闭后会立即显示倒计时，" +
                            "并在 15、25、30、33、35、36 分钟分别提醒一次（间隔为 15/10/5/3/2/1 分钟）。" +
                            "第 36 分钟最后一次提醒后仍未恢复，会显示全屏悬浮窗。" +
                            "关闭守护会受数字自律锁定、每日关闭限额和自动重开保护约束。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAccessibilityGuardDialog = false
                            scope.launch {
                                when (AccessibilityGuardController.enable(activity)) {
                                    AccessibilityGuardController.EnableResult.RequiresA11yMode -> {
                                        toast("请先切换到无障碍模式")
                                        mainVm.navigatePage(AuthA11yRoute)
                                    }

                                    AccessibilityGuardController.EnableResult.UnavailableChannel,
                                    AccessibilityGuardController.EnableResult.Enabled,
                                    AccessibilityGuardController.EnableResult.AlreadyEnabled,
                                    AccessibilityGuardController.EnableResult.Superseded -> Unit
                                }
                            }
                        },
                    ) {
                        Text("同意并开启")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAccessibilityGuardDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }

        if (showAccessibilityGuardDisableDialog) {
            AlertDialog(
                onDismissRequest = { showAccessibilityGuardDisableDialog = false },
                title = { Text("关闭无障碍权限守护") },
                text = {
                    Text(
                        "关闭后将停止无障碍权限提醒、倒计时和全屏提示。" +
                            "如果已加入自动重开保护，守护会在下一次检查时恢复。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAccessibilityGuardDisableDialog = false
                            scope.launch {
                                when (val result = AccessibilityGuardController.disable()) {
                                    AccessibilityGuardController.DisableResult.BlockedByLock ->
                                        toast("数字自律锁定生效中，无法关闭无障碍权限守护")

                                    is AccessibilityGuardController.DisableResult.BlockedByQuota ->
                                        toast("今日关闭次数已用完（${result.limit} 次），将于明日 00:00 重置")

                                    AccessibilityGuardController.DisableResult.Disabled,
                                    AccessibilityGuardController.DisableResult.NoChange -> Unit
                                }
                            }
                        },
                    ) {
                        Text("关闭守护")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAccessibilityGuardDisableDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }

        if (showAutoReenableDialog) {
            var inputText by remember { mutableStateOf(settings.autoReenableIntervalMinutes.toString()) }
            var dailyLimitText by remember { mutableStateOf(settings.autoReenableDailyDisableLimit.toString()) }
            val autoReenableUiState = FocusLockVm.evaluateAutoReenableUiState(
                intervalMinutes = settings.autoReenableIntervalMinutes,
                lastChangedAt = settings.autoReenableIntervalChangedAt,
                scheduledNextEnforceAt = settings.autoReenableNextEnforceAt,
                dailyDisableLimit = settings.autoReenableDailyDisableLimit,
                dailyDisableUsed = settings.autoReenableDailyDisableUsed,
                dailyDisableDayStartAt = settings.autoReenableDailyDisableDayStartAt,
                now = System.currentTimeMillis()
            )
            val nextEditableText = if (autoReenableUiState.canEditInterval) {
                "可立即修改"
            } else {
                autoReenableUiState.nextEditableAt.format("MM-dd HH:mm")
            }
            val nextEnforceText = autoReenableUiState.nextEnforceAt.format("MM-dd HH:mm")
            val nextDailyResetText = autoReenableUiState.nextDailyResetAt.format("MM-dd HH:mm")
            val parsed = inputText.toIntOrNull()
            val intervalInputValid = parsed != null && parsed in 0..AutoReenablePolicy.MAX_INTERVAL_MINUTES
            val parsedDailyLimit = dailyLimitText.toIntOrNull()
            val dailyLimitInputValid =
                parsedDailyLimit != null && parsedDailyLimit in AutoReenablePolicy.MIN_DAILY_DISABLE_LIMIT..AutoReenablePolicy.MAX_DAILY_DISABLE_LIMIT
            val intervalChanged = parsed != null && parsed != settings.autoReenableIntervalMinutes
            val dailyLimitChanged = parsedDailyLimit != null &&
                AutoReenablePolicy.normalizeDailyDisableLimit(parsedDailyLimit) != autoReenableUiState.dailyDisableLimit
            val canSaveInterval = intervalInputValid && (!intervalChanged || autoReenableUiState.canEditInterval)
            val canSave = canSaveInterval && dailyLimitInputValid && (intervalChanged || dailyLimitChanged)

            AlertDialog(
                onDismissRequest = { showAutoReenableDialog = false },
                title = { Text("自动重开间隔") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("自动重开始终启用，无法关闭。会恢复已关闭的规则、使用申请开关与已加入保护的无障碍权限守护。")
                        Text("下一次自动重开：$nextEnforceText")
                        Text("今日已用/总额：${autoReenableUiState.dailyDisableUsed}/${autoReenableUiState.dailyDisableLimit}")
                        Text("剩余次数：${autoReenableUiState.dailyDisableRemaining}")
                        Text("下一次重置时间：$nextDailyResetText")
                        if (!autoReenableUiState.canEditInterval) {
                            Text("冷却中，下次可修改：$nextEditableText")
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() }) {
                                    inputText = value
                                }
                            },
                            label = { Text("间隔（分钟）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = autoReenableUiState.canEditInterval,
                            isError = !intervalInputValid
                        )
                        if (!intervalInputValid) {
                            Text(
                                text = "请输入 0~240 的整数分钟",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        OutlinedTextField(
                            value = dailyLimitText,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() }) {
                                    dailyLimitText = value
                                }
                            },
                            label = { Text("每日关闭限额（次）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = !dailyLimitInputValid
                        )
                        if (!dailyLimitInputValid) {
                            Text(
                                text = "请输入 1~5 的整数次数",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            if (intervalChanged) {
                                vm.updateAutoReenableInterval(parsed ?: settings.autoReenableIntervalMinutes)
                            }
                            if (dailyLimitChanged) {
                                vm.updateAutoReenableDailyDisableLimit(
                                    parsedDailyLimit ?: autoReenableUiState.dailyDisableLimit
                                )
                            }
                            showAutoReenableDialog = false
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAutoReenableDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
fun SelfControlRuntimeStatusCard() {
    val context = LocalContext.current
    val runtime by sdpRuntimeFeatureCoordinator.statusFlow.collectAsStateWithLifecycle()
    val overlayPermission by canDrawOverlaysState.stateFlow.collectAsStateWithLifecycle()
    val readiness = li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.evaluate(
        mode = runtime.mode,
        connected = runtime.connected,
        switching = runtime.switching,
        overlayPermission = overlayPermission,
    )
    val issueText = when (readiness.issue) {
        li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.Issue.None -> "可拦截"
        li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.Issue.Switching -> "运行模式切换中"
        li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.Issue.RuntimeUnavailable -> "运行引擎未连接"
        li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.Issue.OverlayPermissionMissing -> "悬浮窗权限缺失"
    }
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "自律拦截运行状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "模式：${readiness.modeLabel} · 引擎：${when {
                    runtime.switching -> "切换中"
                    runtime.connected -> "已连接"
                    else -> "未连接"
                }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "悬浮窗：${if (overlayPermission) "已授权" else "未授权"} · $issueText",
                style = MaterialTheme.typography.bodySmall,
                color = if (readiness.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            runtime.lastDecision?.let { decision ->
                Text(
                    text = "最近判定：${decision.feature} · ${decision.decision} · " +
                        li.songe.gkd.sdp.util.SelfControlElapsedPolicy.formatAbsolute(decision.atEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!overlayPermission) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                ) {
                    Text("前往授权")
                }
            }
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
@android.annotation.SuppressLint("NonObservableLocale")
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
fun AccessibilityGuardCard(
    enabled: Boolean,
    armed: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PerfIcon(
                imageVector = PerfIcon.VerifiedUser,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "无障碍权限守护",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        enabled -> "关闭后立即倒计时，并按 15/10/5/3/2/1 分钟分阶段提醒"
                        armed -> "已暂时关闭，将由自动重开保护恢复"
                        else -> "检测到无障碍关闭后立即提醒，最后显示全屏提示"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
fun AutoReenableGuardCard(
    intervalMinutes: Int,
    changedAt: Long,
    nextEnforceAt: Long,
    dailyDisableLimit: Int,
    dailyDisableUsed: Int,
    dailyDisableDayStartAt: Long,
    onClick: () -> Unit
) {
    val autoReenableUiState = FocusLockVm.evaluateAutoReenableUiState(
        intervalMinutes = intervalMinutes,
        lastChangedAt = changedAt,
        scheduledNextEnforceAt = nextEnforceAt,
        dailyDisableLimit = dailyDisableLimit,
        dailyDisableUsed = dailyDisableUsed,
        dailyDisableDayStartAt = dailyDisableDayStartAt,
        now = System.currentTimeMillis()
    )
    val nextEditableText = if (autoReenableUiState.canEditInterval) "可立即修改" else autoReenableUiState.nextEditableAt.format("MM-dd HH:mm")
    val nextEnforceText = autoReenableUiState.nextEnforceAt.format("MM-dd HH:mm")
    val nextDailyResetText = autoReenableUiState.nextDailyResetAt.format("MM-dd HH:mm")

    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "自动重开保护",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "自动重开始终启用，无法关闭；会恢复规则、使用申请开关与无障碍权限守护",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "当前间隔：$intervalMinutes 分钟（0 为高频巡检）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "下次可修改：$nextEditableText",
                style = MaterialTheme.typography.bodySmall,
                color = if (!autoReenableUiState.canEditInterval) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "下一次自动重开：$nextEnforceText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "今日关闭次数：${autoReenableUiState.dailyDisableUsed}/${autoReenableUiState.dailyDisableLimit}（剩余 ${autoReenableUiState.dailyDisableRemaining}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "下次配额重置：$nextDailyResetText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    enabledRuleCount: Int,
    enabledGroupCount: Int,
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
                    text = if (enabledRuleCount == 0) {
                        "尚未生效：请添加时间规则"
                    } else {
                        "${enabledGroupCount} 个应用组 · $enabledRuleCount 条启用规则"
                    },
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
fun UsageGuardCard(
    enabled: Boolean,
    scopeMode: Int,
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
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "使用申请",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) {
                        val scopeText = if (scopeMode == 0) "仅选中应用" else "全局生效"
                        "打开受控应用前先说明原因并申请时长 · $scopeText"
                    } else {
                        "未启用"
                    },
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
