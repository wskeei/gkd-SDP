package li.songe.gkd.sdp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import li.songe.gkd.sdp.backup.BackupDataMutationBarrier
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.data.SubsItem
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.writeTextAtomically
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

object SubscriptionMutationRepository {
    private val lastMutationMtime = AtomicLong(0)

    suspend fun upsert(
        subscription: RawSubscription,
        subsItem: SubsItem? = null,
        expectedCurrentMtime: Long? = null,
    ): RawSubscription {
        return BackupDataMutationBarrier.withMutation {
        requirePendingDataRecoveryComplete()
        updateSubsMutex.withStateLock {
            val nextSubscription = normalizeSubscription(subscription)
            val subsId = nextSubscription.id
            val targetFile = subsFolder.resolve("$subsId.json")
            val stagingFolder = subsFolder.resolve(
                "$SUBSCRIPTION_MUTATION_STAGING_PREFIX${UUID.randomUUID()}",
            )
            val stagedNewFile = stagingFolder.resolve("new.json")
            val stagedPreviousFile = stagingFolder.resolve("previous.json")
            var previousStaged = false
            var targetReplaced = false
            var transactionCommitted = false
            val currentBefore = DbSet.subsItemDao.queryById(subsId)
            if (subsItem == null) {
                check(currentBefore != null) {
                    app.getString(R.string.subscription_missing)
                }
                expectedCurrentMtime?.let { expected ->
                    check(currentBefore.mtime == expected) {
                        app.getString(R.string.subscription_changed)
                    }
                }
            }
            val now = nextMutationMtime(currentBefore?.mtime)
            val targetExisted = targetFile.exists()
            val manifest = PendingDataMutationManifest(
                kind = PENDING_KIND_SUBSCRIPTION_UPSERT,
                ids = listOf(subsId),
                targetExisted = targetExisted,
                expectedMtime = if (subsItem == null) now else null,
                requiredPreviousMtime = currentBefore?.mtime,
                expectedSubsItem = subsItem?.copy(mtime = now),
            )
            try {
                withContext(Dispatchers.IO) {
                    stagingFolder.mkdirs()
                    writePendingDataMutationManifest(stagingFolder, manifest)
                    writeTextAtomically(
                        stagedNewFile,
                        json.encodeToString(nextSubscription),
                    )
                }
                withContext(NonCancellable) {
                    val result = DbSet.withTransaction {
                        cleanupSubsConfig(subsId, nextSubscription)
                        if (subsItem == null) {
                            val current = DbSet.subsItemDao.queryById(subsId)
                            check(current != null) {
                                app.getString(R.string.subscription_missing)
                            }
                            expectedCurrentMtime?.let { expected ->
                                check(current.mtime == expected) {
                                    app.getString(R.string.subscription_changed)
                                }
                            }
                            check(DbSet.subsItemDao.updateMtime(subsId, now) == 1) {
                                app.getString(R.string.subscription_missing)
                            }
                        } else {
                            DbSet.subsItemDao.insert(subsItem.copy(mtime = now))
                        }
                        withContext(Dispatchers.IO) {
                            if (targetFile.exists()) {
                                moveSubscriptionFile(targetFile, stagedPreviousFile)
                                previousStaged = true
                            }
                            moveSubscriptionFile(stagedNewFile, targetFile)
                            targetReplaced = true
                        }
                    }
                    transactionCommitted = true
                    subsMapFlow.update { current -> current + (subsId to nextSubscription) }
                    subsLoadErrorsFlow.update { current -> current - subsId }
                    withContext(Dispatchers.IO) {
                        writePendingDataMutationManifest(
                            stagingFolder,
                            manifest.copy(phase = PENDING_PHASE_COMMITTED),
                        )
                    }
                    result
                }
                withContext(Dispatchers.IO) {
                    requirePendingDataCleanup(stagingFolder)
                }
                LogUtils.d(
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "更新订阅文件",
                    "id=$subsId,version=${nextSubscription.version}",
                )
                nextSubscription
            } catch (error: Throwable) {
                if (transactionCommitted) {
                    blockPendingDataRecovery()
                } else {
                    val recoveryCompleted = runCatching {
                        withContext(NonCancellable + Dispatchers.IO) {
                            restoreSubscriptionFile(
                                targetFile = targetFile,
                                stagedPreviousFile = stagedPreviousFile,
                                previousStaged = previousStaged,
                                targetReplaced = targetReplaced,
                            )
                            requirePendingDataCleanup(stagingFolder)
                        }
                    }.isSuccess
                    if (!recoveryCompleted) blockPendingDataRecovery()
                }
                throw error
            }
        }
    }
    }

