package li.songe.gkd.sdp.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMutationGateTest {
    @Test
    fun `queued database mutation rechecks recovery gate after predecessor releases`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val predecessor = async {
                withBackupDataMutationGate {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            val queued = async {
                runCatching {
                    withBackupDataMutationGate { "must-not-write" }
                }
            }
            yield()
            blockBackupImportRecovery()
            release.complete(Unit)
            predecessor.await()
            val result = queued.await()
            assertTrue(result.isFailure)
            assertEquals(
                BackupImportRecoveryBlockedException::class,
                result.exceptionOrNull()?.let { it::class },
            )
        } finally {
            unblockBackupImportRecovery()
        }
    }
}
