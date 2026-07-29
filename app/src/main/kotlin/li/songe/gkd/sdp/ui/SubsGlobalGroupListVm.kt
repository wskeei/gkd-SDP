package li.songe.gkd.sdp.ui

import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.component.ShowGroupState
import li.songe.gkd.sdp.ui.share.BaseViewModel

class SubsGlobalGroupListVm(val route: SubsGlobalGroupListRoute) : BaseViewModel() {
    val subsRawFlow = mapSafeSubs(route.subsItemId)

    val subsConfigsFlow = DbSet.subsConfigDao.queryGlobalGroupTypeConfig(route.subsItemId)
        .stateInit(emptyList())

    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())
    val focusGroupFlow = route.focusGroupKey?.let {
        MutableStateFlow<Triple<Long, String?, Int>?>(
            Triple(
                route.subsItemId,
                null,
                route.focusGroupKey
            )
        )
    }
}