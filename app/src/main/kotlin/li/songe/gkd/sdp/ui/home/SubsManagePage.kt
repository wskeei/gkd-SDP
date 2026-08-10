package li.songe.gkd.sdp.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.Value
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.store.switchStoreEnableMatch
import li.songe.gkd.sdp.ui.SlowGroupRoute
import li.songe.gkd.sdp.ui.UpsertRuleGroupRoute
import li.songe.gkd.sdp.ui.WebViewRoute
import li.songe.gkd.sdp.ui.component.AnimationFloatingActionButton
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.ScaffoldDialog
import li.songe.gkd.sdp.ui.component.SubsItemCard
import li.songe.gkd.sdp.ui.component.TextMenu
import li.songe.gkd.sdp.ui.component.TextSwitch
import li.songe.gkd.sdp.ui.component.usePinnedScrollBehaviorState
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.share.ListPlaceholder
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.util.LOCAL_SUBS_ID
import li.songe.gkd.sdp.util.AutoReenableDisableGuard
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.UpdateTimeOption
import li.songe.gkd.sdp.util.checkSubsUpdate
import li.songe.gkd.sdp.util.deleteSubscription
import li.songe.gkd.sdp.util.findOption
import li.songe.gkd.sdp.util.getUpDownTransform
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.mapState
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.subsItemsFlow
import li.songe.gkd.sdp.util.subsMapFlow
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.util.updateSubsMutex
import li.songe.gkd.sdp.util.usedSubsEntriesFlow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.res.stringResource

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current

    val vm = viewModel<HomeVm>()
    val subItems by subsItemsFlow.collectAsStateWithLifecycle()
    val subsIdToRaw by subsMapFlow.collectAsStateWithLifecycle()
    val constraints by FocusLockUtils.allConstraintsFlow.collectAsStateWithLifecycle()

    var orderSubItems by remember {
        mutableStateOf(subItems)
    }
    LaunchedEffect(subItems) {
        orderSubItems = subItems
    }

    val refreshing by updateSubsMutex.state.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    var isSelectedMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val draggedFlag = remember { Value(false) }
    LaunchedEffect(key1 = isSelectedMode) {
        if (!isSelectedMode && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }
    BackHandler(isSelectedMode) {
        isSelectedMode = false
    }
    LaunchedEffect(key1 = subItems.size) {
        if (subItems.size <= 1) {
            isSelectedMode = false
        }
    }

    var showSettingsDlg by remember { mutableStateOf(false) }
    if (showSettingsDlg) {
        ScaffoldDialog(
            onClose = { showSettingsDlg = false },
            title = stringResource(R.string.s_65f3531c34),
            content = {
                val store by storeFlow.collectAsStateWithLifecycle()
                TextMenu(
                    title = li.songe.gkd.sdp.app.getString(R.string.s_ecae7085ce),
                    option = UpdateTimeOption.objects.findOption(store.updateSubsInterval)
                ) {
                    storeFlow.update { s -> s.copy(updateSubsInterval = it.value) }
                }
                TextSwitch(
                    title = li.songe.gkd.sdp.app.getString(R.string.s_b151485175),
                    subtitle = li.songe.gkd.sdp.app.getString(R.string.s_19ce2aa525),
                    checked = store.subsPowerWarn,
                    onCheckedChange = throttle<Boolean> {
                        storeFlow.update { s -> s.copy(subsPowerWarn = it) }
                    }
                )
            }
        )
    }

    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, lazyListState) = usePinnedScrollBehaviorState(scrollKey)
    LaunchedEffect(null) {
        mainVm.resetPageScrollEvent.collect {
            if (it == BottomNavItem.SubsManage) {
                scrollKey.intValue++
            }
        }
    }
    return ScaffoldExt(
        navItem = BottomNavItem.SubsManage,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                if (isSelectedMode) {
                    PerfIconButton(
                        imageVector = PerfIcon.Close,
                        contentDescription = "取消选择",
                        onClick = { isSelectedMode = false },
                    )
                }
            }, title = {
                if (isSelectedMode) {
                    Text(
                        text = if (selectedIds.isNotEmpty()) selectedIds.size.toString() else "",
                    )
                } else {
                    Text(
                        text = BottomNavItem.SubsManage.label,
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                AnimatedContent(
                    targetState = isSelectedMode,
                    transitionSpec = { getUpDownTransform() },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Row {
                        if (it) {
                            val canDeleteIds = if (selectedIds.contains(LOCAL_SUBS_ID)) {
                                selectedIds - LOCAL_SUBS_ID
                            } else {
                                selectedIds
                            }
                            if (canDeleteIds.isNotEmpty()) {
                                val text = li.songe.gkd.sdp.app.getString(R.string.s_0ee51f3213, canDeleteIds.size).let { s ->
                                    if (selectedIds.contains(LOCAL_SUBS_ID)) li.songe.gkd.sdp.app.getString(R.string.s_9f3eea9816, s) else s
                                }
                                PerfIconButton(
                                    imageVector = PerfIcon.Delete,
                                    contentDescription = "删除选中订阅",
                                    onClick = vm.viewModelScope.launchAsFn {
                                        mainVm.dialogFlow.waitResult(
                                            title = li.songe.gkd.sdp.app.getString(R.string.s_fe7b16b5c0),
                                            text = text,
                                            error = true,
                                        )
                                        deleteSubscription(*canDeleteIds.toLongArray())
                                        selectedIds = selectedIds - canDeleteIds
                                        if (selectedIds.size == canDeleteIds.size) {
                                            isSelectedMode = false
                                        }
                                    },
                                )
                            }
                        } else {
                            val ruleSummary by ruleSummaryFlow.collectAsStateWithLifecycle()
                            AnimatedVisibility(
                                visible = ruleSummary.slowGroupCount > 0,
                                enter = scaleIn(),
                                exit = scaleOut(),
                            ) {
                                PerfIconButton(
                                    imageVector = PerfIcon.Eco,
                                    contentDescription = "缓慢查询规则列表",
                                    onClickLabel = "查看列表",
                                    onClick = throttle {
                                        mainVm.navigatePage(SlowGroupRoute)
                                    })
                            }
                            val scope = rememberCoroutineScope()
                            val enableMatch by remember {
                                storeFlow.mapState(scope) { s -> s.enableMatch }
                            }.collectAsStateWithLifecycle()
                            PerfIconButton(
                                id = if (enableMatch) R.drawable.ic_flash_on else R.drawable.ic_flash_off,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (!enableMatch) {
                                        CheckboxDefaults.colors().checkedBoxColor
                                    } else {
                                        LocalContentColor.current
                                    }
                                ),
                                contentDescription = "规则匹配" + if (enableMatch) "已启用" else "已禁用",
                                onClickLabel = "切换开关",
                                onClick = throttle { switchStoreEnableMatch() },
                            )
                            PerfIconButton(
                                id = R.drawable.ic_page_info,
                                contentDescription = "订阅设置",
                                onClickLabel = "打开设置弹窗",
                                onClick = {
                                    showSettingsDlg = true
                                })
                        }
                    }
                }
                PerfIconButton(
                    imageVector = PerfIcon.MoreVert,
                    contentDescription = "更多操作",
                    onClick = {
                        if (updateSubsMutex.mutex.isLocked) {
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_db8d309a8e))
                        } else {
                            expanded = true
                        }
                    })
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    key(isSelectedMode) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (isSelectedMode) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = li.songe.gkd.sdp.app.getString(R.string.s_3e44b2a933))
                                    },
                                    onClick = {
                                        expanded = false
                                        selectedIds = subItems.map { it.id }.toSet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(text = li.songe.gkd.sdp.app.getString(R.string.s_ae05880411))
                                    },
                                    onClick = {
                                        expanded = false
                                        val newSelectedIds =
                                            subItems.map { it.id }.toSet() - selectedIds
                                        if (newSelectedIds.isEmpty()) {
                                            isSelectedMode = false
                                        }
                                        selectedIds = newSelectedIds
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_0d9a428066)) },
                                    onClick = throttle {
                                        expanded = false
                                        mainVm.navigatePage(
                                            UpsertRuleGroupRoute(
                                                subsId = LOCAL_SUBS_ID,
                                                groupKey = null,
                                                appId = "",
                                                forward = true,
                                            )
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_d039ea532e)) },
                                    onClick = throttle {
                                        expanded = false
                                        mainVm.navigatePage(
                                            UpsertRuleGroupRoute(
                                                subsId = LOCAL_SUBS_ID,
                                                groupKey = null,
                                                appId = null,
                                                forward = true,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                contentDescription = "添加订阅",
                onClickLabel = "打开添加订阅弹窗",
                visible = !isSelectedMode,
                onClick = {
                    if (updateSubsMutex.mutex.isLocked) {
                        toast(li.songe.gkd.sdp.app.getString(R.string.s_2c20f3fd5e))
                    } else {
                        mainVm.viewModelScope.launchTry {
                            val url = mainVm.inputSubsLinkOption.getResult() ?: return@launchTry
                            mainVm.addOrModifySubs(url)
                        }
                    }
                },
                imageVector = PerfIcon.Add,
            )
        },
    ) { contentPadding ->
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                orderSubItems = orderSubItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                    forEachIndexed { index, subsItem ->
                        if (subsItem.order != index) {
                            this[index] = subsItem.copy(order = index)
                        }
                    }
                }
                draggedFlag.value = true
            }
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = { checkSubsUpdate(true) }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(orderSubItems, { _, subItem -> subItem.id }) { index, subItem ->
                    val canDrag = !refreshing && orderSubItems.size > 1
                    val isLocked = remember(subItem.id, constraints) {
                        FocusLockUtils.isSubscriptionLocked(subItem.id)
                    }
                    ReorderableItem(
                        state = reorderableLazyColumnState,
                        key = subItem.id,
                        enabled = canDrag,
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        SubsItemCard(
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = canDrag,
                                interactionSource = interactionSource,
                                onDragStarted = {
                                    if (orderSubItems.size > 1 && !isSelectedMode) {
                                        isSelectedMode = true
                                        selectedIds = setOf(subItem.id)
                                    }
                                },
                                onDragStopped = {
                                    if (draggedFlag.value) {
                                        draggedFlag.value = false
                                        isSelectedMode = false
                                        selectedIds = emptySet()
                                    }
                                    val changeItems = orderSubItems.filter { newItem ->
                                        subItems.find { oldItem -> oldItem.id == newItem.id }?.order != newItem.order
                                    }
                                    if (changeItems.isNotEmpty()) {
                                        vm.viewModelScope.launchTry {
                                            DbSet.subsItemDao.batchUpdateOrder(changeItems)
                                        }
                                    }
                                },
                            ),
                            interactionSource = interactionSource,
                            subsItem = subItem,
                            subscription = subsIdToRaw[subItem.id],
                            index = index + 1,
                            isSelectedMode = isSelectedMode,
                            isSelected = selectedIds.contains(subItem.id),
                            isLocked = isLocked,
                            onCheckedChange = mainVm.viewModelScope.launchAsFn { checked ->
                                if (checked && storeFlow.value.subsPowerWarn && !subItem.isLocal && usedSubsEntriesFlow.value.any { !it.subsItem.isLocal }) {
                                    mainVm.dialogFlow.waitResult(
                                        title = li.songe.gkd.sdp.app.getString(R.string.s_b151485175),
                                        textContent = {
                                            Column {
                                                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_e8c5028e79))
                                                Text(
                                                    text = li.songe.gkd.sdp.app.getString(R.string.s_9454e9b90d),
                                                    modifier = Modifier.clickable(onClick = throttle {
                                                        mainVm.dialogFlow.value = null
                                                        mainVm.navigatePage(
                                                            WebViewRoute(
                                                                initUrl = ShortUrlSet.URL6
                                                            )
                                                        )
                                                    }),
                                                    textDecoration = TextDecoration.Underline,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        confirmText = "仍然启用",
                                        error = true
                                    )
                                }
                                if (subItem.enable && !checked) {
                                    val attempt = AutoReenableDisableGuard.tryConsumeForDisable()
                                    if (!attempt.allowed) {
                                        toast(li.songe.gkd.sdp.app.getString(R.string.s_b0bb6964b5, attempt.limit))
                                        return@launchAsFn
                                    }
                                }
                                DbSet.subsItemDao.updateEnable(subItem.id, checked)
                            },
                            onSelectedChange = {
                                val newSelectedIds = if (selectedIds.contains(subItem.id)) {
                                    selectedIds.toMutableSet().apply {
                                        remove(subItem.id)
                                    }
                                } else {
                                    selectedIds + subItem.id
                                }
                                selectedIds = newSelectedIds
                                if (newSelectedIds.isEmpty()) {
                                    isSelectedMode = false
                                }
                            },
                        )
                    }
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}
