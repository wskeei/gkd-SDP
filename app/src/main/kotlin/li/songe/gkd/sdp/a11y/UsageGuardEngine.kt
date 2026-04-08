package li.songe.gkd.sdp.a11y

import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.UsageGuardRequestOverlayService
import li.songe.gkd.sdp.service.UsageGuardTimeoutOverlayService
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.appInfoMapFlow
import li.songe.gkd.sdp.util.systemUiAppId

object UsageGuardEngine {
    private val stateMutex = Mutex()

    private var lastProtectedAppId: String? = null
    private var requestOverlayAppId: String? = null
    private var timeoutOverlayAppId: String? = null

    private var expiryWatchAppId: String? = null
    private var expiryWatchRecordId: Long? = null
    private var expiryWatchExpiresAt: Long = 0L
    private var expiryWatchJob: Job? = null

    val appProfilesFlow = DbSet.usageGuardAppProfileDao.queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    val tagsFlow = DbSet.usageGuardTagDao.queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    fun onAppChanged(packageName: String, service: A11yService) {
        appScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                handleAppChanged(packageName, service)
            }
        }
    }

    fun onRequestOverlayStopped(appId: String?) {
        appScope.launch {
            stateMutex.withLock {
                if (requestOverlayAppId == appId) {
                    requestOverlayAppId = null
                }
            }
        }
    }

    fun onTimeoutOverlayStopped(appId: String?) {
        appScope.launch {
            stateMutex.withLock {
                if (timeoutOverlayAppId == appId) {
                    timeoutOverlayAppId = null
                }
            }
        }
    }

    fun onRequestGranted(appId: String) {
        appScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                requestOverlayAppId = null
                lastProtectedAppId = appId
                val record = DbSet.usageGuardRecordDao.getActiveRecord(appId) ?: return@withLock
                scheduleExpiryWatch(record)
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
        }
    }

    private suspend fun handleAppChanged(packageName: String, service: A11yService) {
        closePreviousSessionIfNeeded(packageName)

        if (shouldSkipApp(packageName)) {
            lastProtectedAppId = null
            return
        }

        val settings = storeFlow.value
        if (FocusModeEngine.isActiveFlow.value &&
            !FocusModeEngine.currentWhitelistFlow.value.contains(packageName)
        ) {
            lastProtectedAppId = null
            return
        }
        if (AppBlockerEngine.shouldBlock(packageName).first) {
            lastProtectedAppId = null
            return
        }
        val profile = DbSet.usageGuardAppProfileDao.getByAppId(packageName)
        val shouldProtect = UsageGuardPolicy.shouldProtectApp(
            enabled = settings.usageGuardEnabled,
            scopeMode = settings.usageGuardScopeMode,
            appProfile = profile?.toSnapshot(),
        )
        if (!shouldProtect) {
            lastProtectedAppId = null
            return
        }

        lastProtectedAppId = packageName
        if (requestOverlayAppId != null || timeoutOverlayAppId != null) {
            return
        }

        val activeRecord = DbSet.usageGuardRecordDao.getActiveRecord(packageName)
        val now = System.currentTimeMillis()
        if (activeRecord == null) {
            cancelExpiryWatch(packageName)
            showRequestOverlay(service, packageName, profile, settings.usageGuardMinReasonLength)
            requestOverlayAppId = packageName
            return
        }

        if (activeRecord.expiresAt <= now) {
            cancelExpiryWatch(packageName)
            DbSet.usageGuardRecordDao.closeRecord(
                id = activeRecord.id,
                endedAt = now,
                endReason = UsageGuardRecord.END_REASON_EXPIRED,
            )
            showTimeoutOverlay(service, packageName, activeRecord.id, activeRecord.reasonText)
            timeoutOverlayAppId = packageName
            return
        }

        scheduleExpiryWatch(activeRecord)
    }

    private suspend fun closePreviousSessionIfNeeded(nextAppId: String) {
        val previousAppId = lastProtectedAppId ?: return
        if (previousAppId == nextAppId) return

        cancelExpiryWatch(previousAppId)

        val active = DbSet.usageGuardRecordDao.getActiveRecord(previousAppId) ?: return
        if (active.grantMode != UsageGuardPolicy.GRANT_MODE_STRICT) return

        DbSet.usageGuardRecordDao.closeRecord(
            id = active.id,
            endedAt = System.currentTimeMillis(),
            endReason = UsageGuardRecord.END_REASON_LEFT_APP,
        )
    }

    private fun shouldSkipApp(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        return packageName == META.appId ||
            packageName == launcherAppId ||
            packageName == imeAppId ||
            packageName == systemUiAppId
    }

    private fun scheduleExpiryWatch(record: UsageGuardRecord) {
        if (topActivityFlow.value.appId != record.appId) {
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
                if (topActivityFlow.value.appId != record.appId) return@withLock
                if (timeoutOverlayAppId == record.appId) return@withLock

                val activeRecord = DbSet.usageGuardRecordDao.getActiveRecord(record.appId) ?: return@withLock
                if (activeRecord.id != record.id) return@withLock
                if (activeRecord.expiresAt > System.currentTimeMillis()) return@withLock

                DbSet.usageGuardRecordDao.closeRecord(
                    id = activeRecord.id,
                    endedAt = System.currentTimeMillis(),
                    endReason = UsageGuardRecord.END_REASON_EXPIRED,
                )

                cancelExpiryWatch(record.appId)
                A11yService.instance?.let { service ->
                    showTimeoutOverlay(service, record.appId, activeRecord.id, activeRecord.reasonText)
                    timeoutOverlayAppId = record.appId
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

    private fun showRequestOverlay(
        service: A11yService,
        appId: String,
        profile: UsageGuardAppProfile?,
        minReasonLength: Int,
    ) {
        val appName = appInfoMapFlow.value[appId]?.name ?: appId
        val grantMode = profile?.grantMode ?: storeFlow.value.usageGuardDefaultGrantMode
        service.startService(Intent(service, UsageGuardRequestOverlayService::class.java).apply {
            putExtra("appId", appId)
            putExtra("appName", appName)
            putExtra("grantMode", grantMode)
            putExtra("minReasonLength", minReasonLength)
        })
    }

    private fun showTimeoutOverlay(
        service: A11yService,
        appId: String,
        recordId: Long,
        reasonText: String,
    ) {
        service.startService(Intent(service, UsageGuardTimeoutOverlayService::class.java).apply {
            putExtra("appId", appId)
            putExtra("recordId", recordId)
            putExtra("reasonText", reasonText)
        })
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
