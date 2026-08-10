package li.songe.gkd.sdp.ui.capability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.capability.CapabilityGraph
import li.songe.gkd.sdp.capability.CapabilityInput
import li.songe.gkd.sdp.capability.CapabilityResolver
import li.songe.gkd.sdp.capability.RuntimeModeChoice
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.permission.accessA11yState
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.canQueryPkgState
import li.songe.gkd.sdp.permission.ignoreBatteryOptimizationsState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.shizukuGrantedState
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.AutomatorModeOption

/**
 * Resolves the capability graph from the live permission state and the
 * chosen runtime mode. The caller refreshes after every system return so a
 * granted permission never queues the next dialog automatically.
 */
@Composable
fun resolveCapabilityGraph(
    mainVm: MainViewModel,
    refreshKey: Int,
): CapabilityGraph {
    val automatorMode by mainVm.automatorModeFlow.collectAsStateWithLifecycle()
    var activeLock by remember { mutableStateOf(false) }
    produceState(initialValue = activeLock, key1 = refreshKey) {
        value = runCatching {
            DbSet.digitalSelfDisciplineLockDao.hasAnyActiveLock(System.currentTimeMillis())
        }.getOrDefault(false)
        activeLock = value
    }
    val guardEnabled = storeFlow.value.accessibilityGuardEnabled
    val input = CapabilityInput(
        chosenMode = when (automatorMode?.value) {
            AutomatorModeOption.A11yMode.value -> RuntimeModeChoice.ACCESSIBILITY
            AutomatorModeOption.AutomationMode.value -> RuntimeModeChoice.AUTOMATION
            else -> null
        },
        a11yReady = accessA11yState.value,
        shizukuReady = shizukuGrantedState.value,
        overlayReady = canDrawOverlaysState.value,
        notificationReady = notificationState.value,
        batteryExempted = ignoreBatteryOptimizationsState.value,
        a11yGuardEnabled = guardEnabled,
        appListReady = canQueryPkgState.value,
        selfControlLocked = activeLock,
        isGkdFlavor = META.isGkdChannel,
    )
    return CapabilityResolver.resolve(input)
}
