package li.songe.gkd.sdp.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import li.songe.gkd.sdp.backup.BackupDataMutationBarrier
import java.io.File
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StorageMutationBarrierTest {
    @Test
    fun `synchronous store mutation never blocks on the coroutine barrier`() {
        val storageSource = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/store/StorageExt.kt",
        ).readText()
        val barrierSource = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupDataMutationBarrier.kt",
        ).readText()

        assertFalse(storageSource.contains("mutateBlocking"))
        assertFalse(barrierSource.contains("runBlocking"))
        assertFalse(barrierSource.contains("mutateBlocking"))
    }

    @Test
    fun `normal store mutation returns immediately and replays over imported value`() = runBlocking {
        val committedState = MutableStateFlow(0)
        val flow = MutableStoreStateFlow(
            filename = "test.txt",
            decode = { it?.toIntOrNull() ?: 0 },
            encode = Int::toString,
            stateFlow = committedState,
        )
        val barrierHeld = CompletableDeferred<Unit>()
        val releaseBarrier = CompletableDeferred<Unit>()
        val import = async(Dispatchers.Default) {
            BackupDataMutationBarrier.withConsistentDataSnapshot {
                barrierHeld.complete(Unit)
                releaseBarrier.await()
                flow.updateByDecode("40")
            }
        }
        barrierHeld.await()

        val mutationStarted = CompletableDeferred<Unit>()
        val concurrentMutation = async(Dispatchers.Default) {
            mutationStarted.complete(Unit)
            flow.update { it + 1 }
        }
        mutationStarted.await()
        concurrentMutation.await()

        assertEquals(1, flow.value)
        assertEquals(0, committedState.value)
        releaseBarrier.complete(Unit)
        import.await()
        assertEquals(41, flow.value)
        assertEquals(41, committedState.value)
    }

    @Test
    fun `cooperative file mutation waits for consistent export snapshot`() = runBlocking {
        val snapshotStarted = CompletableDeferred<Unit>()
        val finishSnapshot = CompletableDeferred<Unit>()
        val snapshot = async(Dispatchers.Default) {
            BackupDataMutationBarrier.withConsistentDataSnapshot {
                snapshotStarted.complete(Unit)
                finishSnapshot.await()
            }
        }
        snapshotStarted.await()

        val fileMutation = async(Dispatchers.Default) {
            BackupDataMutationBarrier.withMutation { "deleted" }
        }
        yield()

        assertFalse(fileMutation.isCompleted)
        finishSnapshot.complete(Unit)
        snapshot.await()
        assertEquals("deleted", fileMutation.await())
    }

    @Test
    fun `main thread store update returns while barrier owner is suspended on io`() = runBlocking {
        val mainDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "store-test-main").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        val committedState = MutableStateFlow(0)
        val flow = MutableStoreStateFlow(
            filename = "main-thread-test.txt",
            decode = { it?.toIntOrNull() ?: 0 },
            encode = Int::toString,
            stateFlow = committedState,
        )
        val ioStarted = CompletableDeferred<Unit>()
        val releaseIo = CompletableDeferred<Unit>()
        try {
            val barrierOwner = CoroutineScope(mainDispatcher).async {
                BackupDataMutationBarrier.withConsistentDataSnapshot {
                    withContext(Dispatchers.IO) {
                        ioStarted.complete(Unit)
                        releaseIo.await()
                    }
                }
            }
            ioStarted.await()

            val mainMutation = CoroutineScope(mainDispatcher).async {
                flow.update { it + 1 }
            }
            withTimeout(1_000) { mainMutation.await() }

            assertEquals(1, flow.value)
            assertEquals(0, committedState.value)
            releaseIo.complete(Unit)
            barrierOwner.await()
            assertEquals(1, committedState.value)
        } finally {
            releaseIo.complete(Unit)
            mainDispatcher.close()
        }
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
