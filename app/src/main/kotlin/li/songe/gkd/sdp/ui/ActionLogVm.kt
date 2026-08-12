package li.songe.gkd.sdp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.data.DailyStat
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.ActionLogStatsPolicy
import java.time.ZoneId
import li.songe.gkd.sdp.util.subsMapFlow

data class ActionLogPresentation(
    val outcomeTitleRes: Int,
    val outcomeDescriptionRes: Int,
    val subscriptionName: String?,
    val groupName: String?,
    val ruleName: String?,
) {
    companion object {
        fun from(actionLog: ActionLog): ActionLogPresentation = ActionLogPresentation(
            outcomeTitleRes = if (actionLog.outcome == ActionLog.OUTCOME_INTERCEPTED) {
                R.string.action_log_outcome_intercepted_title
            } else {
                R.string.action_log_outcome_executed_title
            },
            outcomeDescriptionRes = if (actionLog.outcome == ActionLog.OUTCOME_INTERCEPTED) {
                R.string.action_log_outcome_intercepted_description
            } else {
                R.string.action_log_outcome_executed_description
            },
            subscriptionName = actionLog.subsNameSnapshot,
            groupName = actionLog.groupNameSnapshot,
            ruleName = actionLog.ruleNameSnapshot,
        )
    }
}

class ActionLogVm(val route: ActionLogRoute) : ViewModel() {
    data class StatsUiState(
        val stats: List<DailyStat> = emptyList(),
        val hasAnyStats: Boolean = false,
    )

    companion object {
        private const val CHART_DAYS = 14

        fun evaluateStatsUiState(
            rawStats: List<DailyStat>,
            now: Long,
            days: Int,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): StatsUiState {
            return StatsUiState(
                stats = ActionLogStatsPolicy.normalizeDailyStats(
                    rawStats = rawStats,
                    now = now,
                    days = days,
                    zoneId = zoneId,
                ),
                hasAnyStats = rawStats.isNotEmpty(),
            )
        }

        fun shouldRefreshStatsWindow(
            anchorNow: Long,
            currentNow: Long,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): Boolean {
            return ActionLogStatsPolicy.localDate(anchorNow, zoneId) !=
                ActionLogStatsPolicy.localDate(currentNow, zoneId)
        }
    }

    private val statsNowFlow = MutableStateFlow(System.currentTimeMillis())

    val selectedTabIndex = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            while (isActive) {
                val anchorNow = statsNowFlow.value
                delay(
                    ActionLogStatsPolicy.nextWindowRefreshDelayMs(
                        now = anchorNow,
                    )
                )
                val currentNow = System.currentTimeMillis()
                if (shouldRefreshStatsWindow(anchorNow, currentNow)) {
                    statsNowFlow.value = currentNow
                }
            }
        }
    }

    val statsUiStateFlow: StateFlow<StatsUiState> = statsNowFlow
        .flatMapLatest { statsNow ->
            DbSet.actionLogDao.queryDailyStats(
                startTime = ActionLogStatsPolicy.windowStartEpochMs(
                    now = statsNow,
                    days = CHART_DAYS,
                ),
                subsId = route.subsId,
                appId = route.appId,
            )
                .map { rawStats ->
                    evaluateStatsUiState(
                        rawStats = rawStats,
                        now = statsNow,
                        days = CHART_DAYS,
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsUiState())

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        if (route.subsId != null) {
            DbSet.actionLogDao.pagingSubsSource(subsId = route.subsId)
        } else if (route.appId != null) {
            DbSet.actionLogDao.pagingAppSource(appId = route.appId)
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
