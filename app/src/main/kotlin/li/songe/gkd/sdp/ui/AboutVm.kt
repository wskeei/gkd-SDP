package li.songe.gkd.sdp.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class AboutVm : ViewModel() {
    val showInfoDlgFlow = MutableStateFlow(false)
    val showShareLogDlgFlow = MutableStateFlow(false)
    val showShareAppDlgFlow = MutableStateFlow(false)
}