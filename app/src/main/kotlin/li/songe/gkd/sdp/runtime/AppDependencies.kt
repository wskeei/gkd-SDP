package li.songe.gkd.sdp.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-level dependencies. Production installs one instance from [App];
 * policy tests can construct an instance with a fake clock and dispatchers.
 */
data class AppDependencies(
    val clock: SdpClock = SystemSdpClock,
    val dispatchers: SdpDispatchers = SdpDispatchers(),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
)

@Volatile
private var installedDependencies = AppDependencies()

val appDependencies: AppDependencies
    get() = installedDependencies

fun installAppDependencies(dependencies: AppDependencies) {
    installedDependencies = dependencies
}
