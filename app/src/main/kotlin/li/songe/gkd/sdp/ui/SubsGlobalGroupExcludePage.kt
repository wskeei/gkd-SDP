package li.songe.gkd.sdp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.a11y.launcherAppId
import li.songe.gkd.sdp.data.ExcludeData
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.blockMatchAppListFlow
import li.songe.gkd.sdp.ui.component.AnimatedBooleanContent
import li.songe.gkd.sdp.ui.component.AnimatedIconButton
import li.songe.gkd.sdp.ui.component.AnimationFloatingActionButton
import li.songe.gkd.sdp.ui.component.AppBarTextField
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.AppNameText
import li.songe.gkd.sdp.ui.component.EmptyText
import li.songe.gkd.sdp.ui.component.InnerDisableSwitch
import li.songe.gkd.sdp.ui.component.MenuGroupCard
import li.songe.gkd.sdp.ui.component.MenuItemCheckbox
import li.songe.gkd.sdp.ui.component.MenuItemRadioButton
import li.songe.gkd.sdp.ui.component.MultiTextField
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfSwitch
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.TowLineText
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.ui.component.isFullVisible
import li.songe.gkd.sdp.ui.component.useListScrollState
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.icon.BackCloseIcon
import li.songe.gkd.sdp.ui.icon.ResetSettings
import li.songe.gkd.sdp.ui.share.ListPlaceholder
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.ui.share.noRippleClickable
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.AppGroupOption
import li.songe.gkd.sdp.util.AppSortOption
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.systemAppsFlow
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast

@Serializable
data class SubsGlobalGroupExcludeRoute(
    val subsItemId: Long,
    val groupKey: Int,
) : NavKey

