package li.songe.gkd.sdp.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
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
    fun `runtime reconcile failure rolls committed data back before reporting failure`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply {
            reconcileFailuresRemaining = 1
        }
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        val result = coordinator.apply(prepared.value, confirmed = true)

        assertEquals(BackupErrorCode.IMPORT_FAILED, (result as BackupResult.Failure).code)
        assertEquals("old", target.current.objects.single().content.decodeToString())
        assertEquals(2, target.events.count { it == "reconcile" })
        assertTrue(target.events.contains("restore"))
        assertTrue(journal.cleared)
        assertEquals(BackupImportPhase.ROLLED_BACK, journal.writes.last().phase)
    }

    @Test
    fun `failed rollback returns recovery required and keeps applying journal`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply {
            failAfterReplace = true
            restoreFailuresRemaining = 1
        }
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        val result = coordinator.apply(prepared.value, confirmed = true)

        assertEquals(
            BackupErrorCode.IMPORT_RECOVERY_REQUIRED,
            (result as BackupResult.Failure).code,
        )
        assertFalse(journal.cleared)
        assertEquals(BackupImportPhase.APPLYING, journal.current?.phase)
    }

    @Test
    fun `cancellation restores through suspending target in non cancellable context`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply {
            pauseAfterReplace = true
            restoreChecksCancellation = true
        }
        val journal = RecordingJournal().apply { requireActiveWrite = true }
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        val apply = async { coordinator.apply(prepared.value, confirmed = true) }
        target.replaceStarted.await()
        apply.cancel()
        target.continueReplace.complete(Unit)
        runCatching { apply.await() }

        assertEquals("old", target.current.objects.single().content.decodeToString())
        assertTrue(target.events.contains("restore"))
        assertTrue(journal.cleared)
        assertEquals(BackupImportPhase.ROLLED_BACK, journal.writes.last().phase)
        target.current = payload("new-history")
        coordinator.recoverInterruptedImport()
        assertEquals("new-history", target.current.objects.single().content.decodeToString())
    }

    @Test
    fun `cancellation rollback completes before queued writer enters mutation`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply {
            pauseAfterReplace = true
        }
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        val apply = async { coordinator.apply(prepared.value, confirmed = true) }
        target.replaceStarted.await()
        val writer = async { target.writeHistory(payload("new-history")) }
        apply.cancel()
        target.continueReplace.complete(Unit)
        runCatching { apply.await() }
        writer.await()

        assertEquals("new-history", target.current.objects.single().content.decodeToString())
    }

    @Test
    fun `rollback journal terminal state survives clear failure`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply { failAfterReplace = true }
        val journal = RecordingJournal().apply { clearResult = false }
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        try {
            val result = coordinator.apply(prepared.value, confirmed = true)
            assertEquals(
                BackupErrorCode.IMPORT_RECOVERY_REQUIRED,
                (result as BackupResult.Failure).code,
            )
            assertEquals(BackupImportPhase.ROLLED_BACK, journal.current?.phase)
            assertFalse(journal.cleared)
        } finally {
            unblockBackupImportRecovery()
        }
    }

    @Test
    fun `transaction return cancellation sees durable rollback terminal`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply {
            failAfterReplace = true
            cancelAfterMutationReturn = true
        }
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        runCatching { coordinator.apply(prepared.value, confirmed = true) }

        assertEquals("old", target.current.objects.single().content.decodeToString())
        assertEquals(BackupImportPhase.ROLLED_BACK, journal.writes.last().phase)
        assertTrue(journal.cleared)
    }

    @Test
    fun `commit return cancellation runs post commit callback before propagating`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply {
            cancelAfterMutationReturn = true
        }
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        runCatching { coordinator.apply(prepared.value, confirmed = true) }

        assertEquals("new", target.current.objects.single().content.decodeToString())
        assertEquals(BackupImportPhase.COMMITTED, journal.writes.last().phase)
        assertTrue(journal.cleared)
    }

    @Test
    fun `post commit cancellation does not restore twice after recovery clears journal`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply {
            cancelDuringReconcile = true
        }
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        val apply = async { coordinator.apply(prepared.value, confirmed = true) }
        target.reconcileStarted.await()
        apply.cancel()
        target.continueReconcile.complete(Unit)
        runCatching { apply.await() }

        assertEquals("old", target.current.objects.single().content.decodeToString())
        assertEquals(1, target.events.count { it == "restore" })
        assertTrue(journal.cleared)
        target.current = payload("after-recovery")
        coordinator.recoverInterruptedImport()
        assertEquals("after-recovery", target.current.objects.single().content.decodeToString())
    }

    @Test
    fun `apply rejects a stale preview without journaling or modifying newer data`() = runBlocking {
        val target = FakeImportTarget(payload("old"))
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>
        target.current = payload("intervening")
        target.events.clear()

        val result = coordinator.apply(prepared.value, confirmed = true)

        assertEquals(
            BackupErrorCode.IMPORT_PREVIEW_STALE,
            (result as BackupResult.Failure).code,
        )
        assertEquals("intervening", target.current.objects.single().content.decodeToString())
        assertEquals(listOf("capture"), target.events)
        assertTrue(journal.writes.isEmpty())
    }

    @Test
    fun `refreshed preview rolls back to the latest state when apply fails`() = runBlocking {
        val target = FakeImportTarget(payload("old"))
        val journal = RecordingJournal()
        val coordinator = coordinator(target, journal)
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>
        target.current = payload("intervening")

        val refreshed = coordinator.refreshPreview(prepared.value)
            as BackupResult.Success<PreparedBackupImport>
        target.failAfterReplace = true
        val result = coordinator.apply(refreshed.value, confirmed = true)

        assertEquals(BackupErrorCode.IMPORT_FAILED, (result as BackupResult.Failure).code)
        assertEquals("intervening", target.current.objects.single().content.decodeToString())
        assertEquals(
            "intervening",
            journal.writes.first().previousState.objects.single().content.decodeToString(),
        )
    }

    @Test
    fun `concurrent history write waits for failed import rollback and is preserved`() = runBlocking {
        val target = FakeImportTarget(payload("old")).apply {
            failAfterReplace = true
            pauseAfterReplace = true
        }
        val coordinator = coordinator(target, RecordingJournal())
        val prepared = coordinator.prepare(
            encryptedPayload(payload("new")),
            "correct-password".toCharArray(),
        ) as BackupResult.Success<PreparedBackupImport>

        val applyResult = async { coordinator.apply(prepared.value, confirmed = true) }
        target.replaceStarted.await()
        val concurrentWrite = async { target.writeHistory(payload("concurrent")) }
        yield()

        assertFalse(concurrentWrite.isCompleted)
        target.continueReplace.complete(Unit)
        assertEquals(
            BackupErrorCode.IMPORT_FAILED,
            (applyResult.await() as BackupResult.Failure).code,
        )
        concurrentWrite.await()
        assertEquals("concurrent", target.current.objects.single().content.decodeToString())
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
        private val mutationMutex = Mutex()
        var current = initial
        var referencesValid = true
        var failAfterReplace = false
        var pauseAfterReplace = false
        var reconcileFailuresRemaining = 0
        var restoreFailuresRemaining = 0
        var restoreChecksCancellation = false
        var cancelDuringReconcile = false
        var cancelAfterMutationReturn = false
        val replaceStarted = CompletableDeferred<Unit>()
        val continueReplace = CompletableDeferred<Unit>()
        val reconcileStarted = CompletableDeferred<Unit>()
        val continueReconcile = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        override suspend fun <T> withExclusiveMutation(
            block: suspend () -> T,
            afterCommit: suspend (T) -> Unit,
        ): T {
            val result = mutationMutex.withLock { block() }
            if (cancelAfterMutationReturn) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    afterCommit(result)
                }
                throw CancellationException("transaction-return")
            }
            return result.also { afterCommit(it) }
        }

        suspend fun writeHistory(payload: BackupPayload) = mutationMutex.withLock {
            events += "concurrent-write"
            current = payload
        }

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
            if (pauseAfterReplace) {
                replaceStarted.complete(Unit)
                continueReplace.await()
            }
            if (failAfterReplace) error("synthetic failure")
        }

        override suspend fun restore(previous: BackupPayload) {
            events += "restore"
            if (restoreChecksCancellation) {
                yield()
                currentCoroutineContext().ensureActive()
            }
            if (restoreFailuresRemaining > 0) {
                restoreFailuresRemaining -= 1
                error("synthetic restore failure")
            }
            current = previous
        }

        override suspend fun reconcileRuntime() {
            events += "reconcile"
            if (cancelDuringReconcile) {
                cancelDuringReconcile = false
                reconcileStarted.complete(Unit)
                continueReconcile.await()
                currentCoroutineContext().ensureActive()
            }
            if (reconcileFailuresRemaining > 0) {
                reconcileFailuresRemaining -= 1
                error("synthetic reconcile failure")
            }
        }
    }

    private class RecordingJournal : BackupImportJournal {
        val writes = mutableListOf<BackupImportJournalRecord>()
        var current: BackupImportJournalRecord? = null
        var cleared = false
        var clearResult = true
        var requireActiveWrite = false

        override suspend fun read(): BackupImportJournalRecord? = current

        override suspend fun write(record: BackupImportJournalRecord) {
            if (requireActiveWrite) currentCoroutineContext().ensureActive()
            current = record
            writes += record
        }

        override suspend fun clear(): Boolean {
            if (clearResult) current = null
            cleared = clearResult
            return clearResult
        }
    }
}
