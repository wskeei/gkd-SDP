package li.songe.gkd.sdp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LegacyBackupImporterPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun looksLikeArchiveDetectsZipMagic() {
        assertTrue(LegacyBackupImporter.looksLikeArchive(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)))
        assertFalse(LegacyBackupImporter.looksLikeArchive(ByteArray(4)))
    }

    @Test
    fun categoryForPathRejectsUnsafeAndUnknownPaths() {
        assertNull(LegacyBackupImporter.categoryForPath("/db.json"))
        assertNull(LegacyBackupImporter.categoryForPath("..\\db.json"))
        assertNull(LegacyBackupImporter.categoryForPath("a\u0000b"))
        assertNull(LegacyBackupImporter.categoryForPath("store/../db.json"))
        assertNull(LegacyBackupImporter.categoryForPath("store/unknown.json"))
        assertNull(LegacyBackupImporter.categoryForPath("subscription/1.exe"))
        assertEquals("subscriptions", LegacyBackupImporter.categoryForPath("db.json"))
    }

    @Test
    fun readRejectsEmptyAndUnknownDirectoryArchives() {
        val empty = temporaryFolder.newFile("empty.zip")
        ZipOutputStream(empty.outputStream()).use { output ->
            output.writeDir("store")
        }
        val emptyResult = LegacyBackupImporter.read(empty)
        assertEquals(BackupErrorCode.INVALID_PAYLOAD, (emptyResult as BackupResult.Failure).code)

        val unknown = temporaryFolder.newFile("unknown.zip")
        ZipOutputStream(unknown.outputStream()).use { output ->
            output.writeEntry("store/store.json", "{}")
            output.writeDir("private")
        }
        val unknownResult = LegacyBackupImporter.read(unknown)
        assertEquals(BackupErrorCode.INVALID_PAYLOAD, (unknownResult as BackupResult.Failure).code)
    }

    @Test
    fun readStoreOnlyCreatesSettingsObject() {
        val archive = temporaryFolder.newFile("store-only.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            output.writeEntry("store/store.json", """{"enableMatch":true}""")
        }

        val result = LegacyBackupImporter.read(archive)

        assertTrue(result is BackupResult.Success)
        val payload = (result as BackupResult.Success).value
        assertEquals(listOf("settings"), payload.manifest.categoryIds)
        assertEquals("settings", payload.objects.single().objectId)
    }

    @Test
    fun readSubscriptionOnlyCreatesFileObjectWithoutDb() {
        val archive = temporaryFolder.newFile("subscription-only.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            output.writeEntry("subscription/1.json", """{"id":1}""")
        }

        val result = LegacyBackupImporter.read(archive)

        assertTrue(result is BackupResult.Success)
        val payload = (result as BackupResult.Success).value
        assertEquals(1, payload.objects.count { it.objectId.startsWith("subscription-file-") })
    }

    @Test
    fun readRejectsMalformedLegacyDatabase() {
        val archive = temporaryFolder.newFile("bad-db.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            output.writeEntry("db.json", "not-json")
            output.writeEntry("subscription/1.json", """{"id":1}""")
        }

        val result = LegacyBackupImporter.read(archive)

        assertEquals(BackupErrorCode.INVALID_PAYLOAD, (result as BackupResult.Failure).code)
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.encodeToByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.writeDir(name: String) {
        putNextEntry(ZipEntry("$name/"))
        closeEntry()
    }
}
