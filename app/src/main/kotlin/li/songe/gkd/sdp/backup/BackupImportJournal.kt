package li.songe.gkd.sdp.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.songe.gkd.sdp.store.writeTextAtomically
import java.io.File

@Serializable
enum class BackupImportPhase {
    PREPARED,
    APPLYING,
    COMMITTED,
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
    suspend fun clear()
}

class FileBackupImportJournal(
    private val file: File,
    private val codec: Json = Json { encodeDefaults = true },
) : BackupImportJournal {
    override suspend fun read(): BackupImportJournalRecord? = file.takeIf(File::isFile)?.let {
        runCatching { codec.decodeFromString<BackupImportJournalRecord>(it.readText()) }.getOrNull()
    }

    override suspend fun write(record: BackupImportJournalRecord) {
        writeTextAtomically(file, codec.encodeToString(record))
    }

    override suspend fun clear() {
        file.delete()
        file.parentFile?.resolve("${file.name}.tmp")?.delete()
    }
}