@Composable
fun SubsGlobalGroupExcludePage(route: SubsGlobalGroupExcludeRoute) {
    val subsItemId = route.subsItemId
    val groupKey = route.groupKey

    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { SubsGlobalGroupExcludeVm(route) }
    val subs = vm.subsFlow.collectAsStateWithLifecycle().value
    val group = vm.groupFlow.collectAsStateWithLifecycle().value ?: return
    val excludeData = vm.excludeDataFlow.collectAsStateWithLifecycle().value
    val showAppInfos = vm.showAppInfosFlow.collectAsStateWithLifecycle().value

    var searchStr by vm.searchStrFlow.asMutableState()
    var editable by vm.editableFlow.asMutableState()

    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (!showSearchBar) {
            searchStr = ""
        }
    })
    val (scrollBehavior, listState) = useListScrollState(
        vm.resetKey,
        canScroll = { !editable }
    )

    BackHandler(editable, onBack = throttle(vm.viewModelScope.launchAsFn {
        context.justHideSoftInput()
        if (vm.changedValue != null) {
            mainVm.dialogFlow.waitResult(
                title = li.songe.gkd.sdp.app.getString(R.string.s_ab3656a956),
                text = li.songe.gkd.sdp.app.getString(R.string.s_aebc195621),
            )
        }
        editable = false
    }))

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable,
                navigationIcon = {
                    IconButton(onClick = throttle(vm.viewModelScope.launchAsFn {
                        if (vm.editableFlow.value) {
                            editable = false
                            context.justHideSoftInput()
                        } else {
                            context.hideSoftInput()
                            mainVm.popPage()
                        }
                    })) {
                        BackCloseIcon(backOrClose = !editable)
                    }
                },
                title = {
                    if (showSearchBar) {
                        BackHandler {
                            if (!context.justHideSoftInput()) {
                                showSearchBar = false
                            }
                        }
                        AppBarTextField(
                            value = searchStr,
                            onValueChange = { newValue ->
                                searchStr = newValue.trim()
                            },
                            hint = "请输入应用名称/ID",
                            modifier = Modifier.autoFocus(),
                        )
                    } else {
                        TowLineText(
                            title = group.name,
                            subtitle = li.songe.gkd.sdp.app.getString(R.string.s_427bec7575),
                            modifier = Modifier.noRippleClickable { vm.resetKey.intValue++ }
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
                                onClick = throttle(vm.viewModelScope.launchAsFn {
                                    val newExclude = vm.changedValue
                                    if (newExclude != null) {
                                        val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsId = subsItemId,
                                            groupKey = groupKey,
                                        )).copy(
                                            exclude = newExclude.stringify()
                                        )
                                        DbSet.subsConfigDao.insert(subsConfig)
                                        toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                                    } else {
                                        toast(li.songe.gkd.sdp.app.getString(R.string.s_fff8cc4d94))
                                    }
                                    context.justHideSoftInput()
                                    editable = false
                                }),
                            )
                        },
                        contentFalse = {
                            Row {
                                AnimatedIconButton(
                                    onClick = {
                                        if (showSearchBar) {
                                            if (searchStr.isEmpty()) {
                                                showSearchBar = false
                                            } else {
                                                searchStr = ""
                                            }
                                        } else {
                                            showSearchBar = true
                                        }
                                    },
                                    id = R.drawable.ic_anim_search_close,
                                    atEnd = showSearchBar,
                                )
                                var expanded by remember { mutableStateOf(false) }
                                PerfIconButton(
                                    imageVector = PerfIcon.Sort,
                                    onClick = {
                                        expanded = true
                                    },
                                )
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
                                                    onClick = { sortType = option }
                                                )
                                            }
                                        }
                                        MenuGroupCard(title = li.songe.gkd.sdp.app.getString(R.string.s_97d8a6c05b)) {
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
                                        MenuGroupCard(title = li.songe.gkd.sdp.app.getString(R.string.s_dcce9a144a)) {
                                            MenuItemCheckbox(
                                                text = li.songe.gkd.sdp.app.getString(R.string.s_f10b25a414),
                                                stateFlow = vm.showInnerDisabledAppFlow,
                                            )
                                            MenuItemCheckbox(
                                                text = li.songe.gkd.sdp.app.getString(R.string.s_8f74cd015b),
                                                stateFlow = vm.showBlockAppFlow,
                                            )
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
                onClick = {
                    editable = !editable
                },
                imageVector = PerfIcon.Edit,
                contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_7063fdc01d)
            )
        }
    ) { contentPadding ->
        if (editable) {
            MultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                textFlow = vm.excludeTextFlow,
                immediateFocus = true,
                placeholderText = tipText,
            )
        } else {
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(showAppInfos, { it.id }) { appInfo ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .itemPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIcon(appId = appInfo.id)
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            AppNameText(appInfo = appInfo)
                            Text(
                                text = appInfo.id,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val blockMatch =
                            blockMatchAppListFlow.collectAsStateWithLifecycle().value.contains(appInfo.id)
                        if (blockMatch) {
                            PerfIcon(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(20.dp),
                                imageVector = PerfIcon.Block,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        val checked = getGlobalGroupChecked(
                            subs,
                            excludeData,
                            group,
                            appInfo.id
                        )
                        if (checked != null) {
                            PerfSwitch(
                                key = appInfo.id,
                                checked = checked,
                                onCheckedChange = vm.viewModelScope.launchAsFn { newChecked ->
                                    val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                                        type = SubsConfig.GlobalGroupType,
                                        subsId = subsItemId,
                                        groupKey = groupKey,
                                    )).copy(
                                        exclude = excludeData.copy(
                                            appIds = excludeData.appIds.toMutableMap()
                                                .apply {
                                                    set(appInfo.id, !newChecked)
                                                })
                                            .stringify()
                                    )
                                    DbSet.subsConfigDao.insert(subsConfig)
                                },
                                thumbContent = if (excludeData.appIds.contains(appInfo.id)) ({
                                    PerfIcon(
                                        imageVector = ResetSettings,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }) else null,
                            )
                        } else {
                            InnerDisableSwitch()
                        }
                    }
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (showAppInfos.isEmpty() && searchStr.isNotEmpty()) {
                        EmptyText(text = if (vm.appFilter.showAllAppFlow.collectAsStateWithLifecycle().value) li.songe.gkd.sdp.app.getString(R.string.s_8f8274c754) else li.songe.gkd.sdp.app.getString(R.string.s_9e7d3ee61c))
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                }
            }
        }
    }
}

// null - 内置禁用
// true - 启用
// false - 禁用
fun getGlobalGroupChecked(
    subscription: RawSubscription,
    excludeData: ExcludeData,
    group: RawSubscription.RawGlobalGroup,
    appId: String,
): Boolean? {
    if (subscription.getGlobalGroupInnerDisabled(group, appId)) {
        return null
    }
    excludeData.appIds[appId]?.let { return !it }
    if (group.appIdEnable[appId] == true) return true
    if (appId == launcherAppId) {
        return group.matchLauncher ?: false
    }
    if (systemAppsFlow.value.contains(appId)) {
        return group.matchSystemApp ?: false
    }
    return group.matchAnyApp ?: true
}

private val tipText = """
以换行或英文逗号分割每条禁用
示例1-禁用单个页面
appId/activityId
示例2-禁用整个应用(移除/)
appId
示例3-开启此应用(前置!)
!appId
""".trimIndent()