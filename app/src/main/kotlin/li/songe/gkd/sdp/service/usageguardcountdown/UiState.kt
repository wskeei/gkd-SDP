@file:JvmName("UsageGuardCountdownUiState")

package li.songe.gkd.sdp.service

internal data class UsageGuardCountdownUiState(
    val remainingMillis: Long,
    val reasonText: String,
    val showTerminateConfirm: Boolean,
)
