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
