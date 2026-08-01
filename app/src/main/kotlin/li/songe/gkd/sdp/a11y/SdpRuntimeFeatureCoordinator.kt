package li.songe.gkd.sdp.a11y

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.util.AutomatorModeOption
import li.songe.gkd.sdp.util.LogUtils

/**
 * Routes foreground-app and raw accessibility events to self-control features.
 *
 * There can be a short handoff period while the accessibility and UiAutomation
 * runtimes overlap. The owner object, rather than the mode integer, is used to
 * fence stale callbacks from the previous runtime.
 */
class SdpRuntimeFeatureCoordinator<T>(
    private val foregroundApps: StateFlow<T>,
    private val appIdOf: (T) -> String = { it as String },
    private val scope: CoroutineScope,
    private val handlers: List<Handler>,
    private val onHandlerFailure: (handlerName: String, error: Throwable) -> Unit = { name, error ->
        runCatching { LogUtils.d("self-control handler failed", name, error::class.java.simpleName) }
    },
) {
    class RuntimeOwner internal constructor(
        val identity: Any,
        val mode: AutomatorModeOption,
        val runtime: A11yCommonImpl? = null,
        val generation: Long,
    )

    class Handler(
        val name: String,
        val onAppChanged: (packageName: String, owner: RuntimeOwner) -> Unit = { _, _ -> },
        val onAccessibilityEvent: (event: AccessibilityEvent, owner: RuntimeOwner) -> Unit = { _, _ -> },
        val onDetached: (owner: RuntimeOwner) -> Unit = {},
    )

    data class RuntimeStatus(
        val connected: Boolean = false,
        val mode: AutomatorModeOption? = null,
        val generation: Long = 0L,
        val packageName: String = "",
    )

    private val lock = Any()
    private var generation = 0L
    private var currentOwner: RuntimeOwner? = null
    private var latestAppId = appIdOf(foregroundApps.value)
    private var dispatchedGeneration = -1L
    private var dispatchedAppId = ""
    private val _statusFlow = MutableStateFlow(RuntimeStatus(packageName = latestAppId))
    val statusFlow: StateFlow<RuntimeStatus> = _statusFlow.asStateFlow()
    val featureNames: Set<String> get() = handlers.mapTo(linkedSetOf()) { it.name }

    init {
        scope.launch(Dispatchers.Default) {
            foregroundApps.collect { value ->
                val appId = appIdOf(value)
                val owner = synchronized(lock) {
                    latestAppId = appId
                    val active = currentOwner
                    if (active != null && appId.isNotEmpty()) {
                        _statusFlow.value = _statusFlow.value.copy(packageName = appId)
                    }
                    active
                }
                if (owner != null && appId.isNotEmpty()) {
                    dispatchAppIfNeeded(owner, appId)
                }
            }
        }
    }

    fun attach(runtime: A11yCommonImpl): RuntimeOwner = attach(
        identity = runtime,
        mode = runtime.mode,
        runtime = runtime,
    )

    fun attach(
        identity: Any,
        mode: AutomatorModeOption,
        runtime: A11yCommonImpl? = null,
    ): RuntimeOwner {
        val owner = synchronized(lock) {
            generation += 1
            RuntimeOwner(identity, mode, runtime, generation).also {
                currentOwner = it
                dispatchedGeneration = -1L
                dispatchedAppId = ""
                _statusFlow.value = RuntimeStatus(
                    connected = true,
                    mode = mode,
                    generation = it.generation,
                    packageName = latestAppId,
                )
            }
        }
        // Reconcile the current app immediately. This is intentionally fenced by
        // the generation so the StateFlow's initial replay cannot duplicate it.
        if (latestAppId.isNotEmpty()) {
            dispatchAppIfNeeded(owner, latestAppId)
        }
        return owner
    }

    fun detach(owner: RuntimeOwner): Boolean {
        val detached = synchronized(lock) {
            if (currentOwner !== owner) {
                false
            } else {
                currentOwner = null
                _statusFlow.value = _statusFlow.value.copy(connected = false)
                true
            }
        }
        if (detached) {
            handlers.forEach { handler ->
                runHandler(handler.name, owner) {
                    handler.onDetached(owner)
                }
            }
        }
        return detached
    }

    fun isCurrent(owner: RuntimeOwner): Boolean = synchronized(lock) {
        currentOwner === owner
    }

    fun currentOwner(): RuntimeOwner? = synchronized(lock) { currentOwner }

    fun reconcileCurrentApp(@Suppress("UNUSED_PARAMETER") reason: String = "manual") {
        val (owner, appId) = synchronized(lock) {
            dispatchedGeneration = -1L
            dispatchedAppId = ""
            currentOwner to latestAppId
        }
        if (owner != null && appId.isNotEmpty()) {
            dispatchAppIfNeeded(owner, appId)
        }
    }

    fun onAccessibilityEvent(owner: RuntimeOwner, event: AccessibilityEvent) {
        if (!isCurrent(owner)) return
        handlers.forEach { handler ->
            runHandler(handler.name, owner) {
                handler.onAccessibilityEvent(event, owner)
            }
        }
    }

    private fun dispatchAppIfNeeded(owner: RuntimeOwner, appId: String) {
        val shouldDispatch = synchronized(lock) {
            if (currentOwner !== owner || appId.isEmpty()) {
                false
            } else if (dispatchedGeneration == owner.generation && dispatchedAppId == appId) {
                false
            } else {
                dispatchedGeneration = owner.generation
                dispatchedAppId = appId
                _statusFlow.value = _statusFlow.value.copy(packageName = appId)
                true
            }
        }
        if (!shouldDispatch) return
        handlers.forEach { handler ->
            runHandler(handler.name, owner) {
                handler.onAppChanged(appId, owner)
            }
        }
    }

    private inline fun runHandler(
        handlerName: String,
        owner: RuntimeOwner,
        block: () -> Unit,
    ) {
        runCatching(block).onFailure { error ->
            onHandlerFailure(handlerName, error)
            runCatching {
                LogUtils.d(
                    "self-control runtime handler failure",
                    "mode=${owner.mode.value}",
                    "generation=${owner.generation}",
                    "feature=$handlerName",
                    error::class.java.simpleName,
                )
            }
        }
    }
}

private val selfControlRuntimeHandlers: List<SdpRuntimeFeatureCoordinator.Handler>
    get() = listOf(
        SdpRuntimeFeatureCoordinator.Handler(
            name = "focus",
            onAppChanged = { packageName, owner ->
                FocusModeEngine.onAppChanged(packageName)
            },
        ),
        SdpRuntimeFeatureCoordinator.Handler(
            name = "usage-guard",
            onAppChanged = { packageName, _ -> UsageGuardEngine.onAppChanged(packageName) },
            onDetached = { owner ->
                UsageGuardEngine.onRuntimeDisconnected()
            },
        ),
        SdpRuntimeFeatureCoordinator.Handler(
            name = "app-blocker",
            onAppChanged = { packageName, _ -> AppBlockerEngine.onAppChanged(packageName) },
        ),
        SdpRuntimeFeatureCoordinator.Handler(
            name = "url-blocker",
            onAccessibilityEvent = { event, owner ->
                UrlBlockerEngine.onAccessibilityEvent(event, owner.runtime?.ruleEngine)
            },
        ),
    )

val sdpRuntimeFeatureCoordinator: SdpRuntimeFeatureCoordinator<TopActivity> by lazy {
    SdpRuntimeFeatureCoordinator(
        foregroundApps = topActivityFlow,
        appIdOf = { it.appId },
        scope = appScope,
        handlers = selfControlRuntimeHandlers,
    )
}
