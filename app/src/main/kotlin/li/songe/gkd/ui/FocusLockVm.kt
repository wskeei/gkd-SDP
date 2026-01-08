package li.songe.gkd.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.data.FocusLock
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.FocusLockUtils
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast

class FocusLockVm : BaseViewModel() {
    val selectedRulesFlow = MutableStateFlow<Set<FocusLock.LockedRule>>(emptySet())
    var selectedDuration by mutableIntStateOf(30)

    fun startLock() = viewModelScope.launchTry(Dispatchers.IO) {
        val rules = selectedRulesFlow.value.toList()
        if (rules.isEmpty()) {
            toast("请选择要锁定的规则组")
            return@launchTry
        }

        FocusLockUtils.createLock(rules, selectedDuration)
        toast("已锁定 ${rules.size} 个规则组，${selectedDuration} 分钟后自动解锁")
        selectedRulesFlow.value = emptySet()
    }
}
