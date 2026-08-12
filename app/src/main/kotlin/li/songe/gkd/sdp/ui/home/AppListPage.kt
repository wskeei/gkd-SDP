package li.songe.gkd.sdp.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.permission.canQueryPkgState
import li.songe.gkd.sdp.store.blockMatchAppListFlow
import li.songe.gkd.sdp.ui.AppConfigRoute
import li.songe.gkd.sdp.ui.EditBlockAppListRoute
import li.songe.gkd.sdp.ui.component.AnimatedIconButton
import li.songe.gkd.sdp.ui.component.AnimationFloatingActionButton
import li.songe.gkd.sdp.ui.component.AppBarTextField
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.AppNameText
import li.songe.gkd.sdp.ui.component.EmptyText
import li.songe.gkd.sdp.ui.component.MenuGroupCard
import li.songe.gkd.sdp.ui.component.MenuItemCheckbox
import li.songe.gkd.sdp.ui.component.MenuItemRadioButton
import li.songe.gkd.sdp.ui.component.PerfCheckbox
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.QueryPkgAuthCard
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.ui.component.updateDialogOptions
import li.songe.gkd.sdp.ui.component.useListScrollState
import li.songe.gkd.sdp.ui.share.ListPlaceholder
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.ui.share.noRippleClickable
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.appItemPadding
import li.songe.gkd.sdp.util.AppGroupOption
import li.songe.gkd.sdp.util.AppSortOption
import li.songe.gkd.sdp.util.appListAuthAbnormalFlow
import li.songe.gkd.sdp.util.getUpDownTransform
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.switchItem
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.updateAllAppInfo
import li.songe.gkd.sdp.util.updateAppMutex

