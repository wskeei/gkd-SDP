package li.songe.gkd.sdp.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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

interface BackupImportTarget {
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
        val currentState = try {
            target.capture(prepared.payload.manifest.categoryIds.toSet())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return BackupResult.Failure(BackupErrorCode.IMPORT_FAILED)
        }
        if (currentState.payloadHash != prepared.previewStateHash) {
            return BackupResult.Failure(BackupErrorCode.IMPORT_PREVIEW_STALE)
        }
        val baseRecord = BackupImportJournalRecord(
            phase = BackupImportPhase.PREPARED,
            payloadHash = prepared.payload.payloadHash,
            previousState = currentState,
        )
        return try {
            journal.write(baseRecord)
            journal.write(baseRecord.copy(phase = BackupImportPhase.APPLYING))
            target.replaceIncludedCategories(prepared.payload)
            journal.write(baseRecord.copy(phase = BackupImportPhase.COMMITTED))
            target.reconcileRuntime()
            journal.clear()
            BackupResult.Success(Unit)
        } catch (error: CancellationException) {
            rollbackAfterFailure(currentState)
            throw error
        } catch (_: Throwable) {
            rollbackAfterFailure(currentState)
            BackupResult.Failure(BackupErrorCode.IMPORT_FAILED)
        }
    }

    suspend fun recoverInterruptedImport(): Unit = importMutex.withLock {
        val record = journal.read() ?: return
        when (record.phase) {
            BackupImportPhase.PREPARED -> journal.clear()
            BackupImportPhase.APPLYING -> {
                target.restore(record.previousState)
                journal.clear()
            }
            BackupImportPhase.COMMITTED -> {
                target.reconcileRuntime()
                journal.clear()
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

    private suspend fun rollbackAfterFailure(previousState: BackupPayload) {
        try {
            target.restore(previousState)
            journal.clear()
        } catch (_: Throwable) {
            // Keep the APPLYING journal so startup recovery retries before opening the interface.
        }
    }
}
