@file:JvmName("UsageGuardEditor")

package li.songe.gkd.sdp.ui

internal fun usageGuardDurationLabel(minutes: Int): String = "${minutes.coerceAtLeast(0)}分钟"
