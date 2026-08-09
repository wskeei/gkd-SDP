package li.songe.gkd.sdp.ui

import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.data.CrashData
import li.songe.gkd.sdp.ui.share.BaseViewModel

object CrashReportRepository {
    private val pendingFlow = MutableStateFlow<List<CrashData>>(emptyList())

    fun publish(value: List<CrashData>) {
        pendingFlow.value = value
    }

    fun consume(): List<CrashData> {
        val value = pendingFlow.value
        pendingFlow.value = emptyList()
        return value
    }
}

class CrashReportVm(
    private val repository: CrashReportRepository = CrashReportRepository,
) : BaseViewModel() {
    val crashDataList = repository.consume()
    val crashSummaries = crashDataList.map { it.summaryText }
}
