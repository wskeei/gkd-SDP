package li.songe.gkd.sdp.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import li.songe.gkd.sdp.backup.BackupDataMutationBarrier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StorageMutationBarrierTest {
    @Test
    fun `normal store mutation waits until backup replacement releases barrier`() = runBlocking {
        val flow = MutableStoreStateFlow(
            filename = "test.txt",
            decode = { it?.toIntOrNull() ?: 0 },
            encode = Int::toString,
            stateFlow = MutableStateFlow(0),
        )
        val barrierHeld = CompletableDeferred<Unit>()
        val releaseBarrier = CompletableDeferred<Unit>()
        val import = async(Dispatchers.Default) {
            BackupDataMutationBarrier.withMutation {
                barrierHeld.complete(Unit)
                releaseBarrier.await()
            }
        }
        barrierHeld.await()

        val mutationStarted = CompletableDeferred<Unit>()
        val concurrentMutation = async(Dispatchers.Default) {
            mutationStarted.complete(Unit)
            flow.value = 1
        }
        mutationStarted.await()
        yield()

        assertFalse(concurrentMutation.isCompleted)
        assertEquals(0, flow.value)
        releaseBarrier.complete(Unit)
        import.await()
        concurrentMutation.await()
        assertEquals(1, flow.value)
    }
}
