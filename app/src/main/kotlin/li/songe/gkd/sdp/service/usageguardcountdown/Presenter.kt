@file:JvmName("UsageGuardCountdownPresenter")

package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayPolicy

internal fun countdownRemainingText(remainingMillis: Long): String =
    UsageGuardCountdownOverlayPolicy.formatRemainingDuration(remainingMillis)

internal fun UsageGuardCountdownUiState.reduce(action: UsageGuardCountdownAction): UsageGuardCountdownUiState =
    when (action) {
        UsageGuardCountdownAction.OpenTerminateConfirm -> copy(showTerminateConfirm = true)
        UsageGuardCountdownAction.DismissTerminateConfirm -> copy(showTerminateConfirm = false)
    }
