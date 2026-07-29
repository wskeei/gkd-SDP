package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.ui.share.BaseViewModel

class CrashReportVm : BaseViewModel() {
    val crashDataList = MainViewModel.instance.run {
        val v = tempCrashDataList
        tempCrashDataList = emptyList()
        v
    }
}