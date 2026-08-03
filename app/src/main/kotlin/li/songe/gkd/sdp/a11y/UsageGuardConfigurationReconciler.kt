package li.songe.gkd.sdp.a11y

import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.store.SettingsStore

/**
 * The part of Usage Guard configuration that changes whether the foreground
 * app is protected. Persistence timestamps and presentation-only settings are
 * intentionally excluded so an equivalent Room emission remains a no-op.
 */
internal data class UsageGuardRuntimeConfiguration(
    val enabled: Boolean,
    val scopeMode: Int,
    val defaultGrantMode: Int,
    val profiles: List<AppProfile>,
) {
    data class AppProfile(
        val appId: String,
        val selectedTarget: Boolean,
        val globalWhitelist: Boolean,
        val grantMode: Int,
    )

    companion object {
        fun from(
            settings: SettingsStore,
            profiles: List<UsageGuardAppProfile>,
        ): UsageGuardRuntimeConfiguration {
            return UsageGuardRuntimeConfiguration(
                enabled = settings.usageGuardEnabled,
                scopeMode = settings.usageGuardScopeMode,
                defaultGrantMode = settings.usageGuardDefaultGrantMode,
                profiles = profiles
                    .map { profile ->
                        AppProfile(
                            appId = profile.appId,
                            selectedTarget = profile.selectedTarget,
                            globalWhitelist = profile.globalWhitelist,
                            grantMode = profile.grantMode,
                        )
                    }
                    .sortedBy(AppProfile::appId),
            )
        }
    }
}

/**
 * Re-dispatches the current foreground app only when the effective policy
 * configuration changes. The first snapshot is meaningful: it closes the
 * initialization gap between Room, settings and runtime attachment.
 */
internal class UsageGuardConfigurationReconciler(
    private val reconcileCurrentApp: (reason: String) -> Unit,
) {
    private var lastConfiguration: UsageGuardRuntimeConfiguration? = null

    fun accept(configuration: UsageGuardRuntimeConfiguration) {
        if (lastConfiguration == configuration) return
        lastConfiguration = configuration
        reconcileCurrentApp("usage-guard-configuration-updated")
    }
}
