package li.songe.gkd.sdp.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.CategoryConfig
import li.songe.gkd.sdp.data.ExcludeData
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.getGlobalGroupChecked
import li.songe.gkd.sdp.ui.icon.ResetSettings
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.getGroupEnable
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import java.util.Objects
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R


@Composable
fun RuleGroupCard(
    modifier: Modifier = Modifier,
    subs: RawSubscription,
    appId: String?,
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    categoryConfig: CategoryConfig?,
    focusGroupFlow: MutableStateFlow<Triple<Long, String?, Int>?>? = null,
    isSelectedMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSelectedChange: () -> Unit = {},
) {
    val mainVm = LocalMainViewModel.current
    val category = subs.getCategory(group.name)

    val inGlobalAppPage = appId != null && group is RawSubscription.RawGlobalGroup

    var highlighted by remember { mutableStateOf(false) }
    if (focusGroupFlow != null) {
        val focusGroup by focusGroupFlow.collectAsStateWithLifecycle()
        if (subs.id == focusGroup?.first && group.key == focusGroup?.third && if (group is RawSubscription.RawAppGroup) appId == focusGroup?.second else focusGroup?.second == null) {
            LaunchedEffect(isSelectedMode) {
                if (isSelectedMode) {
                    highlighted = false
                    focusGroupFlow.value = null
                    return@LaunchedEffect
                }
                delay(300)
                var i = 0
                highlighted = true
                while (isActive && i < 4) {
                    delay(400)
                    highlighted = !highlighted
                    i++
                }
                highlighted = false
                focusGroupFlow.value = null
            }
        }
    }
    val excludeData = remember(subsConfig?.exclude) {
        ExcludeData.parse(subsConfig?.exclude)
    }
    val checked = if (inGlobalAppPage) {
        getGlobalGroupChecked(
            subs,
            excludeData,
            group,
            appId,
        )
    } else {
        getGroupEnable(
            group,
            subsConfig,
            category,
            categoryConfig,
        )
    }
    val isLocked = FocusLockUtils.isRuleLocked(subs.id, appId, group.key)
    val onCheckedChange = appScope.launchAsFn<Boolean> { newChecked ->
        if (!newChecked && isLocked) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_e27c087656))
            return@launchAsFn
        }
        val newConfig = if (appId != null) {
            if (group is RawSubscription.RawGlobalGroup) {
                // APP 汇总页面 - 全局规则
                val excludeData = ExcludeData.parse(subsConfig?.exclude)
                (subsConfig ?: SubsConfig(
                    type = SubsConfig.GlobalGroupType,
                    subsId = subs.id,
                    groupKey = group.key,
                )).copy(
                    exclude = excludeData.copy(
                        appIds = excludeData.appIds.toMutableMap().apply {
                            set(appId, !newChecked)
                        }
                    ).stringify()
                )
            } else {
                // 订阅详情页面 - APP 规则
                (subsConfig?.copy(enable = newChecked) ?: SubsConfig(
                    type = SubsConfig.AppGroupType,
                    subsId = subs.id,
                    appId = appId,
                    groupKey = group.key,
                    enable = newChecked
                ))
            }
        } else {
            // 订阅详情页面 - 全局规则
            group as RawSubscription.RawGlobalGroup
            (subsConfig?.copy(enable = newChecked) ?: SubsConfig(
                type = SubsConfig.GlobalGroupType,
                subsId = subs.id,
                groupKey = group.key,
                enable = newChecked
            ))
        }
        DbSet.subsConfigDao.insert(newConfig)
    }
    val onClick = if (isSelectedMode)
        (onSelectedChange)
    else throttle(mainVm.viewModelScope.launchAsFn(Dispatchers.Default) {
        group.cacheStr // load cache
        mainVm.ruleGroupState.showGroupFlow.value = ShowGroupState(
            subsId = subs.id,
            appId = if (group is RawSubscription.RawAppGroup) appId else null,
            groupKey = group.key,
            pageAppId = appId,
        )
    })
    val containerColor = animateColorAsState(
        if (isSelected || highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tween()
    )
    Card(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = stringResource(R.string.s_bf6269f760),
                onLongClickLabel = "进入多选模式"
            ),
        shape = MaterialTheme.shapes.extraSmall,
        colors = CardDefaults.cardColors(
            containerColor = containerColor.value
        ),
    ) {
        val canRest = if (inGlobalAppPage) {
            excludeData.appIds.contains(appId)
        } else {
            subsConfig?.enable != null
        }
        val hasExcludeActivity = if (inGlobalAppPage) {
            checked != null && excludeData.activityIds.any { it.first == appId }
        } else {
            excludeData.activityIds.isNotEmpty()
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    GroupNameText(
                        modifier = Modifier.fillMaxWidth(),
                        text = group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        isGlobal = group is RawSubscription.RawGlobalGroup,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (group.valid) {
                        if (!group.desc.isNullOrBlank()) {
                            Text(
                                text = group.desc!!,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = group.errorDesc ?: li.songe.gkd.sdp.app.getString(R.string.s_5f76edc5de),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                val percent = usePercentAnimatable(!isSelectedMode)
                val switchModifier = Modifier.graphicsLayer(
                    alpha = 0.5f + (1 - 0.5f) * percent.value,
                )
                if (!group.valid) {
                    InnerDisableSwitch(
                        modifier = switchModifier,
                        valid = false,
                        isSelectedMode = isSelectedMode,
                    )
                } else if (checked != null) {
                    PerfSwitch(
                        key = Objects.hash(subs.id, appId, group.key),
                        modifier = switchModifier.minimumInteractiveComponentSize(),
                        checked = if (isLocked) true else checked,
                        enabled = !isLocked,
                        onCheckedChange = if (isSelectedMode || isLocked) null else onCheckedChange,
                        thumbContent = if (isLocked) ({
                            PerfIcon(
                                imageVector = PerfIcon.Lock,
                                modifier = Modifier.size(8.dp)
                            )
                        }) else if (canRest) ({
                            PerfIcon(
                                imageVector = ResetSettings,
                                modifier = Modifier.size(8.dp)
                            )
                        }) else null,
                    )
                } else {
                    InnerDisableSwitch(
                        modifier = switchModifier,
                        isSelectedMode = isSelectedMode,
                    )
                }
            }
            if (hasExcludeActivity) {
                PerfIcon(
                    imageVector = PerfIcon.Block,
                    contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_d8e83cfbba),
                    tint = if (isSelectedMode) {
                        LocalContentColor.current.copy(alpha = 0.5f)
                    } else {
                        LocalContentColor.current
                    },
                    modifier = Modifier
                        .padding(top = 4.dp, end = 4.dp)
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                )
            }
        }
    }
}

fun getActualGroupChecked(
    subs: RawSubscription,
    group: RawSubscription.RawGroupProps,
    appId: String?,
    subsConfig: SubsConfig?,
    categoryConfig: CategoryConfig?,
): Boolean {
    if (!group.valid) return false
    val inGlobalAppPage = appId != null && group is RawSubscription.RawGlobalGroup
    return if (inGlobalAppPage) {
        getGlobalGroupChecked(
            subs,
            ExcludeData.parse(subsConfig?.exclude),
            group,
            appId,
        )
    } else {
        getGroupEnable(
            group,
            subsConfig,
            subs.getCategory(group.name),
            categoryConfig,
        )
    } ?: false
}


@Composable
fun BatchActionButtonGroup(vm: ViewModel, selectedDataSet: Set<ShowGroupState>) {
    val mainVm = LocalMainViewModel.current
    PerfIconButton(
        imageVector = PerfIcon.ToggleOff,
        contentDescription = stringResource(R.string.s_4f453fb431),
        onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
            mainVm.dialogFlow.waitResult(
                title = li.songe.gkd.sdp.app.getString(R.string.s_93564a7ced),
                text = li.songe.gkd.sdp.app.getString(R.string.s_3d7090e33e)
            )
            val list = batchUpdateGroupEnable(selectedDataSet, false)
            if (list.isNotEmpty()) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_102626a5cc, list.size))
            } else {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_1e010b148d))
            }
        })
    )
    PerfIconButton(
        imageVector = PerfIcon.ToggleOn,
        contentDescription = stringResource(R.string.s_627b07784f),
        onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
            mainVm.dialogFlow.waitResult(
                title = li.songe.gkd.sdp.app.getString(R.string.s_93564a7ced),
                text = li.songe.gkd.sdp.app.getString(R.string.s_f68f9ce68b)
            )
            val list = batchUpdateGroupEnable(selectedDataSet, true)
            if (list.isNotEmpty()) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_b8c5d58816, list.size))
            } else {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_1e010b148d))
            }
        })
    )
    PerfIconButton(
        imageVector = ResetSettings,
        contentDescription = stringResource(R.string.s_35f7c52ddd),
        onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
            mainVm.dialogFlow.waitResult(
                title = li.songe.gkd.sdp.app.getString(R.string.s_93564a7ced),
                text = li.songe.gkd.sdp.app.getString(R.string.s_1f54fb7d63)
            )
            val list = batchUpdateGroupEnable(selectedDataSet, null)
            if (list.isNotEmpty()) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_f988888fa2, list.size))
            } else {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_1e010b148d))
            }
        })
    )
}
