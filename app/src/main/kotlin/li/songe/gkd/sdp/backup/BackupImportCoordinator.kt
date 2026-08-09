package li.songe.gkd.sdp.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

data class BackupConflictPreview(
    val categoryId: String,
    val added: Int,
    val overwritten: Int,
    val deleted: Int,
)

enum class BackupSourceFormat {
    ENCRYPTED_V2,
    LEGACY_V1,
}

data class PreparedBackupImport(
    val payload: BackupPayload,
    val previewStateHash: String,
    val conflicts: List<BackupConflictPreview>,
    val sourceFormat: BackupSourceFormat,
)

private data class BackupImportMutationOutcome(
    val result: BackupResult<Unit>,
    val rollbackCompleted: Boolean = false,
    val commitRecord: BackupImportJournalRecord? = null,
    val handledCancellation: CancellationException? = null,
)

private class ImportRecoveryException(
    val recoveryCompleted: Boolean,
    cause: Throwable,
) : RuntimeException(cause)

private class HandledImportCancellation(
    val original: CancellationException,
) : CancellationException(original.message) {
    init {
        initCause(original)
    }
}

interface BackupImportTarget {
    suspend fun <T> withExclusiveMutation(
        block: suspend () -> T,
        afterCommit: suspend (T) -> Unit = {},
    ): T = block().also { afterCommit(it) }
    suspend fun <T> withRecoveryMutation(block: suspend () -> T): T = block()
    suspend fun validateReferences(payload: BackupPayload): Boolean
    suspend fun capture(categoryIds: Set<String>): BackupPayload
    suspend fun preview(
        previous: BackupPayload,
        incoming: BackupPayload,
    ): List<BackupConflictPreview>

    suspend fun replaceIncludedCategories(payload: BackupPayload)
    suspend fun restore(previous: BackupPayload)
    suspend fun reconcileRuntime()
}

