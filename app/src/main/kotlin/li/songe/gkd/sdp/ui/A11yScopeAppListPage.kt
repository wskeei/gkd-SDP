package li.songe.gkd.sdp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.store.a11yScopeAppListFlow
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.AnimatedBooleanContent
import li.songe.gkd.sdp.ui.component.AnimatedIconButton
import li.songe.gkd.sdp.ui.component.AnimationFloatingActionButton
import li.songe.gkd.sdp.ui.component.AppBarTextField
import li.songe.gkd.sdp.ui.component.AppCheckBoxCard
import li.songe.gkd.sdp.ui.component.EmptyText
import li.songe.gkd.sdp.ui.component.MenuGroupCard
import li.songe.gkd.sdp.ui.component.MenuItemCheckbox
import li.songe.gkd.sdp.ui.component.MenuItemRadioButton
import li.songe.gkd.sdp.ui.component.MultiTextField
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.ui.component.isFullVisible
import li.songe.gkd.sdp.ui.component.useListScrollState
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.icon.BackCloseIcon
import li.songe.gkd.sdp.ui.share.ListPlaceholder
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.ui.share.noRippleClickable
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.AppGroupOption
import li.songe.gkd.sdp.util.AppListString
import li.songe.gkd.sdp.util.AppSortOption
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.switchItem
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast

@Serializable
data object A11YScopeAppListRoute : NavKey

@Composable
fun A11yScopeAppListPage() {
    val store by storeFlow.collectAsStateWithLifecycle()
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<A11yScopeAppListVm>()
    val appInfos by vm.appInfosFlow.collectAsStateWithLifecycle()
    val searchStr by vm.searchStrFlow.collectAsStateWithLifecycle()
    var showSearchBar by vm.showSearchBarFlow.asMutableState()
    var editable by vm.editableFlow.asMutableState()
    val (scrollBehavior, listState) = useListScrollState(vm.resetKey, canScroll = { !editable })
    BackHandler(editable, vm.viewModelScope.launchAsFn {
        context.justHideSoftInput()
        if (vm.textChanged) {
            mainVm.dialogFlow.waitResult(
                title = li.songe.gkd.sdp.app.getString(R.string.s_ab3656a956),
                text = li.songe.gkd.sdp.app.getString(R.string.s_aebc195621),
            )
        }
        editable = false
    })
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable && !store.blockA11yAppListFollowMatch,
                navigationIcon = {
                    IconButton(
                        onClick = throttle(vm.viewModelScope.launchAsFn {
                            if (editable) {
                                if (vm.textChanged) {
                                    context.justHideSoftInput()
                                    mainVm.dialogFlow.waitResult(
                                        title = li.songe.gkd.sdp.app.getString(R.string.s_ab3656a956),
                                        text = li.songe.gkd.sdp.app.getString(R.string.s_aebc195621),
                                    )
                                }
                                editable = !editable
                            } else {
                                context.hideSoftInput()
                                mainVm.popPage()
                            }
                        })
                    ) {
                        BackCloseIcon(backOrClose = !editable)
                    }
                },
                title = {
                    val firstShowSearchBar = remember { showSearchBar }
                    if (showSearchBar) {
                        BackHandler {
                            if (!context.justHideSoftInput()) {
                                showSearchBar = false
                            }
                        }
                        AppBarTextField(
                            value = searchStr,
                            onValueChange = { newValue ->
                                vm.searchStrFlow.value = newValue.trim()
                            },
                            hint = stringResource(R.string.app_list_search_hint),
                            modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                        )
                    } else {
                        val titleModifier = Modifier
                            .noRippleClickable(
                                onClick = throttle {
                                    vm.resetKey.intValue++
                                }
                            )
                        Text(
                            modifier = titleModifier,
                            text = li.songe.gkd.sdp.app.getString(R.string.s_3721fe11a2),
                        )
                    }
                },
                actions = {
                    AnimatedBooleanContent(
                        targetState = editable,
                        contentAlignment = Alignment.TopEnd,
                        contentTrue = {
                            PerfIconButton(
                                imageVector = PerfIcon.Save,
                                onClick = throttle {
                                    if (vm.textChanged) {
                                        a11yScopeAppListFlow.value =
                                            AppListString.decode(vm.textFlow.value)
                                        toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                                    } else {
                                        toast(li.songe.gkd.sdp.app.getString(R.string.s_fff8cc4d94))
                                    }
                                    context.justHideSoftInput()
                                    editable = false
                                },
                            )
                        },
                        contentFalse = {
                            Row {
                                var expanded by remember { mutableStateOf(false) }
                                AnimatedIconButton(
                                    onClick = throttle {
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
                                PerfIconButton(imageVector = PerfIcon.Sort, onClick = {
                                    expanded = true
                                })
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
                                        MenuGroupCard(inTop = true, title = stringResource(R.string.app_list_filter_title)) {
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
                                    }
                                }
                            }
                        },
                    )
                })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = !editable && scrollBehavior.isFullVisible,
                onClickLabel = li.songe.gkd.sdp.app.getString(R.string.a11y_scope_edit_text_mode),
                onClick = {
                    editable = !editable
                },
                imageVector = PerfIcon.Edit,
                contentDescription = li.songe.gkd.sdp.app.getString(R.string.a11y_scope_edit_text)
            )
        },
    ) { contentPadding ->
        if (editable) {
            MultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                textFlow = vm.textFlow,
                immediateFocus = true,
                placeholderText = stringResource(R.string.app_list_id_placeholder),
                indicatorSize = vm.indicatorSizeFlow.collectAsStateWithLifecycle().value,
            )
        } else {
            val a11yScopeAppList by a11yScopeAppListFlow.collectAsStateWithLifecycle()
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    val checked = a11yScopeAppList.contains(appInfo.id)
                    AppCheckBoxCard(
                        appInfo = appInfo,
                        checked = checked,
                        onCheckedChange = {
                            a11yScopeAppListFlow.update {
                                it.switchItem(appInfo.id)
                            }
                        },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        EmptyText(text = li.songe.gkd.sdp.app.getString(R.string.s_8f8274c754))
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                }
            }
        }
    }
}
