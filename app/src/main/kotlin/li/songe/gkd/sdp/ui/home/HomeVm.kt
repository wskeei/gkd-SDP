package li.songe.gkd.sdp.ui.home

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.store.actionCountFlow
import li.songe.gkd.sdp.store.blockMatchAppListFlow
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.ui.share.asMutableStateFlow
import li.songe.gkd.sdp.ui.share.useAppFilter
import li.songe.gkd.sdp.util.AppSortOption
import li.songe.gkd.sdp.util.EMPTY_RULE_TIP
import li.songe.gkd.sdp.util.UsageGuardHistoryPolicy
import li.songe.gkd.sdp.util.UsageGuardReviewPolicy
import li.songe.gkd.sdp.util.findOption
import li.songe.gkd.sdp.util.getSubsStatus
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.usedSubsEntriesFlow
import java.time.LocalDate
import java.time.ZoneId

data class DigitalSelfDisciplineTodaySummary(
    val requestCount: Int,
    val interceptCount: Int,
)

class HomeVm : BaseViewModel() {

    val subsStatusFlow by lazy {
        combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
            getSubsStatus(ruleSummary, count)
        }.stateInit(EMPTY_RULE_TIP)
    }

    val usedSubsItemCountFlow = usedSubsEntriesFlow.mapNew { it.size }

    private val todayFlow = flow {
        var current = homeClock()
        emit(current)
        while (true) {
            delay(60_000L)
            val next = homeClock()
            if (next != current) {
                current = next
                emit(current)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, homeClock())

    val usageGuardReviewSummaryFlow = todayFlow.flatMapLatest { clock ->
        val usageGuardTodayRange = UsageGuardHistoryPolicy.dayRange(clock.date, clock.zoneId)
        DbSet.usageGuardRecordDao
            .queryByRequestedAtRange(usageGuardTodayRange.first, usageGuardTodayRange.second)
            .map { records -> UsageGuardReviewPolicy.summarize(records) }
            .catch { emit(UsageGuardReviewPolicy.summarize(emptyList())) }
    }
        .stateInit(UsageGuardReviewPolicy.summarize(emptyList()))

    val digitalSelfDisciplineTodaySummaryFlow = todayFlow.flatMapLatest { clock ->
        val bounds = UsageGuardHistoryPolicy.dayRange(clock.date, clock.zoneId)
        SelfControlIntervalRepository.fromDb()
            .observeReviewSource(bounds.first, bounds.second)
            .map { source ->
                DigitalSelfDisciplineTodaySummary(
                    requestCount = source.usageRows.count {
                        it.requestedAt >= bounds.first && it.requestedAt < bounds.second
                    },
                    interceptCount = source.interceptEvents.count {
                        it.occurredAt >= bounds.first && it.occurredAt < bounds.second
                    },
                )
            }
            .catch { emit(DigitalSelfDisciplineTodaySummary(0, 0)) }
    }.stateInit(DigitalSelfDisciplineTodaySummary(0, 0))

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
    val appInfosFlow = appFilter.appListFlow

    val showToastInputDlgFlow = MutableStateFlow(false)
    val showNotifTextInputDlgFlow = MutableStateFlow(false)
    val showToastSettingsDlgFlow = MutableStateFlow(false)
    val showA11yBlockDlgFlow = MutableStateFlow(false)
    val showBackupDlgFlow = MutableStateFlow(false)

    private fun homeClock() = HomeClock(
        date = LocalDate.now(),
        zoneId = ZoneId.systemDefault(),
    )

    private data class HomeClock(
        val date: LocalDate,
        val zoneId: ZoneId,
    )
}
