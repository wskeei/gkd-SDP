package li.songe.gkd.sdp.a11y

import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.util.UsageGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardConfigurationReconcilerTest {
    @Test
    fun firstPersistedSnapshotTriggersCurrentAppReconciliation() {
        val reasons = mutableListOf<String>()
        val reconciler = UsageGuardConfigurationReconciler { reasons += it }
        val configuration = UsageGuardRuntimeConfiguration.from(
            SettingsStore(usageGuardEnabled = true),
            listOf(
                UsageGuardAppProfile(
                    appId = "com.example.reader",
                    selectedTarget = true,
                ),
            ),
        )

        reconciler.accept(configuration)

        assertEquals(listOf("usage-guard-configuration-updated"), reasons)
    }

    @Test
    fun semanticallyUnchangedSnapshotDoesNotReconcileAgain() {
        val reasons = mutableListOf<String>()
        val reconciler = UsageGuardConfigurationReconciler { reasons += it }
        val first = UsageGuardRuntimeConfiguration.from(
            SettingsStore(usageGuardEnabled = true),
            listOf(
                UsageGuardAppProfile(
                    appId = "com.example.reader",
                    selectedTarget = true,
                    updatedAt = 10L,
                ),
            ),
        )
        val sameSemantics = UsageGuardRuntimeConfiguration.from(
            SettingsStore(usageGuardEnabled = true),
            listOf(
                UsageGuardAppProfile(
                    appId = "com.example.reader",
                    selectedTarget = true,
                    updatedAt = 20L,
                ),
            ),
        )

        reconciler.accept(first)
        reconciler.accept(sameSemantics)

        assertEquals(1, reasons.size)
    }

    @Test
    fun settingOrProfileChangesTriggerReconciliation() {
        val reasons = mutableListOf<String>()
        val reconciler = UsageGuardConfigurationReconciler { reasons += it }
        val profile = UsageGuardAppProfile(
            appId = "com.example.reader",
        )

        reconciler.accept(
            UsageGuardRuntimeConfiguration.from(
                SettingsStore(usageGuardEnabled = false),
                listOf(profile),
            )
        )
        reconciler.accept(
            UsageGuardRuntimeConfiguration.from(
                SettingsStore(usageGuardEnabled = true),
                listOf(profile),
            )
        )
        reconciler.accept(
            UsageGuardRuntimeConfiguration.from(
                SettingsStore(usageGuardEnabled = true),
                listOf(profile.copy(selectedTarget = true)),
            )
        )
        reconciler.accept(
            UsageGuardRuntimeConfiguration.from(
                SettingsStore(usageGuardEnabled = true),
                listOf(profile.copy(selectedTarget = true, globalWhitelist = true)),
            )
        )
        reconciler.accept(
            UsageGuardRuntimeConfiguration.from(
                SettingsStore(usageGuardEnabled = true),
                listOf(
                    profile.copy(
                        selectedTarget = true,
                        globalWhitelist = true,
                        grantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
                    )
                ),
            )
        )

        assertEquals(5, reasons.size)
        assertTrue(reasons.all { it == "usage-guard-configuration-updated" })
    }

    @Test
    fun snapshotSortsProfilesAndOmitsPersistenceTimestamp() {
        val snapshot = UsageGuardRuntimeConfiguration.from(
            SettingsStore(
                usageGuardEnabled = true,
                usageGuardScopeMode = UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
                usageGuardDefaultGrantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
            ),
            listOf(
                UsageGuardAppProfile(
                    appId = "com.example.z",
                    updatedAt = 200L,
                ),
                UsageGuardAppProfile(
                    appId = "com.example.a",
                    globalWhitelist = true,
                    grantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
                    updatedAt = 100L,
                ),
            ),
        )

        assertEquals(true, snapshot.enabled)
        assertEquals(UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST, snapshot.scopeMode)
        assertEquals(UsageGuardPolicy.GRANT_MODE_STRICT, snapshot.defaultGrantMode)
        assertEquals(listOf("com.example.a", "com.example.z"), snapshot.profiles.map { it.appId })
    }
}
