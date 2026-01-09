package li.songe.gkd.sdp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import li.songe.gkd.sdp.data.FocusLock
import li.songe.gkd.sdp.data.ResolvedAppGroup
import li.songe.gkd.sdp.data.ResolvedGroup
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
    val lockableGroups by vm.lockableGroupsFlow.collectAsState()
    val selectedRules by vm.selectedRulesFlow.collectAsState()

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
                title = { Text(text = "规则锁定") }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            if (activeLock != null && activeLock!!.isActive) {
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
            } else {
                item {
                    Text("锁定时长", modifier = Modifier.titleItemPadding())
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
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .titleItemPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("选择锁定规则 (${selectedRules.size}/${lockableGroups.size})")
                        TextButton(onClick = {
                            if (selectedRules.size == lockableGroups.size) {
                                vm.selectedRulesFlow.value = emptySet()
                            } else {
                                vm.selectedRulesFlow.value = lockableGroups.map { g ->
                                    FocusLock.LockedRule(g.subsItem.id, g.group.key, g.appId)
                                }.toSet()
                            }
                        }) {
                            Text(if (selectedRules.size == lockableGroups.size) "全不选" else "全选")
                        }
                    }
                }

                if (lockableGroups.isEmpty()) {
                    item {
                        Text(
                            text = "当前没有已启用的规则组，请先前往订阅页面启用规则。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.itemPadding()
                        )
                    }
                } else {
                    items(lockableGroups, key = { g -> "${g.subsItem.id}-${g.appId}-${g.group.key}" }) { group ->
                        val lockedRule = FocusLock.LockedRule(group.subsItem.id, group.group.key, group.appId)
                        val isSelected = selectedRules.contains(lockedRule)
                        LockableGroupItem(
                            group = group,
                            isSelected = isSelected,
                            onToggle = { vm.toggleRule(lockedRule) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                item {
                    Button(
                        onClick = throttle { vm.startLock() },
                        enabled = selectedRules.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(if (selectedRules.isEmpty()) "请选择规则" else "开始锁定 (${selectedRules.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun LockableGroupItem(
    group: ResolvedGroup,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .itemPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.group.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtext = if (group is ResolvedAppGroup) {
                "${group.app.name ?: group.appId} (${group.subscription.name})"
            } else {
                "全局规则 (${group.subscription.name})"
            }
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PerfCheckbox(checked = isSelected, onCheckedChange = { onToggle() })
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