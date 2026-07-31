package li.songe.gkd.sdp.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.notif.cancelAccessibilityGuardNotifications
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.AccessibilityGuardControlPolicy
import li.songe.gkd.sdp.util.AutoReenableDisableGuard
import li.songe.gkd.sdp.util.LogUtils

internal fun isAccessibilityGuardRequestCurrent(
    expectedRequestId: Long,
    currentRequestId: Long,
    desired: Boolean,
): Boolean = expectedRequestId == currentRequestId && desired

internal fun shouldInvalidateAccessibilityGuardActivation(
    activationInFlight: Boolean,
): Boolean = activationInFlight

/**
 * Owns all user-facing accessibility guard activation/deactivation writes.
 *
 * Compose screens only render the result. Keeping the request fence here
 * prevents a late permission callback from reviving a request that was
 * superseded while the user was still in a permission dialog.
 */
object AccessibilityGuardController {
    sealed interface EnableResult {
        data object Enabled : EnableResult
        data object AlreadyEnabled : EnableResult
        data object UnavailableChannel : EnableResult
        data object RequiresA11yMode : EnableResult
        data object Superseded : EnableResult
    }

    sealed interface DisableResult {
        data object Disabled : DisableResult
        data object NoChange : DisableResult
        data object BlockedByLock : DisableResult
        data class BlockedByQuota(val limit: Int) : DisableResult
    }

    private val requestLock = Any()
    private val disableMutex = Mutex()
    private var requestSequence = 0L
    private var desired = false
    private var activationInFlight = false
    private var startedStatusForActivation = false

    suspend fun enable(context: MainActivity): EnableResult {
        val requestId = beginEnableRequest()
        val decision = AccessibilityGuardControlPolicy.enableDecision(
            strictChannelAvailable = META.isGkdChannel,
            useA11yMode = storeFlow.value.useA11y,
            // This value is intentionally not a gate. The guard is designed
            // to be enabled while the component is already disabled.
            accessibilityComponentEnabled = false,
        )
        when (decision) {
            AccessibilityGuardControlPolicy.EnableDecision.UNAVAILABLE_CHANNEL -> {
                cleanupActivation(requestId)
                return EnableResult.UnavailableChannel
            }

            AccessibilityGuardControlPolicy.EnableDecision.REQUIRE_A11Y_MODE -> {
                cleanupActivation(requestId)
                return EnableResult.RequiresA11yMode
            }

            AccessibilityGuardControlPolicy.EnableDecision.ALLOW -> Unit
        }

        if (storeFlow.value.accessibilityGuardEnabled) {
            cleanupActivation(requestId)
            return EnableResult.AlreadyEnabled
        }

        try {
            requiredPermission(context, notificationState)
            requiredPermission(context, foregroundServiceSpecialUseState)
            requiredPermission(context, canDrawOverlaysState)
        } catch (e: CancellationException) {
            cleanupActivation(requestId)
            throw e
        }

        if (!isCurrentRequest(requestId)) {
            cleanupActivation(requestId)
            return EnableResult.Superseded
        }

        if (!storeFlow.value.enableStatusService) {
            synchronized(requestLock) {
                if (requestId == requestSequence) {
                    startedStatusForActivation = true
                }
            }
        }
        try {
            if (!StatusService.requestStart(context)) {
                cleanupActivation(requestId)
                return EnableResult.Superseded
            }
        } catch (e: CancellationException) {
            cleanupActivation(requestId)
            throw e
        }

        if (!isCurrentRequest(requestId)) {
            cleanupActivation(requestId)
            return EnableResult.Superseded
        }

        // Do not reintroduce the old accessibility-running prerequisite here.
        // These checks only confirm the permissions required to keep the
        // coordinator and overlay path alive.
        if (!storeFlow.value.useA11y ||
            !notificationState.updateAndGet() ||
            !foregroundServiceSpecialUseState.updateAndGet() ||
            !canDrawOverlaysState.updateAndGet()
        ) {
            cleanupActivation(requestId)
            return EnableResult.Superseded
        }

        synchronized(requestLock) {
            if (requestId != requestSequence || !desired) {
                cleanupActivation(requestId)
                return EnableResult.Superseded
            }
            storeFlow.update {
                it.copy(
                    accessibilityGuardEnabled = true,
                    accessibilityGuardAutoReenableArmed = true,
                )
            }
            // StatusService is now intentionally kept alive by the guard. The
            // activation-only ownership bit is no longer needed after a
            // successful commit.
            startedStatusForActivation = false
            activationInFlight = false
        }
        AccessibilityGuardRuntime.requestReconcile()
        return EnableResult.Enabled
    }