    suspend fun delete(vararg subsIds: Long): Int {
        val uniqueIds = subsIds.distinct().toLongArray()
        if (uniqueIds.isEmpty()) return 0
        return BackupDataMutationBarrier.withMutation {
            requirePendingDataRecoveryComplete()
            updateSubsMutex.withStateLock {
                val stagingFolder = subsFolder.resolve(
                    "$SUBSCRIPTION_MUTATION_STAGING_PREFIX${UUID.randomUUID()}",
                )
                val stagedIds = mutableSetOf<Long>()
                var transactionCommitted = false
                val manifest = PendingDataMutationManifest(
                    kind = PENDING_KIND_SUBSCRIPTION_DELETE,
                    ids = uniqueIds.toList(),
                    requiredPreviousMtimes = uniqueIds.asList().mapNotNull { id ->
                        DbSet.subsItemDao.queryById(id)?.let { id to it.mtime }
                    }.toMap(),
                )
                try {
                    val deleted = withContext(NonCancellable) {
                        val result = DbSet.withTransaction {
                            withContext(Dispatchers.IO) {
                                stagingFolder.mkdirs()
                                writePendingDataMutationManifest(stagingFolder, manifest)
                                uniqueIds.forEach { subsId ->
                                    val source = subsFolder.resolve("$subsId.json")
                                    if (source.exists()) {
                                        moveSubscriptionFile(
                                            source,
                                            stagingFolder.resolve("$subsId.json"),
                                        )
                                        stagedIds += subsId
                                    }
                                }
                            }
                            val deleteSize = DbSet.subsItemDao.deleteById(*uniqueIds)
                            DbSet.subsConfigDao.deleteBySubsId(*uniqueIds)
                            DbSet.appConfigDao.deleteBySubsId(*uniqueIds)
                            DbSet.categoryConfigDao.deleteBySubsId(*uniqueIds)
                            DbSet.actionLogDao.deleteBySubsId(*uniqueIds)
                            deleteSize
                        }
                        transactionCommitted = true
                        subsMapFlow.update { current -> current - uniqueIds.toSet() }
                        withContext(Dispatchers.IO) {
                            writePendingDataMutationManifest(
                                stagingFolder,
                                manifest.copy(phase = PENDING_PHASE_COMMITTED),
                            )
                        }
                        result
                    }
                    withContext(Dispatchers.IO) {
                        requirePendingDataCleanup(stagingFolder)
                    }
                    deleted
            } catch (error: Throwable) {
                if (transactionCommitted) {
                    blockPendingDataRecovery()
                } else {
                    val recoveryCompleted = runCatching {
                        withContext(NonCancellable + Dispatchers.IO) {
                            stagedIds.forEach { subsId ->
                                val staged = stagingFolder.resolve("$subsId.json")
                                if (staged.exists()) {
                                    moveSubscriptionFile(
                                        staged,
                                        subsFolder.resolve("$subsId.json"),
                                    )
                                }
                            }
                            requirePendingDataCleanup(stagingFolder)
                        }
                    }.isSuccess
                    if (!recoveryCompleted) blockPendingDataRecovery()
                }
                    throw error
                }
            }
        }
    }

    internal fun normalizeSubscription(subscription: RawSubscription): RawSubscription {
        val current = subsMapFlow.value[subscription.id]
        return if (subscription.id < 0 && current?.version == subscription.version) {
            subscription.copy(
                version = subscription.version + 1,
                apps = subscription.apps
                    .filterIfNotAll { it.groups.isNotEmpty() }
                    .distinctByIfAny { it.id },
            )
        } else {
            subscription
        }
    }

    internal fun nextMutationMtime(previous: Long?): Long {
        val wallClock = System.currentTimeMillis()
        val floor = previous?.let {
            check(it < Long.MAX_VALUE) {
                app.getString(R.string.subscription_time_overflow)
            }
            it + 1
        } ?: wallClock
        return lastMutationMtime.updateAndGet { last ->
            check(last < Long.MAX_VALUE) {
                app.getString(R.string.subscription_time_overflow)
            }
            maxOf(floor, last + 1)
        }
    }

    internal fun restoreSubscriptionFile(
        targetFile: File,
        stagedPreviousFile: File,
        previousStaged: Boolean,
        targetReplaced: Boolean,
    ) {
        if (targetReplaced && targetFile.exists()) targetFile.delete()
        if (previousStaged && stagedPreviousFile.exists()) {
            moveSubscriptionFile(stagedPreviousFile, targetFile)
        }
    }

    internal fun moveSubscriptionFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }
}
