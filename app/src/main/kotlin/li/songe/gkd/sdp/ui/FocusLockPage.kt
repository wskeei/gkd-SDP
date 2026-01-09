package li.songe.gkd.sdp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.data.ResolvedAppGroup
import li.songe.gkd.sdp.ui.AppState
import li.songe.gkd.sdp.ui.RuleState
import li.songe.gkd.sdp.ui.SubscriptionState
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.throttle

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun FocusLockPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<FocusLockVm>()
    val subStates by vm.subStatesFlow.collectAsState()
    val expandedSubs by vm.expandedSubs.collectAsState()
    val expandedApps by vm.expandedApps.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentLockTarget by remember { mutableStateOf<LockTarget?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = {
                            mainVm.popBackStack()
                        },
                    )
                },
                title = { Text(text = "数字自律") }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
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
                    SubscriptionItem(
                        state = subState,
                        isExpanded = expandedSubs.contains(subState.subsId),
                        onExpandClick = { vm.toggleExpandSubs(subState.subsId) },
                        onLockClick = {
                            currentLockTarget = LockTarget(ConstraintConfig.TYPE_SUBSCRIPTION, subState.subsId, null, null, subState.subsName)
                            showBottomSheet = true
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                if (expandedSubs.contains(subState.subsId)) {
                    // Global Rules
                    subState.globalRules.forEach { ruleState ->
                        item(key = "rule_${subState.subsId}_${ruleState.group.group.key}") {
                            RuleItem(
                                state = ruleState,
                                paddingStart = 32.dp,
                                onLockClick = {
                                    currentLockTarget = LockTarget(ConstraintConfig.TYPE_RULE_GROUP, subState.subsId, null, ruleState.group.group.key, ruleState.group.group.name)
                                    showBottomSheet = true
                                },
                                onInterceptToggle = { vm.toggleIntercept(ruleState.group) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 32.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }

                    // Apps
                    subState.apps.forEach { appState ->
                        val appKey = "${subState.subsId}_${appState.appId}"
                        item(key = "app_$appKey") {
                            AppItem(
                                state = appState,
                                isExpanded = expandedApps.contains(appKey),
                                onExpandClick = { vm.toggleExpandApp(appKey) },
                                onLockClick = {
                                    currentLockTarget = LockTarget(ConstraintConfig.TYPE_APP, subState.subsId, appState.appId, null, appState.appName)
                                    showBottomSheet = true
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }

                        if (expandedApps.contains(appKey)) {
                            appState.rules.forEach { ruleState ->
                                item(key = "rule_${subState.subsId}_${appState.appId}_${ruleState.group.group.key}") {
                                    RuleItem(
                                        state = ruleState,
                                        paddingStart = 48.dp,
                                        onLockClick = {
                                            currentLockTarget = LockTarget(ConstraintConfig.TYPE_RULE_GROUP, subState.subsId, appState.appId, ruleState.group.group.key, ruleState.group.group.name)
                                            showBottomSheet = true
                                        },
                                        onInterceptToggle = { vm.toggleIntercept(ruleState.group) }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(start = 48.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showBottomSheet && currentLockTarget != null) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                LockDurationSheet(
                    targetName = currentLockTarget!!.name,
                    vm = vm,
                    onConfirm = {
                        vm.lockTarget(
                            currentLockTarget!!.type,
                            currentLockTarget!!.subsId,
                            currentLockTarget!!.appId,
                            currentLockTarget!!.groupKey
                        )
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    }
                )
            }
        }
    }
}

data class LockTarget(
    val type: Int,
    val subsId: Long,
    val appId: String?,
    val groupKey: Int?,
    val name: String
)

@Composable
fun SubscriptionItem(
    state: SubscriptionState,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onLockClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PerfIcon(
            imageVector = if (isExpanded) PerfIcon.ArrowDownward else PerfIcon.KeyboardArrowRight,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.subsName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.isLocked) {
                Text(
                    text = "剩余: ${formatRemainingTime(state.lockEndTime - System.currentTimeMillis())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onLockClick) {
            PerfIcon(
                imageVector = if (state.isLocked) PerfIcon.Lock else PerfIcon.History, // Use History as "Clock/Time" icon for setting lock
                tint = if (state.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AppItem(
    state: AppState,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onLockClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandClick() }
            .padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PerfIcon(
            imageVector = if (isExpanded) PerfIcon.ArrowDownward else PerfIcon.KeyboardArrowRight,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.isLocked) {
                Text(
                    text = "剩余: ${formatRemainingTime(state.lockEndTime - System.currentTimeMillis())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onLockClick, modifier = Modifier.size(32.dp)) {
            PerfIcon(
                imageVector = if (state.isLocked) PerfIcon.Lock else PerfIcon.History,
                tint = if (state.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun RuleItem(
    state: RuleState,
    paddingStart: androidx.compose.ui.unit.Dp,
    onLockClick: () -> Unit,
    onInterceptToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.group.group.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.isLocked) {
                val lockSource = when (state.lockedBy) {
                    2 -> " (应用)"
                    3 -> " (订阅)"
                    else -> ""
                }
                Text(
                    text = "剩余: ${formatRemainingTime(state.lockEndTime - System.currentTimeMillis())}$lockSource",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Switch(
            checked = state.isInterceptEnabled,
            onCheckedChange = { onInterceptToggle() },
            modifier = Modifier.height(24.dp).padding(end = 16.dp)
        )

        IconButton(onClick = onLockClick, modifier = Modifier.size(32.dp)) {
            PerfIcon(
                imageVector = if (state.isLocked) PerfIcon.Lock else PerfIcon.History,
                tint = if (state.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun LockDurationSheet(
    targetName: String,
    vm: FocusLockVm,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "锁定: $targetName",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
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
                        modifier = Modifier.weight(1f)
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
                TextButton(
                    onClick = { vm.isCustomDuration = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "自定义",
                        color = if (vm.isCustomDuration)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (vm.isCustomDuration) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确定")
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