    suspend fun disable(nowEpochMs: Long = System.currentTimeMillis()): DisableResult =
        disableMutex.withLock {
            // A permission launcher may still be suspended in enable(). Treat a
            // user disable as a cancellation of that request even though the
            // setting has not committed to true yet.
            if (invalidatePendingActivation()) {
                AccessibilityGuardRuntime.disableAndReset()
                cancelAccessibilityGuardNotifications()
                AccessibilityGuardOverlayService.stop()
                stopActivationOwnedStatusIfNeeded()
                return@withLock DisableResult.NoChange
            }

            val currentSettings = storeFlow.value
            if (!currentSettings.accessibilityGuardEnabled) {
                return@withLock DisableResult.NoChange
            }
            val decision = AccessibilityGuardControlPolicy.disableDecision(
                currentlyEnabled = currentSettings.accessibilityGuardEnabled,
                anyActiveLock = hasAnyActiveLock(nowEpochMs),
                quotaAllowed = true,
            )
            when (decision) {
                AccessibilityGuardControlPolicy.DisableDecision.NO_CHANGE ->
                    return@withLock DisableResult.NoChange

                AccessibilityGuardControlPolicy.DisableDecision.BLOCKED_BY_LOCK ->
                    return@withLock DisableResult.BlockedByLock

                AccessibilityGuardControlPolicy.DisableDecision.ALLOW,
                AccessibilityGuardControlPolicy.DisableDecision.BLOCKED_BY_QUOTA -> Unit
            }

            val quota = AutoReenableDisableGuard.tryConsumeForDisable(nowEpochMs)
            val finalDecision = AccessibilityGuardControlPolicy.disableDecision(
                currentlyEnabled = storeFlow.value.accessibilityGuardEnabled,
                anyActiveLock = false,
                quotaAllowed = quota.allowed,
            )
            if (finalDecision == AccessibilityGuardControlPolicy.DisableDecision.BLOCKED_BY_QUOTA) {
                return@withLock DisableResult.BlockedByQuota(quota.limit)
            }
            if (finalDecision != AccessibilityGuardControlPolicy.DisableDecision.ALLOW) {
                return@withLock DisableResult.NoChange
            }

            synchronized(requestLock) {
                requestSequence++
                desired = false
                activationInFlight = false
                storeFlow.update {
                    it.copy(
                        accessibilityGuardEnabled = false,
                        // Enrollment deliberately survives a temporary disable;
                        // AutoReenableEnforcer will restore the feature later.
                        accessibilityGuardAutoReenableArmed = true,
                    )
                }
            }
            AccessibilityGuardRuntime.disableAndReset()
            cancelAccessibilityGuardNotifications()
            AccessibilityGuardOverlayService.stop()
            stopActivationOwnedStatusIfNeeded()
            DisableResult.Disabled
        }

    /** Called by AutoReenableEnforcer without a user permission dialog. */
    fun autoReenableIfEligible(): Int {
        synchronized(requestLock) {
            val settings = storeFlow.value
            if (!AccessibilityGuardControlPolicy.shouldAutoReenable(
                    strictChannelAvailable = META.isGkdChannel,
                    useA11yMode = settings.useA11y,
                    armed = settings.accessibilityGuardAutoReenableArmed,
                    currentlyEnabled = settings.accessibilityGuardEnabled,
                )
            ) {
                if (settings.accessibilityGuardEnabled &&
                    !settings.accessibilityGuardAutoReenableArmed
                ) {
                    storeFlow.update {
                        it.copy(accessibilityGuardAutoReenableArmed = true)
                    }
                }
                return 0
            }
            requestSequence++
            desired = true
            storeFlow.update { it.copy(accessibilityGuardEnabled = true) }
        }
        AccessibilityGuardRuntime.requestReconcile()
        StatusService.autoStart()
        return 1
    }

    private fun beginEnableRequest(): Long = synchronized(requestLock) {
        requestSequence++
        desired = true
        activationInFlight = true
        requestSequence
    }

    private fun isCurrentRequest(requestId: Long): Boolean = synchronized(requestLock) {
        isAccessibilityGuardRequestCurrent(requestId, requestSequence, desired)
    }

    private fun cleanupActivation(requestId: Long) {
        val shouldStop = synchronized(requestLock) {
            if (requestId == requestSequence) {
                desired = false
                activationInFlight = false
            }
            if (!desired && startedStatusForActivation) {
                startedStatusForActivation = false
                true
            } else {
                false
            }
        }
        if (shouldStop) {
            StatusService.stop()
            storeFlow.update { it.copy(enableStatusService = false) }
        }
    }

    /**
     * Invalidates an enable request that is waiting on user permission or
     * service startup. The request fence is advanced before any suspend work
     * so a late callback can only observe a superseded request.
     */
    private fun invalidatePendingActivation(): Boolean {
        var invalidated = false
        val shouldStop = synchronized(requestLock) {
            if (!shouldInvalidateAccessibilityGuardActivation(activationInFlight)) {
                return@synchronized false
            }
            invalidated = true
            requestSequence++
            desired = false
            activationInFlight = false
            if (startedStatusForActivation) {
                startedStatusForActivation = false
                true
            } else {
                false
            }
        }
        if (shouldStop) {
            StatusService.stop()
            storeFlow.update { it.copy(enableStatusService = false) }
        }
        return invalidated
    }

    private fun stopActivationOwnedStatusIfNeeded() {
        val shouldStop = synchronized(requestLock) {
            if (startedStatusForActivation) {
                startedStatusForActivation = false
                true
            } else {
                false
            }
        }
        if (shouldStop) {
            StatusService.stop()
            storeFlow.update { it.copy(enableStatusService = false) }
        }
    }

    private suspend fun hasAnyActiveLock(nowEpochMs: Long): Boolean {
        return try {
            DbSet.digitalSelfDisciplineLockDao.hasAnyActiveLock(nowEpochMs)
        } catch (error: Throwable) {
            // A failed lock read must fail closed: a guard must not become
            // disableable merely because one DAO query was unavailable.
            LogUtils.d("AccessibilityGuard lock lookup failed", error)
            true
        }
    }
}
