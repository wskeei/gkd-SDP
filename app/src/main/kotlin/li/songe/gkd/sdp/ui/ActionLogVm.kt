package li.songe.gkd.sdp.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import com.ramcosta.composedestinations.generated.destinations.ActionLogPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.data.DailyStat
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.ActionLogStatsPolicy
import li.songe.gkd.sdp.util.subsMapFlow

class ActionLogVm(stateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private const val CHART_DAYS = 14
    }

    private val args = ActionLogPageDestination.argsFrom(stateHandle)
    private val statsNow = System.currentTimeMillis()
    private val statsWindowStart = ActionLogStatsPolicy.windowStartEpochMs(
        now = statsNow,
        days = CHART_DAYS,
    )
    private val rawDailyStatsFlow = DbSet.actionLogDao.queryDailyStats(
        startTime = statsWindowStart,
        subsId = args.subsId,
        appId = args.appId,
    )

    val selectedTabIndex = MutableStateFlow(0)

    val dailyStatsFlow: StateFlow<List<DailyStat>> = rawDailyStatsFlow
        .map { rawStats ->
            ActionLogStatsPolicy.normalizeDailyStats(
                rawStats = rawStats,
                now = statsNow,
                days = CHART_DAYS,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasAnyStatsFlow: StateFlow<Boolean> = rawDailyStatsFlow
        .map { rawStats -> rawStats.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        if (args.subsId != null) {
            DbSet.actionLogDao.pagingSubsSource(subsId = args.subsId)
        } else if (args.appId != null) {
            DbSet.actionLogDao.pagingAppSource(appId = args.appId)
        } else {
            DbSet.actionLogDao.pagingSource()
        }
    }
        .flow
        .cachedIn(viewModelScope)
        .combine(subsMapFlow) { pagingData, subsMap ->
            pagingData.map { c ->
                val group = if (c.groupType == SubsConfig.AppGroupType) {
                    val app = subsMap[c.subsId]?.apps?.find { a -> a.id == c.appId }
                    app?.groups?.find { g -> g.key == c.groupKey }
                } else {
                    subsMap[c.subsId]?.globalGroups?.find { g -> g.key == c.groupKey }
                }
                val rule = group?.rules?.run {
                    if (c.ruleKey != null) {
                        find { r -> r.key == c.ruleKey }
                    } else {
                        getOrNull(c.ruleIndex)
                    }
                }
                Triple(c, group, rule)
            }
        }

    val showActionLogFlow = MutableStateFlow<ActionLog?>(null)

}