@Composable
fun useAppListPage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity

    val vm = viewModel<HomeVm>()
    val appInfos by vm.appInfosFlow.collectAsStateWithLifecycle()
    val searchStr by vm.searchStrFlow.collectAsStateWithLifecycle()
    val ruleSummary by ruleSummaryFlow.collectAsStateWithLifecycle()

    val globalDesc = if (ruleSummary.globalGroups.isNotEmpty()) {
        stringResource(R.string.app_list_global_count, ruleSummary.globalGroups.size)
    } else {
        null
    }
    val showSearchBar by vm.showSearchBarFlow.collectAsStateWithLifecycle()
    val refreshing by updateAppMutex.state.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val editWhiteListMode by vm.editWhiteListModeFlow.collectAsStateWithLifecycle()
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, listState) = useListScrollState(scrollKey)
    LaunchedEffect(null) {
        listOf(
            canQueryPkgState.stateFlow,
            vm.appInfosFlow,
        ).forEach {
            launch {
                it.drop(1).collect {
                    scrollKey.intValue++
                }
            }
        }
        mainVm.resetPageScrollEvent.collect {
            if (it == HomeDestination.RULES) {
                scrollKey.intValue++
            }
        }
    }
    return ScaffoldExt(
        navItem = BottomNavItem.AppList,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DisposableEffect(null) {
                onDispose {
                    if (vm.searchStrFlow.value.isEmpty()) {
                        vm.showSearchBarFlow.value = false
                    }
                    vm.editWhiteListModeFlow.value = false
                }
            }
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                val firstShowSearchBar = remember { showSearchBar }
                if (showSearchBar) {
                    BackHandler {
                        if (!context.justHideSoftInput()) {
                            vm.showSearchBarFlow.value = false
                        }
                    }
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                        hint = stringResource(R.string.app_list_search_hint),
                        modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                    )
                } else {
                    val titleModifier = Modifier
                        .noRippleClickable(
                            onClick = throttle {
                                scrollKey.intValue++
                            }
                        )
                    if (editWhiteListMode) {
                        BackHandler {
                            vm.editWhiteListModeFlow.value = false
                        }
                    }
                    AnimatedContent(
                        targetState = editWhiteListMode,
                        transitionSpec = { getUpDownTransform() },
                    ) { localEditWhiteListMode ->
                        if (localEditWhiteListMode) {
                            Text(
                                modifier = titleModifier,
                                text = li.songe.gkd.sdp.app.getString(R.string.s_7395ba05d0),
                            )
                        } else {
                            Text(
                                modifier = titleModifier,
                                text = stringResource(BottomNavItem.AppList.labelRes),
                            )
                        }
                    }
                }
            }, actions = {
                if (appListAuthAbnormalFlow.collectAsStateWithLifecycle().value) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                        PerfIconButton(
                            imageVector = PerfIcon.WarningAmber,
                            contentDescription = stringResource(R.string.app_list_permission_abnormal, canQueryPkgState.name),
                            onClick = throttle {
                                mainVm.dialogFlow.updateDialogOptions(
                                    title = li.songe.gkd.sdp.app.getString(R.string.s_a15a6fbc16),
                                    text = li.songe.gkd.sdp.app.getString(R.string.s_9c818a85b3, (canQueryPkgState.name).toString())
                                )
                            },
                        )
                    }
                }
                PerfIconButton(
                    imageVector = PerfIcon.Block,
                    contentDescription = stringResource(R.string.app_list_toggle_whitelist_edit),
                    onClickLabel = if (editWhiteListMode) {
                        stringResource(R.string.app_list_exit_edit)
                    } else {
                        stringResource(R.string.app_list_enter_edit)
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (editWhiteListMode) {
                            CheckboxDefaults.colors().checkedBoxColor
                        } else {
                            LocalContentColor.current
                        }
                    ),
                    onClick = throttle {
                        vm.editWhiteListModeFlow.update { !it }
                    },
                )
                AnimatedIconButton(
                    onClick = throttle {
                        if (showSearchBar) {
                            if (vm.searchStrFlow.value.isEmpty()) {
                                vm.showSearchBarFlow.value = false
                            } else {
                                vm.searchStrFlow.value = ""
                            }
                        } else {
                            vm.showSearchBarFlow.value = true
                        }
                    },
                    id = R.drawable.ic_anim_search_close,
                    atEnd = showSearchBar,
                    contentDescription = if (showSearchBar) {
                        stringResource(R.string.app_list_close_search)
                    } else {
                        stringResource(R.string.app_list_open_search)
                    },
                )
                var expanded by remember { mutableStateOf(false) }
                PerfIconButton(
                    imageVector = PerfIcon.Sort,
                    contentDescription = stringResource(R.string.app_list_sort_filter),
                    onClick = {
                        expanded = true
                    }
                )
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        MenuGroupCard(inTop = true, title = stringResource(R.string.app_list_sort_title)) {
                            var sortType by vm.sortTypeFlow.asMutableState()
                            AppSortOption.objects.forEach { option ->
                                MenuItemRadioButton(
                                    text = stringResource(option.labelRes),
                                    selected = sortType == option,
                                    onClick = { sortType = option },
                                )
                            }
                        }
                        MenuGroupCard(title = stringResource(R.string.app_list_group_title)) {
                            var appGroupType by vm.appGroupTypeFlow.asMutableState()
                            AppGroupOption.normalObjects.forEach { option ->
                                val newValue = option.invert(appGroupType)
                                MenuItemCheckbox(
                                    enabled = newValue != 0,
                                    text = stringResource(option.labelRes),
                                    checked = option.include(appGroupType),
                                    onClick = { appGroupType = newValue },
                                )
                            }
                        }
                        MenuGroupCard(title = stringResource(R.string.app_list_filter_title)) {
                            MenuItemCheckbox(
                                text = stringResource(R.string.app_list_whitelist_title),
                                stateFlow = vm.showBlockAppFlow,
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = editWhiteListMode,
                contentDescription = stringResource(R.string.app_list_edit_whitelist),
                onClick = {
                    mainVm.navigatePage(EditBlockAppListRoute)
                },
                imageVector = PerfIcon.Edit,
            )
        }
    ) { contentPadding ->
        val canQueryPkg by canQueryPkgState.stateFlow.collectAsStateWithLifecycle()
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = { updateAllAppInfo() }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                if (!canQueryPkg) {
                    item(key = 1, contentType = 1) {
                        QueryPkgAuthCard()
                    }
                }
                items(appInfos, { it.id }) { appInfo ->
                    val desc = run {
                        if (editWhiteListMode) return@run null
                        val appGroups = ruleSummary.appIdToAllGroups[appInfo.id] ?: emptyList()
                        val appDesc = if (appGroups.isNotEmpty()) {
                            when (val disabledCount = appGroups.count { g -> !g.enable }) {
                                0 -> stringResource(R.string.app_list_group_count, appGroups.size)
                                appGroups.size -> stringResource(
                                    R.string.app_list_group_count_disabled,
                                    appGroups.size,
                                    disabledCount,
                                )
                                else -> {
                                    stringResource(
                                        R.string.app_list_group_count_enabled_disabled,
                                        appGroups.size,
                                        appGroups.size - disabledCount,
                                        disabledCount,
                                    )
                                }
                            }
                        } else {
                            null
                        }
                        if (globalDesc != null) {
                            if (appDesc != null) {
                                "$globalDesc/$appDesc"
                            } else {
                                globalDesc
                            }
                        } else {
                            appDesc
                        }
                    }
                    AppItemCard(
                        appInfo = appInfo,
                        desc = desc,
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        EmptyText(text = if (vm.appFilter.showAllAppFlow.collectAsStateWithLifecycle().value) li.songe.gkd.sdp.app.getString(R.string.s_8f8274c754) else li.songe.gkd.sdp.app.getString(R.string.s_9e7d3ee61c))
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItemCard(
    appInfo: AppInfo,
    desc: String?,
) {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<HomeVm>()
    val editWhiteListMode = vm.editWhiteListModeFlow.collectAsStateWithLifecycle().value
    val inWhiteList = blockMatchAppListFlow.collectAsStateWithLifecycle().value.contains(appInfo.id)
    Row(
        modifier = Modifier
            .clickable(
                onClick = throttle {
                    if (vm.editWhiteListModeFlow.value) {
                        blockMatchAppListFlow.update { it.switchItem(appInfo.id) }
                    } else {
                        context.justHideSoftInput()
                        mainVm.navigatePage(AppConfigRoute(appInfo.id))
                    }
                })
            .clearAndSetSemantics {
                contentDescription = if (editWhiteListMode) {
                    appInfo.name
                } else {
                    li.songe.gkd.sdp.app.getString(
                        R.string.app_list_app_content,
                        appInfo.name,
                        desc ?: appInfo.id,
                    )
                }
                if (inWhiteList) {
                    stateDescription = li.songe.gkd.sdp.app.getString(R.string.app_list_in_whitelist)
                } else if (editWhiteListMode) {
                    stateDescription = li.songe.gkd.sdp.app.getString(R.string.app_list_not_in_whitelist)
                }
                onClick(
                    label = when {
                        editWhiteListMode && inWhiteList ->
                            li.songe.gkd.sdp.app.getString(R.string.app_list_remove_from_whitelist)
                        editWhiteListMode ->
                            li.songe.gkd.sdp.app.getString(R.string.app_list_add_to_whitelist)
                        else ->
                            li.songe.gkd.sdp.app.getString(R.string.app_list_enter_rule_summary)
                    },
                    action = null
                )
            }
            .appItemPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(appId = appInfo.id)
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            AppNameText(appInfo = appInfo)
            Text(
                text = desc ?: appInfo.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
        if (editWhiteListMode) {
            PerfCheckbox(
                key = appInfo.id,
                checked = inWhiteList,
            )
        } else if (inWhiteList) {
            PerfIcon(
                modifier = Modifier
                    .padding(2.dp)
                    .size(20.dp),
                imageVector = PerfIcon.Block,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
