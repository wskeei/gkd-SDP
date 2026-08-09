package li.songe.gkd.sdp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import li.songe.gkd.sdp.backup.BackupDataMutationBarrier
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.data.SubsItem
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.writeTextAtomically
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

object SubscriptionMutationRepository {
    suspend fun upsert(
        subscription: RawSubscription,
        subsItem: SubsItem? = null,
    ): RawSubscription = BackupDataMutationBarrier.withMutation {
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
            try {
                withContext(Dispatchers.IO) {
                    stagingFolder.mkdirs()
                    writeTextAtomically(
                        stagedNewFile,
                        json.encodeToString(nextSubscription),
                    )
                }
                val now = System.currentTimeMillis()
                DbSet.withTransaction {
                    cleanupSubsConfig(subsId, nextSubscription)
                    if (subsItem == null) {
                        DbSet.subsItemDao.updateMtime(subsId, now)
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
                    requirePendingDataCleanup(stagingFolder)
                }
                LogUtils.d(
                    "更新订阅文件",
                    "id=$subsId,version=${nextSubscription.version}",
                )
                nextSubscription
            } catch (error: Throwable) {
                if (!transactionCommitted) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        restoreSubscriptionFile(
                            targetFile = targetFile,
                            stagedPreviousFile = stagedPreviousFile,
                            previousStaged = previousStaged,
                            targetReplaced = targetReplaced,
                        )
                        runCatching { requirePendingDataCleanup(stagingFolder) }
                    }
                }
                throw error
            }
        }
    }

    suspend fun delete(vararg subsIds: Long): Int {
        val uniqueIds = subsIds.distinct().toLongArray()
        if (uniqueIds.isEmpty()) return 0
        return BackupDataMutationBarrier.withMutation {
            updateSubsMutex.withStateLock {
                val stagingFolder = subsFolder.resolve(
                    "$SUBSCRIPTION_MUTATION_STAGING_PREFIX${UUID.randomUUID()}",
                )
                val stagedIds = mutableSetOf<Long>()
                var transactionCommitted = false
                try {
                    val deleted = DbSet.withTransaction {
                        withContext(Dispatchers.IO) {
                            stagingFolder.mkdirs()
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
                        requirePendingDataCleanup(stagingFolder)
                    }
                    deleted
                } catch (error: Throwable) {
                    if (!transactionCommitted) {
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
                            runCatching { requirePendingDataCleanup(stagingFolder) }
                        }
                    }
                    throw error
                }
            }
        }
    }

    private fun normalizeSubscription(subscription: RawSubscription): RawSubscription {
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

    private fun restoreSubscriptionFile(
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

    private fun moveSubscriptionFile(source: File, target: File) {
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
