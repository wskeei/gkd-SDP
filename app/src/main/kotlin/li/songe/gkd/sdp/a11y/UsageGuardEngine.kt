package li.songe.gkd.sdp.a11y

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.UsageGuardCountdownOverlayService
import li.songe.gkd.sdp.service.UsageGuardRequestOverlayService
import li.songe.gkd.sdp.service.UsageGuardTimeoutOverlayService
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayPolicy
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.appInfoMapFlow
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.systemUiAppId
import li.songe.gkd.sdp.widget.UsageGuardReviewWidget
import java.util.concurrent.atomic.AtomicLong

object UsageGuardEngine {
    private val stateMutex = Mutex()

    private var lastProtectedAppId: String? = null
    private val blockingOverlayState = UsageGuardBlockingOverlayState()
    private var countdownOverlayAppId: String? = null
    private var countdownOverlayRecordId: Long? = null
    private var countdownOverlayExpiresAt: Long = 0L

    private var expiryWatchAppId: String? = null
    private var expiryWatchRecordId: Long? = null
    private var expiryWatchExpiresAt: Long = 0L
    private var expiryWatchJob: Job? = null
    private val appChangeToken = AtomicLong()

    private val configurationReconciler = UsageGuardConfigurationReconciler { reason ->
        sdpRuntimeFeatureCoordinator.reconcileCurrentApp(reason)
    }

    val appProfilesFlow = DbSet.usageGuardAppProfileDao.queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    val tagsFlow = DbSet.usageGuardTagDao.queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    init {
        appScope.launch(Dispatchers.IO) {
            combine(
                storeFlow,
                DbSet.usageGuardAppProfileDao.queryAll(),
            ) { settings, profiles ->
                UsageGuardRuntimeConfiguration.from(settings, profiles)
            }.collect(configurationReconciler::accept)
        }
    }

