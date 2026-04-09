package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardAppProfile

object UsageGuardUiStatePolicy {
    val defaultDurationOptions = listOf(10, 15, 30, 60)

    data class SelectedAppSections(
        val strictAppIds: List<String>,
        val resumableAppIds: List<String>,
    )

    fun normalizeDurationOptions(raw: List<Int>): List<Int> {
        val cleaned = raw.filter { it > 0 }.take(4).toMutableList()
        defaultDurationOptions.forEach { fallback ->
            if (cleaned.size < 4) {
                cleaned += fallback
            }
        }
        return cleaned.take(4)
    }

    fun groupSelectedApps(
        profiles: List<UsageGuardAppProfile>,
        defaultGrantMode: Int,
    ): SelectedAppSections {
        val selected = profiles.filter { it.selectedTarget }
        val strict = selected
            .filter {
                resolvedGrantMode(it.grantMode, defaultGrantMode) == UsageGuardPolicy.GRANT_MODE_STRICT
            }
            .map { it.appId }
        val resumable = selected
            .filter {
                resolvedGrantMode(it.grantMode, defaultGrantMode) == UsageGuardPolicy.GRANT_MODE_RESUMABLE
            }
            .map { it.appId }
        return SelectedAppSections(
            strictAppIds = strict,
            resumableAppIds = resumable,
        )
    }

    private fun resolvedGrantMode(grantMode: Int, defaultGrantMode: Int): Int {
        return when (grantMode) {
            UsageGuardPolicy.GRANT_MODE_STRICT,
            UsageGuardPolicy.GRANT_MODE_RESUMABLE -> grantMode
            else -> defaultGrantMode
        }
    }
}
