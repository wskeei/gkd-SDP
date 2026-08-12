@file:JvmName("FocusLockUiState0")

package li.songe.gkd.sdp.ui
import androidx.compose.runtime.Immutable

import li.songe.gkd.sdp.data.InterceptConfig
import li.songe.gkd.sdp.data.ResolvedGroup

@Immutable
data class FocusLockUiState(
    val subStates: List<SubscriptionState> = emptyList(),
    val expandedSubs: Set<Long> = emptySet(),
    val expandedApps: Set<String> = emptySet(),
)

sealed interface FocusLockAction {
    data class ToggleExpandSubs(val subsId: Long) : FocusLockAction
    data class ToggleExpandApp(val key: String) : FocusLockAction
    @Immutable
    data class ToggleRuleSelection(val group: ResolvedGroup) : FocusLockAction
}

data class LockTarget(
    val type: Int,
    val subsId: Long,
    val appId: String?,
    val groupKey: Int?,
    val name: String,
    val currentEndTime: Long = 0
)


data class PauseTarget(
    val subsId: Long,
    val appId: String?,
    val groupKey: Int?,
    val groupName: String,
    val config: InterceptConfig?,
    val isLocked: Boolean = false,
    val initialEnabled: Boolean = false
)
