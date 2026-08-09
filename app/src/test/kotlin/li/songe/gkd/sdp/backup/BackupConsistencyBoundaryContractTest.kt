package li.songe.gkd.sdp.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupConsistencyBoundaryContractTest {
    @Test
    fun exportCaptureAndImportShareOneConsistentBoundary() {
        val repository = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/backup/AppBackupRepository.kt",
        ).readText()
        val collect = repository
            .substringAfter("override suspend fun collect(")
            .substringBefore("private fun collectConsistent(")
        val exclusiveMutation = repository
            .substringAfter("override suspend fun <T> withExclusiveMutation(")
            .substringBefore("override suspend fun collect(")

        assertTrue(collect.contains("BackupDataMutationBarrier.withConsistentDataSnapshot"))
        assertTrue(collect.contains("DbSet.withRawTransaction"))
        assertTrue(exclusiveMutation.contains("BackupDataMutationBarrier.withConsistentDataSnapshot"))
        assertTrue(exclusiveMutation.contains("DbSet.withTransaction"))
        assertTrue(exclusiveMutation.contains("commitPendingDataReplacements"))
        assertTrue(exclusiveMutation.contains("withContext(NonCancellable)"))
        assertTrue(repository.contains("override suspend fun <T> withRecoveryMutation"))
        assertTrue(repository.contains("override suspend fun restore(previous: BackupPayload)"))
        assertFalse(
            repository.substringAfter("override suspend fun restore(previous: BackupPayload)")
                .substringBefore("override suspend fun reconcileRuntime()")
                .contains("commitPendingDataReplacements"),
        )
        assertTrue(repository.contains("override suspend fun capture(categoryIds: Set<String>): BackupPayload = collect(categoryIds)"))
    }

    @Test
    fun snapshotRowsAndFilesUseOneMutationApi() {
        val snapshotExt = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/util/SnapshotExt.kt",
        ).readText()
        val snapshotPage = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/ui/SnapshotPage.kt",
        ).readText()
        val httpService = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/HttpService.kt",
        ).readText()
        val deletion = snapshotExt
            .substringAfter("suspend fun deleteSnapshots(")
            .substringBefore("private fun moveSnapshotDirectory(")

        assertTrue(deletion.contains("BackupDataMutationBarrier.withMutation"))
        assertTrue(deletion.contains("DbSet.withTransaction"))
        assertTrue(deletion.contains("DbSet.snapshotDao.delete"))
        assertTrue(snapshotPage.contains("SnapshotExt.deleteSnapshot(snapshotVal)"))
        assertTrue(snapshotPage.contains("SnapshotExt.deleteSnapshots(snapshots)"))
        assertTrue(httpService.contains("SnapshotExt.deleteSnapshot(snapshot)"))
        assertFalse(snapshotPage.contains("SnapshotExt.removeSnapshot"))
        assertFalse(httpService.contains("SnapshotExt.removeSnapshot"))
    }

    @Test
    fun importRecoveryUsesTerminalJournalAndHeldRecoveryTransaction() {
        val coordinator = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupImportCoordinator.kt",
        ).readText()
        val journal = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupImportJournal.kt",
        ).readText()
        val appDb = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt").readText()
        val gatedDao = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/backup/GatedRoomDao.kt",
        ).readText()

        assertTrue(coordinator.contains("BackupImportPhase.ROLLED_BACK"))
        assertTrue(coordinator.contains("journal.write(commitRecord.copy(phase = BackupImportPhase.COMMITTED))"))
        assertTrue(coordinator.contains("withRecoveryMutation"))
        assertTrue(coordinator.contains("withContext(NonCancellable)"))
        assertTrue(coordinator.contains("persistRollbackTerminal"))
        assertTrue(coordinator.contains("afterCommit = { persistRollbackTerminal(record) }"))
        assertTrue(journal.contains("suspend fun clear(): Boolean"))
        assertTrue(journal.contains("BackupImportRecoveryBlockedException"))
        assertTrue(journal.contains("withBackupImportRecoveryContext"))
        assertTrue(journal.contains("if (!file.isFile) return null"))
        assertFalse(journal.contains("getOrNull()"))
        assertTrue(appDb.contains("withBackupDataMutationGate"))
        assertTrue(appDb.contains("gateRoomDao"))
        assertTrue(gatedDao.contains("withBackupDataMutationGate"))
        assertTrue(gatedDao.contains("startCoroutine"))
    }

    private fun sourceFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return File(relativePath)
        }
        return File(relativePath)
    }
}
