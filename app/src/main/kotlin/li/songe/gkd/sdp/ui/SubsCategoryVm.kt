package li.songe.gkd.sdp.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.BaseViewModel

class SubsCategoryVm(val route: SubsCategoryRoute) : BaseViewModel() {
    val subsRawFlow = mapSafeSubs(route.subsItemId)

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(route.subsItemId)
        .stateInit(emptyList())

    val categoryConfigMapFlow = categoryConfigsFlow.map { it.associateBy { c -> c.categoryKey } }
        .stateInit(emptyMap())

    val showAddCategoryFlow = MutableStateFlow(false)
}