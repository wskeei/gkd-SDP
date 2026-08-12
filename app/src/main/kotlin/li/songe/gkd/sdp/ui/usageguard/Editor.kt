@file:JvmName("UsageGuardEditor")

package li.songe.gkd.sdp.ui

import android.content.Context
import li.songe.gkd.sdp.R

// i18n-ignore: legacy fallback or non-display heuristic data
internal fun usageGuardDurationLabel(minutes: Int): String = "${minutes.coerceAtLeast(0)}分钟"

internal fun usageGuardDurationLabel(minutes: Int, context: Context): String =
    context.getString(R.string.focus_duration_minutes, minutes.coerceAtLeast(0))
