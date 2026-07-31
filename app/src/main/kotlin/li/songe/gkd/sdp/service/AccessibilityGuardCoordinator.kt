package li.songe.gkd.sdp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.notif.cancelAccessibilityGuardNotifications
import li.songe.gkd.sdp.notif.postAccessibilityGuardNotification
import li.songe.gkd.sdp.store.AccessibilityGuardSession
import li.songe.gkd.sdp.store.accessibilityGuardSessionFlow
import li.songe.gkd.sdp.store.MutableStoreStateFlow
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.AccessibilityGuardPolicy
import li.songe.gkd.sdp.util.LogUtils

/**
 * Pure reset transition used by the runtime and JVM tests.
 *
 * A reset only advances the generation when there is state to invalidate. This
 * makes repeated resets idempotent while still fencing off work scheduled for
 * a previous session.
 */
internal fun resetAccessibilityGuardSession(
    session: AccessibilityGuardSession,
): AccessibilityGuardSession {
    val hasRuntimeState = session.disabledAtEpochMs != 0L ||
        session.lastReminderIndex != -1 ||
        session.enforcementStarted ||
        session.temporaryShutdownExpected ||
        session.grantFlowUntilEpochMs != 0L
    if (!hasRuntimeState) return session
    return AccessibilityGuardSession(generation = session.generation + 1L)
}

/** Pure marker transition kept separate so its state-preserving behavior is testable. */
internal fun markTemporaryShutdownSession(
    session: AccessibilityGuardSession,
): AccessibilityGuardSession = session.copy(temporaryShutdownExpected = true)

private fun clearTemporaryShutdownSession(
    session: AccessibilityGuardSession,
): AccessibilityGuardSession = session.copy(temporaryShutdownExpected = false)

/**
 * Applies the pure session part of one coordinator reconciliation.
 *
 * Keeping this transition separate from Android side effects makes the two
 * race-sensitive rules explicit: a temporary marker is retained only while
 * the blocked app is still current, and a newly tracked disable gets a fresh
 * generation/timestamp instead of inheriting stale reminder state.
 */
internal fun transitionAccessibilityGuardSession(
    session: AccessibilityGuardSession,
    mode: AccessibilityGuardPolicy.SessionMode,
    currentAppBlocked: Boolean,
    nowEpochMs: Long,
): AccessibilityGuardSession {
    return when (mode) {
        AccessibilityGuardPolicy.SessionMode.RESET -> resetAccessibilityGuardSession(session)
        AccessibilityGuardPolicy.SessionMode.SUPPRESSED_TEMPORARY -> session
        AccessibilityGuardPolicy.SessionMode.TRACK -> {
            val markerCleared = if (session.temporaryShutdownExpected && !currentAppBlocked) {
                clearTemporaryShutdownSession(session)
            } else {
                session
            }
            if (markerCleared.disabledAtEpochMs == 0L) {
                AccessibilityGuardSession(
                    generation = markerCleared.generation + 1L,
                    disabledAtEpochMs = nowEpochMs,
                )
            } else {
                markerCleared
            }
        }
    }
}

/**
 * Pure fence used immediately before a notification or enforcement side
 * effect. A state update from an older generation must never leak a stale
 * action after the feature has been disabled or temporarily suppressed.
 */
internal fun canApplyAccessibilityGuardSideEffect(
    expectedGeneration: Long,
    currentGeneration: Long,
    mode: AccessibilityGuardPolicy.SessionMode,
    featureEnabled: Boolean,
): Boolean = expectedGeneration == currentGeneration &&
    featureEnabled &&
    mode == AccessibilityGuardPolicy.SessionMode.TRACK

/**
 * Process-local entrance for events that affect the accessibility guard.
 *
 * The coordinator will consume [wakeups] in a later task. Keeping these
 * methods limited to session state and a conflated wake-up prevents Android
 * notification/overlay side effects from racing with state reconciliation.
 * The policy keeps a pre-disable temporary marker suppressed while the
 * component still reports enabled.
 */
object AccessibilityGuardRuntime {
    const val GRANT_FLOW_TIMEOUT_MS = 5 * 60_000L

    private val _wakeups = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A conflated signal for the future coordinator to trigger reconciliation. */
    val wakeups: SharedFlow<Unit> = _wakeups.asSharedFlow()

    fun markTemporaryShutdownExpected() {
        accessibilityGuardSessionFlow.update(::markTemporaryShutdownSession)
        // The policy preserves this marker while the component still reports
        // enabled, so wake coordinator-only consumers immediately.
        wake()
    }

    fun clearTemporaryShutdownExpected() {
        accessibilityGuardSessionFlow.update(::clearTemporaryShutdownSession)
        wake()
    }

