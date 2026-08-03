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
        var current = LocalDate.now()
        emit(current)
        while (true) {
            delay(60_000L)
            val next = LocalDate.now()
            if (next != current) {
                current = next
                emit(current)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    val usageGuardReviewSummaryFlow = todayFlow.flatMapLatest { today ->
        val usageGuardTodayRange = UsageGuardHistoryPolicy.dayRange(today)
        DbSet.usageGuardRecordDao
            .queryByRequestedAtRange(usageGuardTodayRange.first, usageGuardTodayRange.second)
            .map { records -> UsageGuardReviewPolicy.summarize(records) }
            .catch { emit(UsageGuardReviewPolicy.summarize(emptyList())) }
    }
        .stateInit(UsageGuardReviewPolicy.summarize(emptyList()))

    val digitalSelfDisciplineTodaySummaryFlow = todayFlow.flatMapLatest { today ->
        val bounds = UsageGuardHistoryPolicy.dayRange(today)
        SelfControlIntervalRepository.fromDb()
            .observeReviewSource(bounds.first, bounds.second)
            .map { source ->
                DigitalSelfDisciplineTodaySummary(
                    requestCount = source.usageRecords.count {
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
    val showExportBackupDlgFlow = MutableStateFlow(false)
}
