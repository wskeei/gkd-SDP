package li.songe.gkd.sdp.a11y

import android.content.Context
import android.content.Intent
import android.provider.Settings
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.util.LogUtils

enum class OverlayFailureCategory {
    PERMISSION,
    BACKGROUND_START,
    SECURITY,
    UNKNOWN,
}

sealed interface OverlayLaunchResult {
    data object Accepted : OverlayLaunchResult
    data object MissingPermission : OverlayLaunchResult
    data object RuntimeUnavailable : OverlayLaunchResult
    data class Rejected(val category: OverlayFailureCategory) : OverlayLaunchResult
}

/**
 * Single permission/error boundary for self-control overlay services.
 * Starting a service is deliberately kept separate from business cooldowns;
 * callers only commit a cooldown after receiving [OverlayLaunchResult.Accepted].
 */
class SelfControlOverlayLauncher(
    private val appContext: Context?,
    private val canDrawOverlays: () -> Boolean = {
        appContext?.let(Settings::canDrawOverlays) == true
    },
    private val startService: (Intent) -> Unit = { intent ->
        requireNotNull(appContext) { "overlay context unavailable" }.startService(intent)
    },
    private val stopService: (Intent) -> Boolean = { intent ->
        appContext?.stopService(intent) == true
    },
) {
    fun launch(intent: Intent, runtimeAvailable: Boolean = true): OverlayLaunchResult {
        if (!runtimeAvailable) return OverlayLaunchResult.RuntimeUnavailable
        if (!canDrawOverlays()) return OverlayLaunchResult.MissingPermission
        return try {
            startService(intent)
            OverlayLaunchResult.Accepted
        } catch (error: Throwable) {
            val category = when (error) {
                is SecurityException -> OverlayFailureCategory.SECURITY
                is IllegalStateException -> OverlayFailureCategory.BACKGROUND_START
                else -> OverlayFailureCategory.UNKNOWN
            }
            runCatching {
                LogUtils.d(
                    "self-control overlay start rejected",
                    "category=$category",
                    error::class.java.simpleName,
                )
            }
            OverlayLaunchResult.Rejected(category)
        }
    }

    fun stop(intent: Intent): Boolean = runCatching { stopService(intent) }
        .onFailure { error ->
            runCatching {
                LogUtils.d(
                    "self-control overlay stop rejected",
                    error::class.java.simpleName,
                )
            }
        }
        .getOrDefault(false)
}

val selfControlOverlayLauncher: SelfControlOverlayLauncher by lazy {
    SelfControlOverlayLauncher(app)
}
