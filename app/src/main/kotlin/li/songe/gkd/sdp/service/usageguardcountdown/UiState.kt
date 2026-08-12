@file:JvmName("UsageGuardCountdownUiState")

package li.songe.gkd.sdp.service
import androidx.compose.runtime.Immutable

internal data class UsageGuardCountdownUiState(
    val remainingMillis: Long,
    val reasonText: String,
    val showTerminateConfirm: Boolean,
)

internal sealed interface UsageGuardCountdownAction {
    data object OpenTerminateConfirm : UsageGuardCountdownAction
    data object DismissTerminateConfirm : UsageGuardCountdownAction
}
