package li.songe.gkd.sdp.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupImportTargetDefaultsTest {
    private class DefaultTarget : BackupImportTarget {
        override suspend fun validateReferences(payload: BackupPayload): Boolean = true

        override suspend fun capture(categoryIds: Set<String>): BackupPayload =
            throw UnsupportedOperationException()

        override suspend fun preview(
            previous: BackupPayload,
            incoming: BackupPayload,
        ): List<BackupConflictPreview> = emptyList()

        override suspend fun replaceIncludedCategories(payload: BackupPayload) = Unit

        override suspend fun restore(previous: BackupPayload) = Unit

        override suspend fun reconcileRuntime() = Unit
    }

    @Test
    fun defaultExclusiveMutationRunsBlockThenAfterCommit() = runBlocking {
        val target = DefaultTarget()
        var afterCommit = 0

        val result = target.withExclusiveMutation(
            block = { "ok" },
            afterCommit = { afterCommit++ },
        )

        assertEquals("ok", result)
        assertEquals(1, afterCommit)
    }

    @Test
    fun defaultRecoveryMutationRunsBlock() = runBlocking {
        assertEquals("recovered", DefaultTarget().withRecoveryMutation { "recovered" })
    }
}
