package li.songe.gkd.sdp.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class AdvancedVm : ViewModel() {

    val showEditPortDlgFlow = MutableStateFlow(false)
    val showShizukuStateFlow = MutableStateFlow(false)
    val showCaptureScreenshotDlgFlow = MutableStateFlow(false)
}
