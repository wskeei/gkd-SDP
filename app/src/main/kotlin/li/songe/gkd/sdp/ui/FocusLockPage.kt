package li.songe.gkd.sdp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.sdp.data.ResolvedAppGroup
import li.songe.gkd.sdp.ui.component.PerfCheckbox
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.titleItemPadding
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.throttle

@Destination<RootGraph>
@Composable
fun FocusLockPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<FocusLockVm>()
    val activeLock by FocusLockUtils.activeLockFlow.collectAsState()
    val groupStates by vm.groupStatesFlow.collectAsState()

    val selectedCount = groupStates.count { it.isSelectedForLock }
    val allCount = groupStates.size
    val isActive = activeLock != null && activeLock!!.isActive

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
            if (isActive) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "当前锁定状态",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "剩余时间: ${formatRemainingTime(activeLock!!.remainingTime)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            item {
                Text(if (isActive) "延长锁定" else "锁定时长", modifier = Modifier.titleItemPadding())
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding()
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
                                    text = if (isActive) "+$label" else label,
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
            }

            item {
                val buttonText = if (isActive) {
                    if (selectedCount > 0) "更新锁定 (添加 ${selectedCount} 个规则)" else "更新锁定"
                } else {
                    if (selectedCount == 0) "请选择规则" else "开始锁定 (${selectedCount})"
                }
                
                val isDurationSet = if (vm.isCustomDuration) {
                    (vm.customDaysText.toIntOrNull() ?: 0) > 0 || (vm.customHoursText.toIntOrNull() ?: 0) > 0
                } else {
                    vm.selectedDuration > 0
                }

                val enabled = if (isActive) {
                    selectedCount > 0 || isDurationSet
                } else {
                    selectedCount > 0 && isDurationSet
                }

                Button(
                    onClick = throttle { vm.updateOrStartLock() },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(buttonText)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleItemPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("规则列表 (${selectedCount}/${allCount})")
                    TextButton(onClick = {
                        vm.selectAll(selectedCount != allCount)
                    }) {
                        Text(if (selectedCount == allCount) "全不选" else "全选")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "规则名称 / 来源",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "自律模式",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "锁定选中",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (groupStates.isEmpty()) {
                item {
                    Text(
                        text = "当前没有已启用的规则组，请先前往订阅页面启用规则。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.itemPadding()
                    )
                }
            } else {
                items(groupStates, key = { s -> "${s.group.subsItem.id}-${s.group.appId}-${s.group.group.key}" }) { state ->
                    LockableGroupItemNew(
                        state = state,
                        onToggleIntercept = { vm.toggleIntercept(state.group) },
                        onToggleSelection = { vm.toggleRuleSelection(state.group) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun LockableGroupItemNew(
    state: GroupState,
    onToggleIntercept: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.isAlreadyLocked) { onToggleSelection() }
            .itemPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.group.group.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtext = if (state.group is ResolvedAppGroup) {
                "${state.group.app.name ?: state.group.appId} (${state.group.subscription.name})"
            } else {
                "全局规则 (${state.group.subscription.name})"
            }
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp) // Gap between switch and checkbox
        ) {
            Switch(
                checked = state.isInterceptEnabled,
                onCheckedChange = { onToggleIntercept() },
                modifier = Modifier.height(24.dp) // Adjust size if needed
            )
            if (state.isAlreadyLocked) {
                PerfIcon(
                    imageVector = PerfIcon.Lock,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                PerfCheckbox(
                    checked = state.isSelectedForLock,
                    onCheckedChange = { onToggleSelection() }
                )
            }
        }
    }
}

private fun formatRemainingTime(millis: Long): String {
    val minutes = millis / 60000
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${minutes}分钟"
    }
}
