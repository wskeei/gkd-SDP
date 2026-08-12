@file:JvmName("ActionLogScreen")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.data.ExcludeData
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.util.subsItemsFlow
import li.songe.gkd.sdp.util.toast

@Composable
fun ActionLogPage(route: ActionLogRoute) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { ActionLogVm(route) }
    val selectedTabIndex by vm.selectedTabIndex.collectAsStateWithLifecycle()
    val detail by vm.showActionLogFlow.collectAsStateWithLifecycle()
    val uiState = ActionLogUiState(
        selectedTabIndex = selectedTabIndex,
        detail = detail,
    )
    val dismissDetail = { vm.dispatch(ActionLogAction.DismissDetail) }
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val statsUiState by vm.statsUiStateFlow.collectAsStateWithLifecycle()

    ActionLogPageSections(
        route = route,
        uiState = uiState,
        list = list,
        statsUiState = statsUiState,
        onBack = mainVm::popPage,
        onDeleteAll = {
            mainVm.viewModelScope.launch {
                mainVm.dialogFlow.waitResult(
                    title = li.songe.gkd.sdp.app.getString(R.string.s_8f22c9908e),
                    text = when {
                        route.subsId != null -> li.songe.gkd.sdp.app.getString(R.string.action_log_delete_subs)
                        route.appId != null -> li.songe.gkd.sdp.app.getString(R.string.action_log_delete_app)
                        else -> li.songe.gkd.sdp.app.getString(R.string.action_log_delete_all)
                    },
                    error = true,
                )
                when {
                    route.subsId != null -> DbSet.actionLogDao.deleteSubsAll(route.subsId)
                    route.appId != null -> DbSet.actionLogDao.deleteAppAll(route.appId)
                    else -> DbSet.actionLogDao.deleteAll()
                }
                toast(li.songe.gkd.sdp.app.getString(R.string.s_86e8d12a79))
            }
        },
        onSelectTab = { vm.dispatch(ActionLogAction.SelectTab(it)) },
        onOpenDetail = { vm.dispatch(ActionLogAction.OpenDetail(it)) },
        onDismissDetail = dismissDetail,
        onOpenAppConfig = { actionLog ->
            mainVm.navigatePage(AppConfigRoute(appId = actionLog.appId))
        },
        onOpenSubsSheet = { actionLog ->
            if (subsItemsFlow.value.any { it.id == actionLog.subsId }) {
                mainVm.sheetSubsIdFlow.value = actionLog.subsId
            } else {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_9e5cc3140b))
            }
        },
        onOpenGroup = {
            val d = detail
            if (d != null) {
                dismissDetail()
                if (d.groupType == SubsConfig.AppGroupType) {
                    mainVm.navigatePage(
                        SubsAppGroupListRoute(
                            d.subsId,
                            d.appId,
                            d.groupKey,
                        ),
                    )
                } else if (d.groupType == SubsConfig.GlobalGroupType) {
                    mainVm.navigatePage(
                        SubsGlobalGroupListRoute(
                            d.subsId,
                            d.groupKey,
                        ),
                    )
                }
            }
        },
        onToggleGlobalApp = { subsConfig, oldExclude, appChecked ->
            val d = detail
            if (d != null) {
                vm.viewModelScope.launch {
                    val effectiveConfig = subsConfig ?: SubsConfig(
                        type = SubsConfig.GlobalGroupType,
                        subsId = d.subsId,
                        groupKey = d.groupKey,
                    )
                    DbSet.subsConfigDao.insert(
                        effectiveConfig.copy(
                            exclude = oldExclude.copy(
                                appIds = oldExclude.appIds.toMutableMap().apply {
                                    set(d.appId, appChecked)
                                },
                            ).stringify(),
                        ),
                    )
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                }
            }
        },
        onTogglePage = { subsConfig, oldExclude ->
            val d = detail
            if (d != null) {
                vm.viewModelScope.launch {
                    val effectiveConfig = if (d.groupType == SubsConfig.AppGroupType) {
                        subsConfig ?: SubsConfig(
                            type = SubsConfig.AppGroupType,
                            subsId = d.subsId,
                            appId = d.appId,
                            groupKey = d.groupKey,
                        )
                    } else {
                        subsConfig ?: SubsConfig(
                            type = SubsConfig.GlobalGroupType,
                            subsId = d.subsId,
                            groupKey = d.groupKey,
                        )
                    }
                    DbSet.subsConfigDao.insert(
                        effectiveConfig.copy(
                            exclude = oldExclude.switch(
                                d.appId,
                                d.activityId,
                            ).stringify(),
                        ),
                    )
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                }
            }
        },
    )
}
