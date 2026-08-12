package li.songe.gkd.sdp.backup

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileBackupImportJournalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missingJournalReadsAsNull() = runBlocking {
        val journal = FileBackupImportJournal(temporaryFolder.root.resolve("journal.json"))
        assertNull(journal.read())
    }

    @Test
    fun writeReadAndClearRoundTrip() = runBlocking {
        val file = temporaryFolder.newFile("journal.json")
        val journal = FileBackupImportJournal(file)
        val record = BackupImportJournalRecord(
            phase = BackupImportPhase.APPLYING,
            payloadHash = "hash",
            previousState = BackupPayload(
                manifest = BackupPayloadManifest(
                    formatVersion = 2,
                    categoryIds = listOf("settings"),
                    objects = emptyList(),
                ),
                objects = emptyList(),
            ),
        )

        journal.write(record)
        assertEquals(record, journal.read())
        assertTrue(journal.clear())
        assertNull(journal.read())
        assertFalse(file.exists())
    }

    @Test
    fun clearIsIdempotentWithoutAnExistingJournal() = runBlocking {
        val file = temporaryFolder.root.resolve("journal.json")
        val journal = FileBackupImportJournal(file)

        assertTrue(journal.clear())
        assertNull(journal.read())
    }

    @Test
    fun clearRemovesLeftoverAtomicTempFile() = runBlocking {
        val file = temporaryFolder.newFile("journal.json")
        val tempFile = temporaryFolder.root.resolve("journal.json.tmp").apply { writeText("partial") }
        val journal = FileBackupImportJournal(file)

        assertTrue(journal.clear())
        assertFalse(file.exists())
        assertFalse(tempFile.exists())
    }
}
