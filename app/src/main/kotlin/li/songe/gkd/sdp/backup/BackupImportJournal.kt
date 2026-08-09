package li.songe.gkd.sdp.backup

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.songe.gkd.sdp.store.writeTextAtomically
import li.songe.gkd.sdp.util.requirePendingDataRecoveryComplete
import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@Serializable
enum class BackupImportPhase {
    PREPARED,
    APPLYING,
    COMMITTED,
    ROLLED_BACK,
}

@Serializable
data class BackupImportJournalRecord(
    val phase: BackupImportPhase,
    val payloadHash: String,
    val previousState: BackupPayload,
)

interface BackupImportJournal {
    suspend fun read(): BackupImportJournalRecord?
    suspend fun write(record: BackupImportJournalRecord)
    suspend fun clear(): Boolean
}

class BackupImportRecoveryBlockedException : IllegalStateException("backup_import_recovery")

private object RecoveryContextKey : CoroutineContext.Key<RecoveryContext>
private class RecoveryContext : AbstractCoroutineContextElement(RecoveryContextKey)
private object MutationGateKey : CoroutineContext.Key<MutationGateContext>
private class MutationGateContext : AbstractCoroutineContextElement(MutationGateKey)

@Volatile
private var backupImportRecoveryBlocked = false
private val backupDataMutationGate = Mutex()

internal fun blockBackupImportRecovery() {
    backupImportRecoveryBlocked = true
}

internal fun unblockBackupImportRecovery() {
    backupImportRecoveryBlocked = false
}

suspend fun requireBackupImportRecoveryComplete() {
    if (
        backupImportRecoveryBlocked &&
        currentCoroutineContext()[RecoveryContextKey] == null
    ) {
        throw BackupImportRecoveryBlockedException()
    }
}

suspend fun <T> withBackupImportRecoveryContext(block: suspend () -> T): T =
    withContext(RecoveryContext()) { block() }

internal suspend fun <T> withBackupDataMutationGate(block: suspend () -> T): T {
    if (currentCoroutineContext()[MutationGateKey] != null) return block()
    return backupDataMutationGate.withLock {
        if (currentCoroutineContext()[RecoveryContextKey] == null) {
            requireBackupImportRecoveryComplete()
            requirePendingDataRecoveryComplete()
        }
        withContext(MutationGateContext()) { block() }
    }
}

class FileBackupImportJournal(
    private val file: File,
    private val codec: Json = Json { encodeDefaults = true },
) : BackupImportJournal {
    override suspend fun read(): BackupImportJournalRecord? {
        if (!file.isFile) return null
        return codec.decodeFromString<BackupImportJournalRecord>(file.readText())
    }

    override suspend fun write(record: BackupImportJournalRecord) {
        writeTextAtomically(file, codec.encodeToString(record))
    }

    override suspend fun clear(): Boolean = runCatching {
        val tempFile = file.parentFile?.resolve("${file.name}.tmp")
        if (file.exists() && !file.delete()) return@runCatching false
        if (tempFile?.exists() == true && !tempFile.delete()) return@runCatching false
        !file.exists() && tempFile?.exists() != true
    }.getOrDefault(false)
}
