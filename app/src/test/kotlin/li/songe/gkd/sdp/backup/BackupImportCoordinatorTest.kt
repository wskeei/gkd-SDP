package li.songe.gkd.sdp.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupImportCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `prepare decrypts validates and previews without modifying target`() = runBlocking {
        val incoming = payload("new")
        val target = FakeImportTarget(payload("old"))
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)

        val prepared = coordinator.prepare(
            encryptedPayload(incoming),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        assertEquals(listOf("validate", "capture", "preview"), target.events)
        assertEquals("old", target.current.objects.single().content.decodeToString())
        assertEquals(
            listOf(BackupConflictPreview("settings", added = 0, overwritten = 1, deleted = 0)),
            prepared.value.conflicts,
        )
        assertTrue(journal.writes.isEmpty())

        val notConfirmed = coordinator.apply(prepared.value, confirmed = false)
        assertEquals(
            BackupErrorCode.IMPORT_NOT_CONFIRMED,
            (notConfirmed as BackupResult.Failure).code,
        )
        assertEquals("old", target.current.objects.single().content.decodeToString())
    }

    @Test
    fun `legacy payload uses the same validation preview and transaction path`() = runBlocking {
        val incoming = payload("legacy")
        val target = FakeImportTarget(payload("old"))
        val coordinator = coordinator(target, RecordingJournal())

        val result = coordinator.preparePayload(
            payload = incoming,
            sourceFormat = BackupSourceFormat.LEGACY_V1,
        ) as BackupResult.Success<PreparedBackupImport>

        assertEquals(BackupSourceFormat.LEGACY_V1, result.value.sourceFormat)
        assertEquals(listOf("validate", "capture", "preview"), target.events)
        assertEquals("old", target.current.objects.single().content.decodeToString())
    }

    @Test
    fun `confirmed import journals replaces included categories and reconciles`() = runBlocking {
        val target = FakeImportTarget(payload("old"))
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        val result = coordinator.apply(prepared.value, confirmed = true)

        assertTrue(result is BackupResult.Success)
        assertEquals("new", target.current.objects.single().content.decodeToString())
        assertEquals(
            listOf(
                BackupImportPhase.PREPARED,
                BackupImportPhase.APPLYING,
                BackupImportPhase.COMMITTED,
            ),
            journal.writes.map(BackupImportJournalRecord::phase),
        )
        assertTrue(target.events.contains("replace"))
        assertTrue(target.events.contains("reconcile"))
        assertTrue(journal.cleared)
    }

    @Test
    fun `failed apply immediately restores captured stores and room rows`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply { failAfterReplace = true }
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        val result = coordinator.apply(prepared.value, confirmed = true)

        assertEquals(BackupErrorCode.IMPORT_FAILED, (result as BackupResult.Failure).code)
        assertEquals("old", target.current.objects.single().content.decodeToString())
        assertTrue(target.events.contains("restore"))
        assertFalse(target.events.contains("reconcile"))
        assertTrue(journal.cleared)
    }

    @Test
    fun `startup rolls back applying journal before interface can open`() = runBlocking {
        val original = payload("old")
        val target = FakeImportTarget(payload("partially-applied"))
        val journal = RecordingJournal().apply {
            current = BackupImportJournalRecord(
                phase = BackupImportPhase.APPLYING,
                payloadHash = payload("new").payloadHash,
                previousState = original,
            )
        }
        val coordinator = coordinator(target, journal)

        coordinator.recoverInterruptedImport()

        assertEquals("old", target.current.objects.single().content.decodeToString())
        assertEquals(listOf("restore"), target.events)
        assertTrue(journal.cleared)
    }

    @Test
    fun `reference failure blocks capture journal and mutation`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply { referencesValid = false }
        val journal = RecordingJournal()
        val result = coordinator(target, journal).prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        )

        assertEquals(BackupErrorCode.REFERENCE_MISMATCH, (result as BackupResult.Failure).code)
        assertEquals(listOf("validate"), target.events)
        assertTrue(journal.writes.isEmpty())
        assertEquals("old", target.current.objects.single().content.decodeToString())
    }

    private fun coordinator(
        target: FakeImportTarget,
        journal: RecordingJournal,
    ) = BackupImportCoordinator(
        target = target,
        journal = journal,
        tempDirectoryFactory = { temporaryFolder.newFolder("import-${System.nanoTime()}") },
    )

    private fun encryptedPayload(payload: BackupPayload): ByteArray {
        val zip = temporaryFolder.newFile("payload-${System.nanoTime()}.zip")
        BackupPayloadArchive.build(zip, payload.objects)
        return (BackupCrypto().encrypt(
            zip.readBytes(),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<ByteArray>).value
    }

    private fun payload(value: String): BackupPayload {
        val objectValue = BackupPayloadObject(
            objectId = "settings",
            categoryId = "settings",
            schema = 1,
            count = 1,
            content = value.encodeToByteArray(),
        )
        val zip = temporaryFolder.newFile("plain-${System.nanoTime()}.zip")
        BackupPayloadArchive.build(zip, listOf(objectValue))
        return (BackupPayloadArchive.read(zip) as BackupResult.Success<BackupPayload>).value
    }

    private class FakeImportTarget(initial: BackupPayload) : BackupImportTarget {
        var current = initial
        var referencesValid = true
        var failAfterReplace = false
        val events = mutableListOf<String>()

        override suspend fun validateReferences(payload: BackupPayload): Boolean {
            events += "validate"
            return referencesValid
        }

        override suspend fun capture(categoryIds: Set<String>): BackupPayload {
            events += "capture"
            return current
        }

        override suspend fun preview(
            previous: BackupPayload,
            incoming: BackupPayload,
        ): List<BackupConflictPreview> {
            events += "preview"
            return listOf(
                BackupConflictPreview(
                    categoryId = "settings",
                    added = 0,
                    overwritten = 1,
                    deleted = 0,
                ),
            )
        }

        override suspend fun replaceIncludedCategories(payload: BackupPayload) {
            events += "replace"
            current = payload
            if (failAfterReplace) error("synthetic failure")
        }

        override suspend fun restore(previous: BackupPayload) {
            events += "restore"
            current = previous
        }

        override suspend fun reconcileRuntime() {
            events += "reconcile"
        }
    }

    private class RecordingJournal : BackupImportJournal {
        val writes = mutableListOf<BackupImportJournalRecord>()
        var current: BackupImportJournalRecord? = null
        var cleared = false

        override suspend fun read(): BackupImportJournalRecord? = current

        override suspend fun write(record: BackupImportJournalRecord) {
            current = record
            writes += record
        }

        override suspend fun clear() {
            current = null
            cleared = true
        }
    }
}