class BackupImportCoordinator(
    private val target: BackupImportTarget,
    private val journal: BackupImportJournal,
    private val crypto: BackupCrypto = BackupCrypto(),
    private val tempDirectoryFactory: () -> File,
) {
    private val importMutex = Mutex()

    suspend fun prepare(
        encryptedBytes: ByteArray,
        password: CharArray,
    ): BackupResult<PreparedBackupImport> {
        val decrypted = when (val result = crypto.decrypt(encryptedBytes, password)) {
            is BackupResult.Failure -> return result
            is BackupResult.Success -> result.value
        }
        val tempDirectory = tempDirectoryFactory()
        return try {
            val payloadFile = tempDirectory.resolve("payload.zip")
            payloadFile.writeBytes(decrypted)
            decrypted.fill(0)
            val payload = when (val result = BackupPayloadArchive.read(payloadFile)) {
                is BackupResult.Failure -> return result
                is BackupResult.Success -> result.value
            }
            preparePayload(
                payload = payload,
                sourceFormat = BackupSourceFormat.ENCRYPTED_V2,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        } finally {
            decrypted.fill(0)
            tempDirectory.deleteRecursively()
        }
    }

    suspend fun preparePayload(
        payload: BackupPayload,
        sourceFormat: BackupSourceFormat,
    ): BackupResult<PreparedBackupImport> = importMutex.withLock {
        preparePayloadUnlocked(payload, sourceFormat)
    }

    suspend fun refreshPreview(
        prepared: PreparedBackupImport,
    ): BackupResult<PreparedBackupImport> = importMutex.withLock {
        try {
            val currentState = target.capture(prepared.payload.manifest.categoryIds.toSet())
            BackupResult.Success(
                prepared.copy(
                    previewStateHash = currentState.payloadHash,
                    conflicts = target.preview(currentState, prepared.payload),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        }
    }

    suspend fun apply(
        prepared: PreparedBackupImport,
        confirmed: Boolean,
    ): BackupResult<Unit> = importMutex.withLock {
        if (!confirmed) return BackupResult.Failure(BackupErrorCode.IMPORT_NOT_CONFIRMED)
        val originalJob = currentCoroutineContext()[Job]
        val outcome = try {
            target.withExclusiveMutation(
                block = {
                    val currentState = try {
                        target.capture(prepared.payload.manifest.categoryIds.toSet())
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        return@withExclusiveMutation BackupImportMutationOutcome(
                            result = BackupResult.Failure(BackupErrorCode.IMPORT_FAILED),
                        )
                    }
                    if (currentState.payloadHash != prepared.previewStateHash) {
                        return@withExclusiveMutation BackupImportMutationOutcome(
                            result = BackupResult.Failure(BackupErrorCode.IMPORT_PREVIEW_STALE),
                        )
                    }
                    val baseRecord = BackupImportJournalRecord(
                        phase = BackupImportPhase.PREPARED,
                        payloadHash = prepared.payload.payloadHash,
                        previousState = currentState,
                    )
                    try {
                        journal.write(baseRecord)
                        journal.write(baseRecord.copy(phase = BackupImportPhase.APPLYING))
                        target.replaceIncludedCategories(prepared.payload)
                        BackupImportMutationOutcome(
                            result = BackupResult.Success(Unit),
                            commitRecord = baseRecord,
                        )
                    } catch (error: CancellationException) {
                        val rollbackCompleted = withContext(NonCancellable) {
                            rollbackAfterFailure(currentState)
                        }
                        if (!rollbackCompleted) {
                            blockBackupImportRecovery()
                            throw error
                        }
                        withContext(NonCancellable) {
                            persistRollbackTerminal(baseRecord)
                        }
                        BackupImportMutationOutcome(
                            result = BackupResult.Failure(BackupErrorCode.IMPORT_FAILED),
                            rollbackCompleted = true,
                            handledCancellation = error,
                        )
                    } catch (error: Throwable) {
                        val rollbackCompleted = withContext(NonCancellable) {
                            rollbackAfterFailure(currentState)
                        }
                        if (!rollbackCompleted) {
                            blockBackupImportRecovery()
                            throw ImportRecoveryException(false, error)
                        }
                        persistRollbackTerminal(baseRecord)
                        BackupImportMutationOutcome(
                            result = BackupResult.Failure(BackupErrorCode.IMPORT_FAILED),
                            rollbackCompleted = rollbackCompleted,
                        )
                    }
                },
                afterCommit = { committedOutcome ->
                    val commitRecord = committedOutcome.commitRecord
                    if (commitRecord != null) {
                        try {
                            withContext(NonCancellable) {
                                journal.write(commitRecord.copy(phase = BackupImportPhase.COMMITTED))
                            }
                            withContext(originalJob ?: EmptyCoroutineContext) {
                                reconcileCommittedImport(
                                    record = commitRecord,
                                    journalAlreadyCommitted = true,
                                )
                            }
                            originalJob?.ensureActive()
                        } catch (error: HandledImportCancellation) {
                            throw error
                        } catch (error: Throwable) {
                            val journalRemains = runCatching {
                                withContext(NonCancellable) { journal.read() != null }
                            }.getOrElse {
                                blockBackupImportRecovery()
                                throw ImportRecoveryException(false, it)
                            }
                            if (!journalRemains) throw error
                            val recoveryCompleted = rollbackCommittedImport(commitRecord)
                            if (!recoveryCompleted) {
                                blockBackupImportRecovery()
                                throw ImportRecoveryException(false, error)
                            }
                            if (error is CancellationException) {
                                throw HandledImportCancellation(error)
                            }
                            throw ImportRecoveryException(true, error)
                        }
                    } else if (committedOutcome.rollbackCompleted) {
                        withContext(NonCancellable) {
                            clearJournalOrBlock()
                        }
                        committedOutcome.handledCancellation?.let {
                            throw HandledImportCancellation(it)
                        }
                    }
                },
            )
        } catch (error: CancellationException) {
            if (error is HandledImportCancellation) throw error.original
            throw error
        } catch (error: ImportRecoveryException) {
            return BackupResult.Failure(
                if (error.recoveryCompleted) {
                    BackupErrorCode.IMPORT_FAILED
                } else {
                    BackupErrorCode.IMPORT_RECOVERY_REQUIRED
                },
            )
        } catch (_: Throwable) {
            return BackupResult.Failure(BackupErrorCode.IMPORT_RECOVERY_REQUIRED)
        }
        outcome.result
    }

    suspend fun recoverInterruptedImport(): Unit = importMutex.withLock {
        withBackupImportRecoveryContext {
            val record = runCatching { journal.read() }.getOrElse { error ->
                blockBackupImportRecovery()
                throw error
            } ?: run {
                unblockBackupImportRecovery()
                return@withBackupImportRecoveryContext
            }
            try {
                when (record.phase) {
                    BackupImportPhase.PREPARED -> clearJournalOrBlock()
                    BackupImportPhase.APPLYING -> {
                        target.withExclusiveMutation(
                            block = {
                                target.restore(record.previousState)
                                persistRollbackTerminal(record)
                            },
                            afterCommit = { clearJournalOrBlock() },
                        )
                    }
                    BackupImportPhase.ROLLED_BACK -> {
                        target.reconcileRuntime()
                        clearJournalOrBlock()
                    }
                    BackupImportPhase.COMMITTED -> {
                        target.reconcileRuntime()
                        clearJournalOrBlock()
                    }
                }
            } catch (error: Throwable) {
                blockBackupImportRecovery()
                throw error
            }
        }
    }

    private suspend fun preparePayloadUnlocked(
        payload: BackupPayload,
        sourceFormat: BackupSourceFormat,
    ): BackupResult<PreparedBackupImport> = try {
        if (!target.validateReferences(payload)) {
            BackupResult.Failure(BackupErrorCode.REFERENCE_MISMATCH)
        } else {
            val currentState = target.capture(payload.manifest.categoryIds.toSet())
            BackupResult.Success(
                PreparedBackupImport(
                    payload = payload,
                    previewStateHash = currentState.payloadHash,
                    conflicts = target.preview(currentState, payload),
                    sourceFormat = sourceFormat,
                ),
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
    }

    private suspend fun rollbackAfterFailure(previousState: BackupPayload): Boolean =
        try {
            target.restore(previousState)
            true
        } catch (_: Throwable) {
            // Keep the APPLYING journal so startup recovery retries before opening the interface.
            false
        }

    private suspend fun persistRollbackTerminal(record: BackupImportJournalRecord) {
        try {
            journal.write(record.copy(phase = BackupImportPhase.ROLLED_BACK))
        } catch (error: Throwable) {
            blockBackupImportRecovery()
            throw ImportRecoveryException(false, error)
        }
    }

    private suspend fun clearJournalOrBlock() {
        try {
            check(journal.clear()) { "backup_import_journal_cleanup_failed" }
            unblockBackupImportRecovery()
        } catch (error: Throwable) {
            blockBackupImportRecovery()
            throw error
        }
    }

    private suspend fun reconcileCommittedImport(
        record: BackupImportJournalRecord,
        journalAlreadyCommitted: Boolean = false,
    ) {
        try {
            if (!journalAlreadyCommitted) {
                journal.write(record.copy(phase = BackupImportPhase.COMMITTED))
            }
            target.reconcileRuntime()
            clearJournalOrBlock()
        } catch (error: Throwable) {
            val recoveryCompleted = rollbackCommittedImport(record)
            if (!recoveryCompleted) blockBackupImportRecovery()
            if (error is CancellationException && recoveryCompleted) {
                throw HandledImportCancellation(error)
            }
            throw ImportRecoveryException(recoveryCompleted, error)
        }
    }

    private suspend fun rollbackCommittedImport(record: BackupImportJournalRecord): Boolean =
        withContext(NonCancellable) {
            withBackupImportRecoveryContext {
                val applyingWritten = runCatching {
                    journal.write(record.copy(phase = BackupImportPhase.APPLYING))
                }.isSuccess
                val restored = applyingWritten && runCatching {
                    target.withRecoveryMutation {
                        target.restore(record.previousState)
                        persistRollbackTerminal(record)
                    }
                }.isSuccess
                val reconciled = restored && runCatching {
                    target.reconcileRuntime()
                }.isSuccess
                val journalCleared = reconciled && runCatching {
                    clearJournalOrBlock()
                }.isSuccess
                restored && reconciled && journalCleared
            }
        }
}