    fun onAppChanged(
        packageName: String,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
    ) {
        val token = appChangeToken.incrementAndGet()
        appScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                if (!isCurrentRequest(packageName, owner, token)) return@withLock
                try {
                    handleAppChanged(packageName, owner, token)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    LogUtils.d("usage guard app reconciliation failed", error::class.java.simpleName)
                }
            }
        }
    }

    fun onRequestOverlayStopped(appId: String?) {
        appScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                blockingOverlayState.clearRequest(appId)
                val owner = sdpRuntimeFeatureCoordinator.currentOwner() ?: return@withLock
                val token = appChangeToken.get()
                if (appId != null && !isCurrentRequest(appId, owner, token)) return@withLock
                if (appId == null) return@withLock
                if (topActivityFlow.value.appId != appId) {
                    stopCountdownOverlay(appId = appId, owner = owner, token = token)
                    return@withLock
                }
                val record = DbSet.usageGuardRecordDao.getActiveRecord(appId) ?: run {
                    stopCountdownOverlay(appId = appId, owner = owner, token = token)
                    return@withLock
                }
                scheduleExpiryWatch(record, owner, token)
                syncCountdownOverlay(
                    activeRecord = record,
                    foregroundAppId = appId,
                    owner = owner,
                    token = token,
                )
            }
        }
    }

    fun onTimeoutOverlayStopped(appId: String?) {
        appScope.launch {
            stateMutex.withLock {
                blockingOverlayState.clearTimeout(appId)
                if (sdpRuntimeFeatureCoordinator.currentOwner() == null) return@withLock
            }
        }
    }

    fun onCountdownOverlayStopped(appId: String?) {
        appScope.launch {
            stateMutex.withLock {
                if (appId == null || countdownOverlayAppId == appId) {
                    clearCountdownOverlayState()
                }
            }
        }
    }

    fun onRuntimeDisconnected() {
        appChangeToken.incrementAndGet()
        appScope.launch {
            stateMutex.withLock {
                // A detached handler can finish asynchronously after a new
                // runtime has already attached. Never tear down state owned by
                // that new runtime.
                if (sdpRuntimeFeatureCoordinator.currentOwner() != null) return@withLock
                lastProtectedAppId = null
                cancelExpiryWatch()
                blockingOverlayState.clearAll()
                stopCountdownOverlay()
                // The service may still be alive even if its bookkeeping was
                // lost during a previous teardown, so stop it unconditionally
                // as part of the runtime-disconnect boundary.
                selfControlOverlayLauncher.stop(
                    Intent(app, UsageGuardCountdownOverlayService::class.java),
                )
                selfControlOverlayLauncher.stop(
                    Intent(app, UsageGuardRequestOverlayService::class.java),
                )
                selfControlOverlayLauncher.stop(
                    Intent(app, UsageGuardTimeoutOverlayService::class.java),
                )
            }
        }
    }

    fun onOverlayMountFailed(kind: String, appId: String?) {
        appScope.launch {
            stateMutex.withLock {
                when (kind) {
                    "request" -> blockingOverlayState.clearRequest(appId)
                    "timeout" -> blockingOverlayState.clearTimeout(appId)
                    "countdown" -> if (countdownOverlayAppId == appId) clearCountdownOverlayState()
                }
            }
        }
        sdpRuntimeFeatureCoordinator.invalidateCurrentApp("usage-guard-overlay-mount-failed")
        LogUtils.d("usage guard overlay mount failed", "kind=$kind")
    }

    fun onRequestGranted(appId: String) {
        appScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                blockingOverlayState.clearRequest(appId)
                val owner = sdpRuntimeFeatureCoordinator.currentOwner() ?: return@withLock
                val token = appChangeToken.get()
                if (!isCurrentRequest(appId, owner, token)) return@withLock
                lastProtectedAppId = appId
                val record = DbSet.usageGuardRecordDao.getActiveRecord(appId) ?: run {
                    stopCountdownOverlay(appId = appId, owner = owner, token = token)
                    return@withLock
                }
                scheduleExpiryWatch(record, owner, token)
                if (!isCurrentRequest(appId, owner, token)) return@withLock
                if (topActivityFlow.value.appId != appId) {
                    stopCountdownOverlay(appId = appId, owner = owner, token = token)
                }
            }
        }
    }

    fun markRecordHomeButton(recordId: Long) {
        if (recordId <= 0L) return
        appScope.launch(Dispatchers.IO) {
            DbSet.usageGuardRecordDao.updateEndReason(
                id = recordId,
                endReason = UsageGuardRecord.END_REASON_HOME_BUTTON,
            )
            UsageGuardReviewWidget.refreshAll(app)
        }
    }

    fun terminateActiveUsage(appId: String, recordId: Long) {
        if (appId.isBlank() || recordId <= 0L) return
        appScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                val activeRecord = DbSet.usageGuardRecordDao.getActiveRecord(appId) ?: return@withLock
                if (activeRecord.id != recordId) return@withLock

                val now = System.currentTimeMillis()
                DbSet.usageGuardRecordDao.closeRecord(
                    id = activeRecord.id,
                    endedAt = now,
                    endReason = UsageGuardRecord.END_REASON_USER_TERMINATED,
                )
                UsageGuardReviewWidget.refreshAll(app)
                cancelExpiryWatch(appId)
                stopCountdownOverlay(appId = appId)
                lastProtectedAppId = null
                A11yRuleEngine.performActionHome()
            }
        }
    }

    private suspend fun handleAppChanged(
        packageName: String,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner?,
        token: Long,
    ) {
        if (!isCurrentRequest(packageName, owner, token)) return
        closePreviousSessionIfNeeded(packageName, owner, token)
        if (!isCurrentRequest(packageName, owner, token)) return

        if (shouldSkipApp(packageName)) {
            sdpRuntimeFeatureCoordinator.recordDecision(owner, "usage-guard", packageName, "ignored_system_app")
            stopCountdownOverlay(owner = owner, token = token)
            lastProtectedAppId = null
            return
        }

        val settings = storeFlow.value
        if (FocusModeEngine.isActiveFlow.value &&
            !FocusModeEngine.currentWhitelistFlow.value.contains(packageName)
        ) {
            sdpRuntimeFeatureCoordinator.recordDecision(owner, "usage-guard", packageName, "focus_priority")
            stopCountdownOverlay(owner = owner, token = token)
            lastProtectedAppId = null
            return
        }
        if (AppBlockerEngine.shouldBlock(packageName).first) {
            sdpRuntimeFeatureCoordinator.recordDecision(owner, "usage-guard", packageName, "app_blocker_priority")
            stopCountdownOverlay(owner = owner, token = token)
            lastProtectedAppId = null
            return
        }
        val profile = DbSet.usageGuardAppProfileDao.getByAppId(packageName)
        if (!isCurrentRequest(packageName, owner, token)) return
        val shouldProtect = UsageGuardPolicy.shouldProtectApp(
            enabled = settings.usageGuardEnabled,
            scopeMode = settings.usageGuardScopeMode,
            appProfile = profile?.toSnapshot(),
        )
        if (!shouldProtect) {
            sdpRuntimeFeatureCoordinator.recordDecision(owner, "usage-guard", packageName, "outside_scope")
            stopCountdownOverlay(owner = owner, token = token)
            lastProtectedAppId = null
            return
        }

        lastProtectedAppId = packageName
        if (blockingOverlayState.hasBlockingOverlay) {
            sdpRuntimeFeatureCoordinator.recordDecision(
                owner,
                "usage-guard",
                packageName,
                "blocking_overlay_${blockingOverlayState.activeKind ?: "unknown"}",
            )
            stopCountdownOverlay(appId = packageName, owner = owner, token = token)
            return
        }

        val activeRecord = DbSet.usageGuardRecordDao.getActiveRecord(packageName)
        if (!isCurrentRequest(packageName, owner, token)) return
        val now = System.currentTimeMillis()
        if (activeRecord == null) {
            cancelExpiryWatch(packageName)
            if (!isCurrentRequest(packageName, owner, token)) return
            val result = showRequestOverlay(
                packageName,
                profile,
                settings.usageGuardMinReasonLength,
                owner,
                token,
            )
            sdpRuntimeFeatureCoordinator.recordDecision(
                owner,
                "usage-guard",
                packageName,
                "request_${result::class.simpleName ?: "unknown"}",
            )
            if (result == OverlayLaunchResult.Accepted && isCurrentRequest(packageName, owner, token)) {
                blockingOverlayState.markRequestStarted(packageName)
            }
            return
        }

        if (activeRecord.expiresAt <= now) {
            cancelExpiryWatch(packageName)
            if (!isCurrentRequest(packageName, owner, token)) return
            DbSet.usageGuardRecordDao.closeRecord(
                id = activeRecord.id,
                endedAt = now,
                endReason = UsageGuardRecord.END_REASON_EXPIRED,
            )
            UsageGuardReviewWidget.refreshAll(app)
            if (!isCurrentRequest(packageName, owner, token)) return
            val result = showTimeoutOverlay(
                packageName,
                activeRecord.id,
                activeRecord.reasonText,
                owner,
                token,
            )
            sdpRuntimeFeatureCoordinator.recordDecision(
                owner,
                "usage-guard",
                packageName,
                "timeout_${result::class.simpleName ?: "unknown"}",
            )
            if (result == OverlayLaunchResult.Accepted && isCurrentRequest(packageName, owner, token)) {
                blockingOverlayState.markTimeoutStarted(packageName)
            }
            return
        }

        scheduleExpiryWatch(activeRecord, owner, token)
        if (!isCurrentRequest(packageName, owner, token)) return
        sdpRuntimeFeatureCoordinator.recordDecision(owner, "usage-guard", packageName, "countdown")
        syncCountdownOverlay(
            activeRecord = activeRecord,
            foregroundAppId = packageName,
            now = now,
            owner = owner,
            token = token,
        )
    }

    private suspend fun closePreviousSessionIfNeeded(
        nextAppId: String,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner?,
        token: Long,
    ) {
        val previousAppId = lastProtectedAppId ?: return
        if (previousAppId == nextAppId) return
        if (!isCurrentRequest(nextAppId, owner, token)) return

        stopCountdownOverlay(appId = previousAppId, owner = owner, token = token)
        cancelExpiryWatch(previousAppId)

        val active = DbSet.usageGuardRecordDao.getActiveRecord(previousAppId) ?: return
        if (!isCurrentRequest(nextAppId, owner, token)) return
        if (active.grantMode != UsageGuardPolicy.GRANT_MODE_STRICT) return
        if (!isCurrentRequest(nextAppId, owner, token)) return

        DbSet.usageGuardRecordDao.closeRecord(
            id = active.id,
            endedAt = System.currentTimeMillis(),
            endReason = UsageGuardRecord.END_REASON_LEFT_APP,
        )
        UsageGuardReviewWidget.refreshAll(app)
    }

    private fun shouldSkipApp(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        return packageName == META.appId ||
            packageName == launcherAppId ||
            packageName == imeAppId ||
            packageName == systemUiAppId
    }

    private fun scheduleExpiryWatch(
        record: UsageGuardRecord,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
        token: Long = appChangeToken.get(),
    ) {
        if (!isCurrentRequest(record.appId, owner, token)) return
        if (topActivityFlow.value.appId != record.appId) {
            stopCountdownOverlay(appId = record.appId, owner = owner, token = token)
            cancelExpiryWatch(record.appId)
            return
        }
        if (
            expiryWatchAppId == record.appId &&
            expiryWatchRecordId == record.id &&
            expiryWatchExpiresAt == record.expiresAt
        ) {
            return
        }

        cancelExpiryWatch()
        expiryWatchAppId = record.appId
        expiryWatchRecordId = record.id
        expiryWatchExpiresAt = record.expiresAt
        expiryWatchJob = appScope.launch(Dispatchers.IO) {
            val delayMs = (record.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(delayMs)
            stateMutex.withLock {
                if (!isCurrentRequest(record.appId, owner, token)) return@withLock
                if (topActivityFlow.value.appId != record.appId) return@withLock
                if (blockingOverlayState.timeoutAppId == record.appId) return@withLock

                val activeRecord = DbSet.usageGuardRecordDao.getActiveRecord(record.appId) ?: return@withLock
                if (activeRecord.id != record.id) return@withLock
                if (activeRecord.expiresAt > System.currentTimeMillis()) return@withLock
                if (!isCurrentRequest(record.appId, owner, token)) return@withLock

                DbSet.usageGuardRecordDao.closeRecord(
                    id = activeRecord.id,
                    endedAt = System.currentTimeMillis(),
                    endReason = UsageGuardRecord.END_REASON_EXPIRED,
                )
                UsageGuardReviewWidget.refreshAll(app)

                stopCountdownOverlay(appId = record.appId, owner = owner, token = token)
                cancelExpiryWatch(record.appId)
                if (!isCurrentRequest(record.appId, owner, token)) return@withLock
                val result = showTimeoutOverlay(
                    record.appId,
                    activeRecord.id,
                    activeRecord.reasonText,
                    owner,
                    token,
                )
                if (result == OverlayLaunchResult.Accepted && isCurrentRequest(record.appId, owner, token)) {
                    blockingOverlayState.markTimeoutStarted(record.appId)
                }
            }
        }
    }

    private fun cancelExpiryWatch(appId: String? = null) {
        if (appId != null && expiryWatchAppId != appId) return
        expiryWatchJob?.cancel()
        expiryWatchJob = null
        expiryWatchAppId = null
        expiryWatchRecordId = null
        expiryWatchExpiresAt = 0L
    }

    private fun isCurrentRequest(
        packageName: String,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner?,
        token: Long,
    ): Boolean {
        if (appChangeToken.get() != token) return false
        if (owner != null && !sdpRuntimeFeatureCoordinator.isCurrent(owner)) return false
        if (sdpRuntimeFeatureCoordinator.currentOwner() == null) return false
        return topActivityFlow.value.appId == packageName
    }

    private fun syncCountdownOverlay(
        activeRecord: UsageGuardRecord?,
        foregroundAppId: String,
        now: Long = System.currentTimeMillis(),
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
        token: Long = appChangeToken.get(),
    ) {
        if (!isCurrentRequest(foregroundAppId, owner, token)) return
        val record = activeRecord
        if (
            record == null ||
            !UsageGuardCountdownOverlayPolicy.shouldDisplay(
                record,
                foregroundAppId,
                blockingOverlayState.requestAppId,
                blockingOverlayState.timeoutAppId,
                now,
            )
        ) {
            stopCountdownOverlay(
                appId = activeRecord?.appId ?: foregroundAppId,
                owner = owner,
                token = token,
            )
            return
        }
        showCountdownOverlay(record, owner, token)
    }

    private fun showCountdownOverlay(
        record: UsageGuardRecord,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner?,
        token: Long,
    ) {
        if (!isCurrentRequest(record.appId, owner, token)) return
        if (
            countdownOverlayAppId == record.appId &&
            countdownOverlayRecordId == record.id &&
            countdownOverlayExpiresAt == record.expiresAt
        ) {
            return
        }

        stopCountdownOverlay(owner = owner, token = token)
        val result = selfControlOverlayLauncher.launch(Intent(app, UsageGuardCountdownOverlayService::class.java).apply {
            putExtra("appId", record.appId)
            putExtra("recordId", record.id)
            putExtra("expiresAt", record.expiresAt)
            putExtra(
                UsageGuardCountdownOverlayService.EXTRA_REASON_TEXT,
                record.reasonText,
            )
        })
        if (result == OverlayLaunchResult.Accepted && isCurrentRequest(record.appId, owner, token)) {
            countdownOverlayAppId = record.appId
            countdownOverlayRecordId = record.id
            countdownOverlayExpiresAt = record.expiresAt
        }
    }

    private fun stopCountdownOverlay(
        appId: String? = null,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
        token: Long = appChangeToken.get(),
    ) {
        if (owner != null && !isCurrentRequest(appId ?: topActivityFlow.value.appId, owner, token)) return
        if (appId != null && countdownOverlayAppId != appId) return
        val shouldStop = countdownOverlayAppId != null
        clearCountdownOverlayState()
        if (shouldStop) {
            selfControlOverlayLauncher.stop(Intent(app, UsageGuardCountdownOverlayService::class.java))
        }
    }

    private fun showRequestOverlay(
        appId: String,
        profile: UsageGuardAppProfile?,
        minReasonLength: Int,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
        token: Long = appChangeToken.get(),
    ): OverlayLaunchResult {
        stopCountdownOverlay(appId = appId, owner = owner, token = token)
        val appName = appInfoMapFlow.value[appId]?.name ?: appId
        val grantMode = profile?.grantMode ?: storeFlow.value.usageGuardDefaultGrantMode
        return selfControlOverlayLauncher.launch(Intent(app, UsageGuardRequestOverlayService::class.java).apply {
            putExtra("appId", appId)
            putExtra("appName", appName)
            putExtra("grantMode", grantMode)
            putExtra("minReasonLength", minReasonLength)
        })
    }

    private fun showTimeoutOverlay(
        appId: String,
        recordId: Long,
        reasonText: String,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
        token: Long = appChangeToken.get(),
    ): OverlayLaunchResult {
        stopCountdownOverlay(appId = appId, owner = owner, token = token)
        return selfControlOverlayLauncher.launch(Intent(app, UsageGuardTimeoutOverlayService::class.java).apply {
            putExtra("appId", appId)
            putExtra("recordId", recordId)
            putExtra("reasonText", reasonText)
        })
    }

    private fun clearCountdownOverlayState() {
        countdownOverlayAppId = null
        countdownOverlayRecordId = null
        countdownOverlayExpiresAt = 0L
    }

    private fun UsageGuardAppProfile.toSnapshot(): UsageGuardPolicy.AppProfileSnapshot {
        return UsageGuardPolicy.AppProfileSnapshot(
            appId = appId,
            selectedTarget = selectedTarget,
            globalWhitelist = globalWhitelist,
            grantMode = grantMode,
        )
    }
}
