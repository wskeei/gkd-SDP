package li.songe.gkd.sdp.util

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.backup.AppBackupRepository
import li.songe.gkd.sdp.backup.BackupCatalog
import li.songe.gkd.sdp.backup.BackupErrorCode
import li.songe.gkd.sdp.backup.BackupExportCoordinator
import li.songe.gkd.sdp.backup.BackupExportSummary
import li.songe.gkd.sdp.backup.BackupFormatV2
import li.songe.gkd.sdp.backup.BackupImportCoordinator
import li.songe.gkd.sdp.backup.BackupResult
import li.songe.gkd.sdp.backup.BackupSourceFormat
import li.songe.gkd.sdp.backup.FileBackupImportJournal
import li.songe.gkd.sdp.backup.LegacyBackupImporter
import li.songe.gkd.sdp.backup.PreparedBackupImport
import java.io.File
import li.songe.gkd.sdp.R

object BackupUtils {
    private const val MAX_ENCRYPTED_BACKUP_BYTES = 65L * 1024L * 1024L
    private val repository by lazy { AppBackupRepository() }
    private val exportCoordinator by lazy {
        BackupExportCoordinator(
            source = repository,
            tempDirectoryFactory = ::createGkdTempDir,
        )
    }
    private val importCoordinator by lazy {
        BackupImportCoordinator(
            target = repository,
            journal = FileBackupImportJournal(
                privateStoreFolder.resolve("backup-import-journal.json"),
            ),
            tempDirectoryFactory = ::createGkdTempDir,
        )
    }

    val defaultCategoryIds: Set<String>
        get() = BackupCatalog.defaultCategoryIds

    val pendingImportUriFlow = MutableStateFlow<Uri?>(null)

    suspend fun importBackUpData(uri: Uri) {
        pendingImportUriFlow.value = uri
        toast(li.songe.gkd.sdp.app.getString(R.string.s_e907fcd7c0))
    }

    suspend fun exportBackUpData(
        categoryIds: Set<String>,
        password: CharArray,
    ): BackupResult<BackupExportSummary> {
        val outputFile = sharedDir.resolve(
            "gkd-sdp-backup-v2-${System.currentTimeMillis()}.gkdbak",
        )
        return exportCoordinator.export(categoryIds, password, outputFile)
    }

    suspend fun prepareImport(
        uri: Uri,
        password: CharArray,
    ): BackupResult<PreparedBackupImport> {
        val tempDirectory = createGkdTempDir()
        var encryptedBytes: ByteArray? = null
        return try {
            val encryptedFile = tempDirectory.resolve("backup.gkdbak")
            UriUtils.copyUriToFile(
                uri = uri,
                target = encryptedFile,
                maxBytes = MAX_ENCRYPTED_BACKUP_BYTES,
            )
            encryptedBytes = encryptedFile.readBytes()
            val bytes = requireNotNull(encryptedBytes)
            if (!BackupFormatV2.hasMagic(bytes) && LegacyBackupImporter.looksLikeArchive(bytes)) {
                when (val legacy = LegacyBackupImporter.read(encryptedFile)) {
                    is BackupResult.Failure -> legacy
                    is BackupResult.Success -> importCoordinator.preparePayload(
                        payload = legacy.value,
                        sourceFormat = BackupSourceFormat.LEGACY_V1,
                    )
                }
            } else {
                importCoordinator.prepare(bytes, password)
            }
        } catch (error: CancellationException) {
            password.fill('\u0000')
            throw error
        } catch (_: Throwable) {
            password.fill('\u0000')
            BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        } finally {
            encryptedBytes?.fill(0)
            password.fill('\u0000')
            tempDirectory.deleteRecursively()
        }
    }

    suspend fun applyImport(
        prepared: PreparedBackupImport,
        confirmed: Boolean,
    ): BackupResult<Unit> = importCoordinator.apply(prepared, confirmed)

    suspend fun refreshImportPreview(
        prepared: PreparedBackupImport,
    ): BackupResult<PreparedBackupImport> = importCoordinator.refreshPreview(prepared)

    suspend fun recoverInterruptedImport() {
        importCoordinator.recoverInterruptedImport()
    }
}
