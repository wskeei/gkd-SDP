package li.songe.gkd.sdp.backup

import kotlinx.coroutines.CancellationException
import li.songe.gkd.sdp.store.writeFileAtomically
import java.io.File

data class BackupExportSummary(
    val file: File,
    val categoryIds: List<String>,
    val objectCount: Int,
    val encryptedBytes: Long,
)

interface BackupExportSource {
    suspend fun collect(categoryIds: Set<String>): BackupPayload
}

class BackupExportCoordinator(
    private val source: BackupExportSource,
    private val crypto: BackupCrypto = BackupCrypto(),
    private val tempDirectoryFactory: () -> File,
) {
    suspend fun export(
        categoryIds: Set<String>,
        password: CharArray,
        outputFile: File,
    ): BackupResult<BackupExportSummary> {
        if (
            categoryIds.isEmpty() ||
            categoryIds.any { BackupCatalog.category(it) == null }
        ) {
            password.fill('\u0000')
            return BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        }
        val tempDirectory = tempDirectoryFactory()
        return try {
            val payload = source.collect(categoryIds)
            val payloadFile = tempDirectory.resolve("payload.zip")
            BackupPayloadArchive.build(payloadFile, payload.objects)
            val encrypted = when (
                val result = crypto.encrypt(payloadFile.readBytes(), password)
            ) {
                is BackupResult.Failure -> return result
                is BackupResult.Success -> result.value
            }
            writeFileAtomically(outputFile, encrypted)
            encrypted.fill(0)
            BackupResult.Success(
                BackupExportSummary(
                    file = outputFile,
                    categoryIds = payload.manifest.categoryIds,
                    objectCount = payload.objects.size,
                    encryptedBytes = outputFile.length(),
                ),
            )
        } catch (error: CancellationException) {
            password.fill('\u0000')
            throw error
        } catch (_: Throwable) {
            password.fill('\u0000')
            outputFile.delete()
            BackupResult.Failure(BackupErrorCode.INVALID_PAYLOAD)
        } finally {
            password.fill('\u0000')
            tempDirectory.deleteRecursively()
        }
    }
}
