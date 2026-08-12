package li.songe.gkd.sdp.backup

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import li.songe.gkd.sdp.util.json
import li.songe.gkd.sdp.util.ZipUtils
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupPayloadArchivePolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createPayloadRejectsDuplicateObjectIds() {
        val first = objectValue("settings", "settings", "one")
        val second = objectValue("settings", "settings", "two")

        assertThrows { BackupPayloadArchive.createPayload(listOf(first, second)) }
    }

    @Test
    fun createPayloadRejectsInvalidObjectMetadata() {
        assertThrows {
            BackupPayloadArchive.createPayload(
                listOf(objectValue("../settings", "settings", "x")),
            )
        }
        assertThrows {
            BackupPayloadArchive.createPayload(
                listOf(objectValue("settings", "unknown-category", "x")),
            )
        }
        assertThrows {
            BackupPayloadArchive.createPayload(
                listOf(objectValue("settings", "settings", "x").copy(schema = 2)),
            )
        }
        assertThrows {
            BackupPayloadArchive.createPayload(
                listOf(objectValue("settings", "settings", "x").copy(count = -1)),
            )
        }
        assertThrows {
            BackupPayloadArchive.createPayload(
                listOf(
                    objectValue("settings", "settings", "x").copy(
                        content = ByteArray(ZipUtils.ArchiveLimits().maxEntryUncompressedBytes.toInt() + 1),
                    ),
                ),
            )
        }
    }

    @Test
    fun readRejectsMissingManifestAndUnknownSchema() {
        val missing = temporaryFolder.newFile("missing-manifest.zip")
        ZipOutputStream(missing.outputStream()).use { output ->
            output.writeEntry("objects/settings.bin", "x".encodeToByteArray())
        }
        val missingResult = BackupPayloadArchive.read(missing)
        assertEquals(BackupErrorCode.INVALID_PAYLOAD, (missingResult as BackupResult.Failure).code)

        val wrongSchema = temporaryFolder.newFile("wrong-schema.zip")
        val manifest = BackupPayloadManifest(
            formatVersion = BackupFormatV2.FORMAT_VERSION,
            categoryIds = listOf("settings"),
            objects = listOf(
                BackupManifestObject(
                    objectId = "settings",
                    categoryId = "settings",
                    schema = 9,
                    count = 1,
                    bytes = 1,
                    sha256 = "not-used",
                    entryName = "objects/settings.bin",
                ),
            ),
        )
        ZipOutputStream(wrongSchema.outputStream()).use { output ->
            output.writeEntry("manifest.json", json.encodeToString(manifest).encodeToByteArray())
            output.writeEntry("objects/settings.bin", "x".encodeToByteArray())
        }
        val schemaResult = BackupPayloadArchive.read(wrongSchema)
        assertEquals(BackupErrorCode.SCHEMA_MISMATCH, (schemaResult as BackupResult.Failure).code)
    }

    @Test
    fun readRejectsManifestCategoryAndNameMismatches() {
        val unsorted = temporaryFolder.newFile("unsorted.zip")
        val manifest = BackupPayloadManifest(
            formatVersion = BackupFormatV2.FORMAT_VERSION,
            categoryIds = listOf("settings", "subscriptions"),
            objects = listOf(
                manifestObject("table-subs_item", "subscriptions"),
                manifestObject("settings", "settings"),
            ),
        )
        ZipOutputStream(unsorted.outputStream()).use { output ->
            output.writeEntry("manifest.json", json.encodeToString(manifest).encodeToByteArray())
            output.writeEntry("objects/table-subs_item.bin", "x".encodeToByteArray())
            output.writeEntry("objects/settings.bin", "x".encodeToByteArray())
        }
        val unsortedResult = BackupPayloadArchive.read(unsorted)
        assertEquals(BackupErrorCode.INVALID_PAYLOAD, (unsortedResult as BackupResult.Failure).code)

        val categoryMismatch = temporaryFolder.newFile("category-mismatch.zip")
        val mismatchManifest = BackupPayloadManifest(
            formatVersion = BackupFormatV2.FORMAT_VERSION,
            categoryIds = listOf("settings"),
            objects = listOf(manifestObject("settings", "subscriptions")),
        )
        ZipOutputStream(categoryMismatch.outputStream()).use { output ->
            output.writeEntry("manifest.json", json.encodeToString(mismatchManifest).encodeToByteArray())
            output.writeEntry("objects/settings.bin", "x".encodeToByteArray())
        }
        val categoryResult = BackupPayloadArchive.read(categoryMismatch)
        assertEquals(BackupErrorCode.INVALID_PAYLOAD, (categoryResult as BackupResult.Failure).code)
    }

    @Test
    fun readRejectsContentHashAndSizeMismatch() {
        val archive = temporaryFolder.newFile("hash-mismatch.zip")
        val manifest = BackupPayloadManifest(
            formatVersion = BackupFormatV2.FORMAT_VERSION,
            categoryIds = listOf("settings"),
            objects = listOf(
                BackupManifestObject(
                    objectId = "settings",
                    categoryId = "settings",
                    schema = 1,
                    count = 1,
                    bytes = 5,
                    sha256 = "0".repeat(64),
                    entryName = "objects/settings.bin",
                ),
            ),
        )
        ZipOutputStream(archive.outputStream()).use { output ->
            output.writeEntry("manifest.json", json.encodeToString(manifest).encodeToByteArray())
            output.writeEntry("objects/settings.bin", "wrong".encodeToByteArray())
        }

        val result = BackupPayloadArchive.read(archive)

        assertEquals(BackupErrorCode.INVALID_PAYLOAD, (result as BackupResult.Failure).code)
    }

    @Test
    fun buildSortsAndWritesCanonicalArchive() {
        val archive = temporaryFolder.newFile("canonical.zip")
        BackupPayloadArchive.build(
            archive,
            listOf(
                objectValue("table-subs_item", "subscriptions", "second"),
                objectValue("settings", "settings", "first"),
            ),
        )

        val parsed = BackupPayloadArchive.read(archive)
        assertTrue(parsed is BackupResult.Success)
        assertEquals(
            listOf("settings", "table-subs_item"),
            (parsed as BackupResult.Success).value.objects.map { it.objectId },
        )
    }

    private fun objectValue(
        objectId: String,
        categoryId: String,
        text: String,
    ) = BackupPayloadObject(
        objectId = objectId,
        categoryId = categoryId,
        schema = 1,
        count = 1,
        content = text.encodeToByteArray(),
    )

    private fun manifestObject(
        objectId: String,
        categoryId: String,
    ) = BackupManifestObject(
        objectId = objectId,
        categoryId = categoryId,
        schema = 1,
        count = 1,
        bytes = 1,
        sha256 = "x",
        entryName = "objects/$objectId.bin",
    )

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
