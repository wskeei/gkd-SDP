package li.songe.gkd.sdp.backup

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupExportCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun exportBuildsEncryptedPayloadAndReturnsSummary() = runBlocking {
        val source = object : BackupExportSource {
            override suspend fun collect(categoryIds: Set<String>): BackupPayload =
                payload()
        }
        val coordinator = BackupExportCoordinator(
            source = source,
            crypto = BackupCrypto(
                randomSource = BackupRandomSource { size -> ByteArray(size) { index -> (index + 1).toByte() } },
            ),
            tempDirectoryFactory = { temporaryFolder.newFolder("export-temp") },
        )
        val output = temporaryFolder.newFile("backup.bin")

        val result = coordinator.export(
            categoryIds = setOf("settings"),
            password = "correct horse battery staple".toCharArray(),
            outputFile = output,
        ) as BackupResult.Success<BackupExportSummary>

        assertEquals(listOf("settings"), result.value.categoryIds)
        assertEquals(1, result.value.objectCount)
        assertTrue(output.exists())
        assertTrue(output.length() > 0)
    }

    @Test
    fun exportRejectsInvalidCategoryWithoutWritingFile() = runBlocking {
        val coordinator = BackupExportCoordinator(
            source = object : BackupExportSource {
                override suspend fun collect(categoryIds: Set<String>): BackupPayload =
                    error("must not be called")
            },
            tempDirectoryFactory = { temporaryFolder.newFolder("invalid-temp") },
        )
        val output = temporaryFolder.root.resolve("invalid.bin")

        val result = coordinator.export(
            categoryIds = setOf("unknown"),
            password = "correct horse battery staple".toCharArray(),
            outputFile = output,
        ) as BackupResult.Failure

        assertEquals(BackupErrorCode.INVALID_PAYLOAD, result.code)
        assertFalse(output.exists())
    }

    @Test
    fun exportFailureCleansOutputAndReturnsStableError() = runBlocking {
        val coordinator = BackupExportCoordinator(
            source = object : BackupExportSource {
                override suspend fun collect(categoryIds: Set<String>): BackupPayload =
                    error("synthetic failure")
            },
            crypto = BackupCrypto(
                randomSource = BackupRandomSource { size -> ByteArray(size) },
            ),
            tempDirectoryFactory = { temporaryFolder.newFolder("failure-temp") },
        )
        val output = temporaryFolder.newFile("failure.bin").apply { writeText("x") }

        val result = coordinator.export(
            categoryIds = setOf("settings"),
            password = "correct horse battery staple".toCharArray(),
            outputFile = output,
        ) as BackupResult.Failure

        assertEquals(BackupErrorCode.INVALID_PAYLOAD, result.code)
        assertFalse(output.exists())
    }

    private fun payload(): BackupPayload {
        val content = "settings-json".encodeToByteArray()
        return BackupPayload(
            manifest = BackupPayloadManifest(
                formatVersion = 2,
                categoryIds = listOf("settings"),
                objects = emptyList(),
            ),
            objects = listOf(
                BackupPayloadObject(
                    objectId = "settings",
                    categoryId = "settings",
                    schema = 1,
                    count = 1,
                    content = content,
                ),
            ),
        )
    }
}
