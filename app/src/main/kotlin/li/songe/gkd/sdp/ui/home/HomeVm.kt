package li.songe.gkd.sdp.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.store.actionCountFlow
import li.songe.gkd.sdp.store.blockMatchAppListFlow
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.ui.share.asMutableStateFlow
import li.songe.gkd.sdp.ui.share.useAppFilter
import li.songe.gkd.sdp.util.AppSortOption
import li.songe.gkd.sdp.util.EMPTY_RULE_TIP
import li.songe.gkd.sdp.util.findOption
import li.songe.gkd.sdp.util.getSubsStatus
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.usedSubsEntriesFlow

class HomeVm : BaseViewModel() {

    val subsStatusFlow by lazy {
        combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
            getSubsStatus(ruleSummary, count)
        }.stateInit(EMPTY_RULE_TIP)
    }

    val usedSubsItemCountFlow = usedSubsEntriesFlow.mapNew { it.size }

    val sortTypeFlow = storeFlow.asMutableStateFlow(
        getter = { AppSortOption.objects.findOption(it.appSort) },
        setter = {
            storeFlow.value.copy(appSort = it.value)
        }
    )
    val showBlockAppFlow = storeFlow.asMutableStateFlow(
        getter = { it.showBlockApp },
        setter = {
            storeFlow.value.copy(showBlockApp = it)
        }
    )
    val appGroupTypeFlow = storeFlow.asMutableStateFlow(
        getter = { it.appGroupType },
        setter = {
            storeFlow.value.copy(appGroupType = it)
        }
    )

    val editWhiteListModeFlow = MutableStateFlow(false)
    val blockAppListFlow = MutableStateFlow(blockMatchAppListFlow.value).also { stateFlow ->
        combine(blockMatchAppListFlow, editWhiteListModeFlow) { it }.launchCollect {
            if (!editWhiteListModeFlow.value) {
                stateFlow.value = blockMatchAppListFlow.value
            }
        }
    }

    val appFilter = useAppFilter(
        appGroupTypeFlow = appGroupTypeFlow,
        sortTypeFlow = sortTypeFlow,
        showBlockAppFlow = showBlockAppFlow,
        blockAppListFlow = blockAppListFlow,
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showSearchBarFlow = MutableStateFlow(false).apply {
        launchCollect {
            if (!it) {
                searchStrFlow.value = ""
            }
        }
    }
    val appInfosFlow = appFilter.appListFlow.apply {
        launchOnChange {
            MainViewModel.instance.appListKeyFlow.value++
        }
    }

    val showToastInputDlgFlow = MutableStateFlow(false)
    val showNotifTextInputDlgFlow = MutableStateFlow(false)
    val showToastSettingsDlgFlow = MutableStateFlow(false)
    val showA11yBlockDlgFlow = MutableStateFlow(false)
}
