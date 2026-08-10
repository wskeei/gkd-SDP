package li.songe.gkd.sdp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.AppConfig
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.component.AnimatedIconButton
import li.songe.gkd.sdp.ui.component.AppBarTextField
import li.songe.gkd.sdp.ui.component.EmptyText
import li.songe.gkd.sdp.ui.component.MenuGroupCard
import li.songe.gkd.sdp.ui.component.MenuItemCheckbox
import li.songe.gkd.sdp.ui.component.MenuItemRadioButton
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.SubsAppCard
import li.songe.gkd.sdp.ui.component.TowLineText
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.ui.component.useListScrollState
import li.songe.gkd.sdp.ui.component.useSubs
import li.songe.gkd.sdp.ui.share.ListPlaceholder
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.ui.share.noRippleClickable
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.AppGroupOption
import li.songe.gkd.sdp.util.AppSortOption
import li.songe.gkd.sdp.util.LOCAL_SUBS_IDS
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.throttle


@Serializable
data class SubsAppListRoute(val subsItemId: Long) : NavKey

@Composable
fun SubsAppListPage(route: SubsAppListRoute) {
    val subsItemId = route.subsItemId
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { SubsAppListVm(route) }

    val appTripleList by vm.appItemListFlow.collectAsStateWithLifecycle()
    val searchStr by vm.searchStrFlow.collectAsStateWithLifecycle()
    val constraints by li.songe.gkd.sdp.util.FocusLockUtils.allConstraintsFlow.collectAsStateWithLifecycle()

    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (!showSearchBar) {
            vm.searchStrFlow.value = ""
        }
    })
    val (scrollBehavior, listState) = useListScrollState(
        vm.resetKey,
    )
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = throttle(vm.viewModelScope.launchAsFn {
                        context.hideSoftInput()
                        mainVm.popPage()
                    }),
                )
            }, title = {
                val firstShowSearchBar = remember { showSearchBar }
                if (showSearchBar) {
                    BackHandler {
                        if (!context.justHideSoftInput()) {
                            showSearchBar = false
                        }
                    }
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                        hint = "请输入应用名称/ID",
                        modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                    )
                } else {
                    TowLineText(
                        title = useSubs(subsItemId)?.name ?: subsItemId.toString(),
                        subtitle = li.songe.gkd.sdp.app.getString(R.string.s_da6a6dc1af),
                        modifier = Modifier.noRippleClickable {
                            vm.resetKey.intValue++
                        }
                    )
                }
            }, actions = {
                AnimatedIconButton(
                    onClick = {
                        if (showSearchBar) {
                            if (vm.searchStrFlow.value.isEmpty()) {
                                showSearchBar = false
                            } else {
                                vm.searchStrFlow.value = ""
                            }
                        } else {
                            showSearchBar = true
                        }
                    },
                    id = R.drawable.ic_anim_search_close,
                    atEnd = showSearchBar,
                )
                PerfIconButton(
                    imageVector = PerfIcon.Sort,
                    onClick = {
                        expanded = true
                    },
                )
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        MenuGroupCard(inTop = true, title = li.songe.gkd.sdp.app.getString(R.string.s_dc35af8d69)) {
                            var sortType by vm.sortTypeFlow.asMutableState()
                            AppSortOption.objects.forEach { option ->
                                MenuItemRadioButton(
                                    text = option.label,
                                    selected = sortType == option,
                                    onClick = { sortType = option },
                                )
                            }
                        }
                        MenuGroupCard(title = li.songe.gkd.sdp.app.getString(R.string.s_97d8a6c05b)) {
                            var appGroupType by vm.appGroupTypeFlow.asMutableState()
                            AppGroupOption.allObjects.forEach { option ->
                                val newValue = option.invert(appGroupType)
                                MenuItemCheckbox(
                                    enabled = newValue != 0,
                                    text = option.label,
                                    checked = option.include(appGroupType),
                                    onClick = { appGroupType = newValue },
                                )
                            }
                        }
                        MenuGroupCard(title = li.songe.gkd.sdp.app.getString(R.string.s_dcce9a144a)) {
                            MenuItemCheckbox(
                                text = li.songe.gkd.sdp.app.getString(R.string.s_8f74cd015b),
                                stateFlow = vm.showBlockAppFlow,
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            if (LOCAL_SUBS_IDS.contains(subsItemId)) {
                FloatingActionButton(onClick = throttle {
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = subsItemId,
                            groupKey = null,
                            appId = "",
                            forward = true,
                        )
                    )
                }) {
                    PerfIcon(
                        imageVector = PerfIcon.Add,
                    )
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState
        ) {
            items(appTripleList, { it.id }) { a ->
                val isLocked = remember(a.id, subsItemId, constraints) {
                    li.songe.gkd.sdp.util.FocusLockUtils.isAppLocked(subsItemId, a.id)
                }
                SubsAppCard(
                    data = a,
                    isLocked = isLocked,
                    onClick = throttle {
                        context.justHideSoftInput()
                        mainVm.navigatePage(SubsAppGroupListRoute(subsItemId, a.id))
                    },
                    onValueChange = vm.viewModelScope.launchAsFn { enable ->
                        val newItem = a.appConfig?.copy(
                            enable = enable
                        ) ?: AppConfig(
                            enable = enable,
                            subsId = subsItemId,
                            appId = a.id,
                        )
                        DbSet.appConfigDao.insert(newItem)
                    },
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                val firstLoading by vm.firstLoadingFlow.collectAsStateWithLifecycle()
                if (appTripleList.isEmpty() && !firstLoading) {
                    EmptyText(
                        text = if (searchStr.isNotEmpty()) {
                            if (vm.showAllAppFlow.collectAsStateWithLifecycle().value) li.songe.gkd.sdp.app.getString(R.string.s_8f8274c754) else li.songe.gkd.sdp.app.getString(R.string.s_9e7d3ee61c)
                        } else {
                            li.songe.gkd.sdp.app.getString(R.string.s_cff584d9ab)
                        }
                    )
                    Spacer(modifier = Modifier.height(EmptyHeight / 2))
                }
            }
        }
    }
}