    fun beginGrantFlow(nowEpochMs: Long = System.currentTimeMillis()) {
        accessibilityGuardSessionFlow.update { session ->
            session.copy(grantFlowUntilEpochMs = nowEpochMs + GRANT_FLOW_TIMEOUT_MS)
        }
        wake()
    }

    fun onAppVisible() {
        accessibilityGuardSessionFlow.update { session ->
            session.copy(grantFlowUntilEpochMs = 0L)
        }
        wake()
    }

    fun requestReconcile() {
        wake()
    }

    fun disableAndReset() {
        accessibilityGuardSessionFlow.update(::resetAccessibilityGuardSession)
        wake()
    }

    private fun wake() {
        _wakeups.tryEmit(Unit)
    }
}

/**
 * Owns the process-local accessibility guard lifecycle while StatusService is
 * alive. Every input only wakes the single reconcile loop; all state changes
 * and Android side effects therefore pass through one mutex-protected path.
 */
class AccessibilityGuardCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val storeStateFlow: StateFlow<SettingsStore> = storeFlow,
    private val sessionStateFlow: MutableStoreStateFlow<AccessibilityGuardSession> =
        accessibilityGuardSessionFlow,
    private val a11yServiceEnabledFlow: StateFlow<Boolean>,
    private val currentAppBlockedFlow: StateFlow<Boolean>,
    private val activityVisibleCountFlow: StateFlow<Int>,
    private val runtimeWakeups: SharedFlow<Unit> = AccessibilityGuardRuntime.wakeups,
    private val overlayRunningFlow: StateFlow<Boolean> = AccessibilityGuardOverlayService.isRunning,
) {
    companion object {
        private const val APP_EXIT_DEBOUNCE_MS = 750L
    }

    private data class TimerToken(
        val generation: Long,
        val targetEpochMs: Long,
    )

    private val reconcileMutex = Mutex()
    private val wakeChannel = Channel<Unit>(Channel.CONFLATED)
    private var coordinatorJob: Job? = null
    private var timerJob: Job? = null
    private var timerToken: TimerToken? = null
    private var screenReceiverRegistered = false
    private var previousMode: AccessibilityGuardPolicy.SessionMode? = null

    /** The last visibility value seen by the coordinator, or null before its first pass. */
    private var previousAppVisible: Boolean? = null

    /** Absolute timestamp after which a hidden app may show the enforcement overlay. */
    private var overlayDeferredUntilEpochMs = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT -> wake()
            }
        }
    }

    fun start() {
        if (coordinatorJob != null) return
        registerScreenReceiver()
        coordinatorJob = scope.launch {
            // All source collectors feed one conflated channel. A noisy source
            // can therefore not create concurrent or unbounded reconciles.
            launch {
                merge(
                    storeStateFlow.map { Unit },
                    sessionStateFlow.map { Unit },
                    a11yServiceEnabledFlow.map { Unit },
                    currentAppBlockedFlow.map { Unit },
                    activityVisibleCountFlow.map { Unit },
                    overlayRunningFlow.map { Unit },
                    canDrawOverlaysState.stateFlow.map { Unit },
                ).collect { wake() }
            }
            launch { runtimeWakeups.collect { wake() } }
            while (isActive) {
                wakeChannel.receive()
                reconcileMutex.withLock {
                    reconcile(System.currentTimeMillis())
                }
            }
        }
        wake()
    }

    fun close() {
        timerJob?.cancel()
        timerJob = null
        timerToken = null
        if (overlayRunningFlow.value) {
            AccessibilityGuardOverlayService.stop(context)
        }
        coordinatorJob?.cancel()
        coordinatorJob = null
        if (screenReceiverRegistered) {
            runCatching { context.unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        wakeChannel.close()
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun wake() {
        wakeChannel.trySend(Unit)
    }

    private fun reconcile(nowEpochMs: Long) {
        val store = storeStateFlow.value
        val sessionBefore = sessionStateFlow.value
        val currentAppBlocked = currentAppBlockedFlow.value
        val a11yEnabled = a11yServiceEnabledFlow.value
        val appVisible = activityVisibleCountFlow.value > 0
        updateVisibilityDebounce(appVisible, nowEpochMs)

        val mode = AccessibilityGuardPolicy.sessionMode(
            featureEnabled = store.accessibilityGuardEnabled,
            strictChannelAvailable = META.isGkdChannel,
            useA11yMode = store.useA11y,
            a11yEnabled = a11yEnabled,
            temporaryShutdownExpected = sessionBefore.temporaryShutdownExpected,
            currentAppBlocked = currentAppBlocked,
        )
        if (previousMode != mode) {
            LogUtils.d(
                "AccessibilityGuard transition " +
                    "generation=${sessionBefore.generation} " +
                    "from=${previousMode ?: "NONE"} to=$mode " +
                    "reminder=${sessionBefore.lastReminderIndex} reason=policy",
            )
            previousMode = mode
        }

        when (mode) {
            AccessibilityGuardPolicy.SessionMode.RESET -> {
                resetAndStop(sessionBefore)
                return
            }

            AccessibilityGuardPolicy.SessionMode.SUPPRESSED_TEMPORARY -> {
                // Keep the pending marker and session intact while the blocked
                // app owns the foreground. No reminder timer or enforcement
                // overlay may survive this temporary shutdown.
                scheduleTimerAt(null)
                stopOverlayIfRunning()
                return
            }

            AccessibilityGuardPolicy.SessionMode.TRACK -> Unit
        }

        val session = transitionAccessibilityGuardSession(
            session = sessionBefore,
            mode = mode,
            currentAppBlocked = currentAppBlocked,
            nowEpochMs = nowEpochMs,
        )
        if (session != sessionBefore) {
            sessionStateFlow.value = session
            val reason = when {
                sessionBefore.temporaryShutdownExpected && !currentAppBlocked ->
                    "temporary_marker_cleared"
                sessionBefore.disabledAtEpochMs == 0L -> "track_started"
                else -> "session_mode"
            }
            logSessionTransition(sessionBefore, session, reason)
        }

        val evaluation = AccessibilityGuardPolicy.evaluate(
            disabledAtEpochMs = session.disabledAtEpochMs,
            lastReminderIndex = session.lastReminderIndex,
            enforcementStarted = session.enforcementStarted,
            nowEpochMs = nowEpochMs,
        )

        val finalCheckpoint = evaluation.startEnforcement
        val dueReminderIndex = evaluation.dueReminderIndex
            ?: if (finalCheckpoint) AccessibilityGuardPolicy.REMINDER_OFFSETS_MS.lastIndex else null

        if (dueReminderIndex != null) {
            // The flow can be stale while Settings.Secure changes. Read it
            // immediately before posting and never notify a recovered user.
            if (!sideEffectFenceOpen(session.generation)) return
            if (secureA11yServiceEnabled()) {
                resetAndStop(sessionStateFlow.value)
                return
            }
            postAccessibilityGuardNotification(dueReminderIndex)

            if (finalCheckpoint) {
                // Final notice is posted first. Re-read before persisting the
                // enforcement fence and starting any overlay service.
                if (secureA11yServiceEnabled()) {
                    resetAndStop(sessionStateFlow.value)
                    return
                }
                val beforePersist = sessionStateFlow.value
                sessionStateFlow.update { current ->
                    if (current.generation == session.generation) {
                        current.copy(
                            lastReminderIndex = dueReminderIndex,
                            enforcementStarted = true,
                        )
                    } else {
                        current
                    }
                }
                logSessionTransition(
                    beforePersist,
                    sessionStateFlow.value,
                    "final_enforcement_started",
                )
            } else {
                val beforePersist = sessionStateFlow.value
                sessionStateFlow.update { current ->
                    if (current.generation == session.generation &&
                        dueReminderIndex > current.lastReminderIndex
                    ) {
                        current.copy(lastReminderIndex = dueReminderIndex)
                    } else {
                        current
                    }
                }
                logSessionTransition(
                    beforePersist,
                    sessionStateFlow.value,
                    "reminder_${dueReminderIndex}",
                )
            }
        }

        val currentSession = sessionStateFlow.value
        reconcileOverlay(
            session = currentSession,
            expectedGeneration = currentSession.generation,
            a11yEnabled = a11yServiceEnabledFlow.value,
            appVisible = appVisible,
            nowEpochMs = nowEpochMs,
        )

        val nextWakeAt = listOfNotNull(
            evaluation.nextWakeAtEpochMs,
            currentSession.grantFlowUntilEpochMs.takeIf { it > nowEpochMs },
            overlayDeferredUntilEpochMs.takeIf {
                it > nowEpochMs && !appVisible && currentSession.enforcementStarted
            },
        ).minOrNull()
        scheduleTimerAt(nextWakeAt, currentSession.generation)
    }

    private fun updateVisibilityDebounce(appVisible: Boolean, nowEpochMs: Long) {
        val previous = previousAppVisible
        previousAppVisible = appVisible
        when {
            appVisible -> overlayDeferredUntilEpochMs = 0L
            previous == null || previous -> {
                // App-exit debounce: give the activity 750 ms to settle before
                // displaying a full-screen enforcement surface.
                overlayDeferredUntilEpochMs = nowEpochMs + APP_EXIT_DEBOUNCE_MS
            }
            overlayDeferredUntilEpochMs <= nowEpochMs -> overlayDeferredUntilEpochMs = 0L
        }
    }

    private fun secureA11yServiceEnabled(): Boolean {
        return app.getSecureA11yServices().contains(A11yService.a11yCn)
    }

    private fun sideEffectFenceOpen(expectedGeneration: Long): Boolean {
        val store = storeStateFlow.value
        val currentSession = sessionStateFlow.value
        val mode = AccessibilityGuardPolicy.sessionMode(
            featureEnabled = store.accessibilityGuardEnabled,
            strictChannelAvailable = META.isGkdChannel,
            useA11yMode = store.useA11y,
            a11yEnabled = a11yServiceEnabledFlow.value,
            temporaryShutdownExpected = currentSession.temporaryShutdownExpected,
            currentAppBlocked = currentAppBlockedFlow.value,
        )
        return canApplyAccessibilityGuardSideEffect(
            expectedGeneration = expectedGeneration,
            currentGeneration = currentSession.generation,
            mode = mode,
            featureEnabled = store.accessibilityGuardEnabled,
        )
    }

    private fun logSessionTransition(
        from: AccessibilityGuardSession,
        to: AccessibilityGuardSession,
        reason: String,
    ) {
        if (from == to) return
        LogUtils.d(
            "AccessibilityGuard state transition " +
                "generation=${to.generation} " +
                "from=${redactedState(from)} to=${redactedState(to)} " +
                "reminder=${to.lastReminderIndex} reason=$reason",
        )
    }

    private fun redactedState(session: AccessibilityGuardSession): String = when {
        session.temporaryShutdownExpected -> "SUPPRESSED"
        session.disabledAtEpochMs == 0L -> "IDLE"
        session.enforcementStarted -> "ENFORCING"
        else -> "TRACKING"
    }

    private fun resetAndStop(session: AccessibilityGuardSession) {
        val reset = resetAccessibilityGuardSession(session)
        if (reset != session) {
            sessionStateFlow.value = reset
            logSessionTransition(session, reset, "reset")
        }
        cancelAccessibilityGuardNotifications()
        stopOverlayIfRunning()
        overlayDeferredUntilEpochMs = 0L
        scheduleTimerAt(null)
    }

    private fun stopOverlayIfRunning() {
        if (overlayRunningFlow.value) {
            AccessibilityGuardOverlayService.stop(context)
        }
    }

    private fun reconcileOverlay(
        session: AccessibilityGuardSession,
        expectedGeneration: Long,
        a11yEnabled: Boolean,
        appVisible: Boolean,
        nowEpochMs: Long,
    ) {
        val shouldShow = AccessibilityGuardPolicy.shouldShowOverlay(
            AccessibilityGuardPolicy.OverlayInput(
                enforcementStarted = session.enforcementStarted,
                a11yEnabled = a11yEnabled,
                appVisible = appVisible,
                grantFlowUntilEpochMs = session.grantFlowUntilEpochMs,
                nowEpochMs = nowEpochMs,
                canDrawOverlays = canDrawOverlaysState.updateAndGet(),
                screenInteractive = app.powerManager.isInteractive,
                keyguardLocked = app.keyguardManager.isKeyguardLocked,
            )
        ) && nowEpochMs >= overlayDeferredUntilEpochMs

        if (appVisible || !shouldShow) {
            stopOverlayIfRunning()
            return
        }
        if (overlayRunningFlow.value) return

        if (!sideEffectFenceOpen(expectedGeneration)) return

        // Check Settings.Secure again immediately before the enforcement
        // side effect. A recovered component always wins over a stale flow.
        if (secureA11yServiceEnabled()) {
            resetAndStop(sessionStateFlow.value)
            return
        }
        AccessibilityGuardOverlayService.start(context)
    }

    private fun scheduleTimerAt(
        targetEpochMs: Long?,
        generation: Long = sessionStateFlow.value.generation,
    ) {
        if (targetEpochMs == null) {
            timerJob?.cancel()
            timerJob = null
            timerToken = null
            return
        }
        val token = TimerToken(generation = generation, targetEpochMs = targetEpochMs)
        if (timerToken == token && timerJob?.isActive == true) return

        timerJob?.cancel()
        timerToken = token
        timerJob = scope.launch {
            delay((targetEpochMs - System.currentTimeMillis()).coerceAtLeast(0L))
            if (timerToken == token) {
                timerToken = null
                timerJob = null
                wake()
            }
        }
    }
}
