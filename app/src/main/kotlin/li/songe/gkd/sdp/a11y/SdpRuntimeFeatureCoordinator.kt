package li.songe.gkd.sdp.a11y

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        val switching: Boolean = false,
        val lastDecision: RuntimeDecision? = null,
    )

    data class RuntimeDecision(
        val feature: String,
        val packageName: String,
        val decision: String,
        val atEpochMs: Long,
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
                    if (appId.isEmpty()) {
                        dispatchedGeneration = -1L
                        dispatchedAppId = ""
                        if (active != null) {
                            _statusFlow.value = _statusFlow.value.copy(packageName = "")
                        }
                    }
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
        val (owner, handoff, currentAppId) = synchronized(lock) {
            val hadOwner = currentOwner != null
            generation += 1
            val newOwner = RuntimeOwner(identity, mode, runtime, generation).also {
                currentOwner = it
                dispatchedGeneration = -1L
                dispatchedAppId = ""
                _statusFlow.value = RuntimeStatus(
                    connected = true,
                    mode = mode,
                    generation = it.generation,
                    packageName = latestAppId,
                    switching = hadOwner,
                    lastDecision = _statusFlow.value.lastDecision,
                )
            }
            Triple(newOwner, hadOwner, latestAppId)
        }
        // Reconcile the current app immediately. This is intentionally fenced by
        // the generation so the StateFlow's initial replay cannot duplicate it.
        if (currentAppId.isNotEmpty()) {
            dispatchAppIfNeeded(owner, currentAppId)
        }
        if (handoff) {
            scope.launch {
                delay(1_000L)
                synchronized(lock) {
                    if (currentOwner === owner) {
                        _statusFlow.value = _statusFlow.value.copy(switching = false)
                    }
                }
            }
        }
        return owner
    }

    fun detach(owner: RuntimeOwner): Boolean {
        val detached = synchronized(lock) {
            if (currentOwner !== owner) {
                false
            } else {
                currentOwner = null
                _statusFlow.value = _statusFlow.value.copy(connected = false, switching = false)
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

    fun recordDecision(
        owner: RuntimeOwner?,
        feature: String,
        packageName: String,
        decision: String,
    ) {
        synchronized(lock) {
            if (owner != null && currentOwner !== owner) return
            if (currentOwner == null) return
            _statusFlow.value = _statusFlow.value.copy(
                lastDecision = RuntimeDecision(
                    feature = feature,
                    packageName = packageName,
                    decision = decision,
                    atEpochMs = System.currentTimeMillis(),
                )
            )
        }
    }

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

    fun invalidateCurrentApp(@Suppress("UNUSED_PARAMETER") reason: String = "retry") {
        synchronized(lock) {
            dispatchedGeneration = -1L
            dispatchedAppId = ""
        }
    }

    fun onAccessibilityEvent(owner: RuntimeOwner, event: AccessibilityEvent?) {
        if (event == null) return
        if (!isCurrent(owner)) return
        for (handler in handlers) {
            if (!isCurrent(owner)) return
            runHandler(handler.name, owner) {
                handler.onAccessibilityEvent(event, owner)
            }
        }
    }

    private fun dispatchAppIfNeeded(owner: RuntimeOwner, appId: String) {
        val shouldDispatch = synchronized(lock) {
            if (currentOwner !== owner || latestAppId != appId || appId.isEmpty()) {
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
        for (handler in handlers) {
            if (!isCurrent(owner)) return
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
            runCatching { onHandlerFailure(handlerName, error) }
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

val selfControlRuntimeFeatureNames: Set<String> =
    setOf("focus", "usage-guard", "app-blocker", "url-blocker")

private val selfControlRuntimeHandlers: List<SdpRuntimeFeatureCoordinator.Handler>
    get() = listOf(
        SdpRuntimeFeatureCoordinator.Handler(
            name = "focus",
            onAppChanged = { packageName, owner ->
                FocusModeEngine.onAppChanged(packageName, owner)
            },
        ),
        SdpRuntimeFeatureCoordinator.Handler(
            name = "usage-guard",
            onAppChanged = { packageName, owner -> UsageGuardEngine.onAppChanged(packageName, owner) },
            onDetached = { owner ->
                UsageGuardEngine.onRuntimeDisconnected()
            },
        ),
        SdpRuntimeFeatureCoordinator.Handler(
            name = "app-blocker",
            onAppChanged = { packageName, owner -> AppBlockerEngine.onAppChanged(packageName, owner) },
        ),
        SdpRuntimeFeatureCoordinator.Handler(
            name = "url-blocker",
            onAccessibilityEvent = { event, owner ->
                UrlBlockerEngine.onAccessibilityEvent(event, owner.runtime?.ruleEngine, owner)
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
