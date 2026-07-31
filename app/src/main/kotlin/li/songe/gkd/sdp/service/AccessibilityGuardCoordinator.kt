package li.songe.gkd.sdp.service

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.store.AccessibilityGuardSession
import li.songe.gkd.sdp.store.accessibilityGuardSessionFlow

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
