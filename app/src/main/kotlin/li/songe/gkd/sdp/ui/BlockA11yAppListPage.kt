package li.songe.gkd.sdp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.service.fixRestartAutomatorService
import li.songe.gkd.sdp.store.blockA11yAppListFlow
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
import li.songe.gkd.sdp.ui.icon.LockOpenRight
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
import androidx.compose.ui.res.stringResource

@Serializable
data object BlockA11yAppListRoute : NavKey

@Composable
fun BlockA11yAppListPage() {
    val store by storeFlow.collectAsStateWithLifecycle()
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<BlockA11yAppListVm>()
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
                            hint = "请输入应用名称/ID",
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
                            text = li.songe.gkd.sdp.app.getString(R.string.s_fb2cc4f730),
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
                                        blockA11yAppListFlow.value =
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
                                PerfIconButton(
                                    imageVector = if (store.blockA11yAppListFollowMatch) PerfIcon.Lock else LockOpenRight,
                                    contentDescription = if (store.blockA11yAppListFollowMatch) li.songe.gkd.sdp.app.getString(R.string.s_6cbc419758) else li.songe.gkd.sdp.app.getString(R.string.s_469f2dcd8a),
                                    onClickLabel = li.songe.gkd.sdp.app.getString(R.string.s_a144b06fd1),
                                    onClick = throttle {
                                        showSearchBar = false
                                        storeFlow.update { it.copy(blockA11yAppListFollowMatch = !it.blockA11yAppListFollowMatch) }
                                        fixRestartAutomatorService()
                                    }
                                )

                                var expanded by remember { mutableStateOf(false) }
                                AnimatedVisibility(!store.blockA11yAppListFollowMatch) {
                                    Row {
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
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .wrapContentSize(Alignment.TopStart)
                                ) {
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
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
                                        MenuGroupCard(inTop = true, title = li.songe.gkd.sdp.app.getString(R.string.s_dcce9a144a)) {
                                            var appGroupType by vm.appGroupTypeFlow.asMutableState()
                                            AppGroupOption.normalObjects.forEach { option ->
                                                val newValue = option.invert(appGroupType)
                                                MenuItemCheckbox(
                                                    enabled = newValue != 0,
                                                    text = option.label,
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
                visible = !editable && scrollBehavior.isFullVisible && !store.blockA11yAppListFollowMatch,
                onClickLabel = li.songe.gkd.sdp.app.getString(R.string.s_668b6cf3f0),
                onClick = {
                    editable = !editable
                },
                imageVector = PerfIcon.Edit,
                contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_f6628af38b)
            )
        },
    ) { contentPadding ->
        if (store.blockA11yAppListFollowMatch) {
            Column(
                modifier = Modifier.scaffoldPadding(contentPadding),
            ) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                Text(
                    text = stringResource(R.string.s_6cbc419758),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else if (editable) {
            MultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                textFlow = vm.textFlow,
                immediateFocus = true,
                placeholderText = "请输入应用ID列表\n示例:\ncom.android.systemui\ncom.android.settings",
                indicatorSize = vm.indicatorSizeFlow.collectAsStateWithLifecycle().value,
            )
        } else {
            val blockA11yAppList by blockA11yAppListFlow.collectAsStateWithLifecycle()
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    AppCheckBoxCard(
                        appInfo = appInfo,
                        checked = blockA11yAppList.contains(appInfo.id),
                        onCheckedChange = {
                            blockA11yAppListFlow.update {
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
