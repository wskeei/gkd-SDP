@file:JvmName("UsageGuardCountdownPresenter")

package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayPolicy

internal fun countdownRemainingText(expiresAt: Long, nowEpochMs: Long): String =
    UsageGuardCountdownOverlayPolicy.formatRemainingText(expiresAt, nowEpochMs)
