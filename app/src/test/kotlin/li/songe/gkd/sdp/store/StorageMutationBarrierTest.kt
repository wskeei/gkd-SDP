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
import org.junit.Assert.fail
import org.junit.Test

class StorageMutationBarrierTest {
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
        val stageReplacement = CompletableDeferred<Unit>()
        val replacementStaged = CompletableDeferred<Unit>()
        val commitReplacement = CompletableDeferred<Unit>()
        val import = async(Dispatchers.Default) {
            BackupDataMutationBarrier.withConsistentDataSnapshot {
                barrierHeld.complete(Unit)
                stageReplacement.await()
                flow.updateByDecode("40")
                replacementStaged.complete(Unit)
                commitReplacement.await()
                BackupDataMutationBarrier.commitPendingDataReplacements()
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
        assertEquals(1, committedState.value)
        stageReplacement.complete(Unit)
        replacementStaged.await()
        assertEquals(1, flow.value)
        assertEquals(1, committedState.value)
        commitReplacement.complete(Unit)
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
            assertEquals(1, committedState.value)
            releaseIo.complete(Unit)
            barrierOwner.await()
            assertEquals(1, committedState.value)
        } finally {
            releaseIo.complete(Unit)
            mainDispatcher.close()
        }
    }

    @Test
    fun `queued persistence reads latest committed value after barrier release`() = runBlocking {
        val state = MutableStateFlow(0)
        val flow = MutableStoreStateFlow(
            filename = "persistence-order-test.txt",
            decode = { it?.toIntOrNull() ?: 0 },
            encode = Int::toString,
            stateFlow = state,
        )
        val snapshotStarted = CompletableDeferred<Unit>()
        val stageImport = CompletableDeferred<Unit>()
        val importStaged = CompletableDeferred<Unit>()
        val finishImport = CompletableDeferred<Unit>()
        val snapshot = async(Dispatchers.Default) {
            BackupDataMutationBarrier.withConsistentDataSnapshot {
                snapshotStarted.complete(Unit)
                stageImport.await()
                flow.updateByDecode("40")
                importStaged.complete(Unit)
                finishImport.await()
                BackupDataMutationBarrier.commitPendingDataReplacements()
            }
        }
        snapshotStarted.await()
        flow.update { it + 1 }

        var persistedValue = -1
        val queuedOldEmission = async(Dispatchers.Default) {
            BackupDataMutationBarrier.withMutation {
                persistedValue = state.value
            }
        }
        stageImport.complete(Unit)
        importStaged.await()
        assertFalse(queuedOldEmission.isCompleted)
        finishImport.complete(Unit)
        snapshot.await()
        queuedOldEmission.await()

        assertEquals(41, persistedValue)
    }

    @Test
    fun `failed snapshot discards staged replacement instead of publishing it`() = runBlocking {
        val state = MutableStateFlow(1)
        val flow = MutableStoreStateFlow(
            filename = "rollback-test.txt",
            decode = { it?.toIntOrNull() ?: 0 },
            encode = Int::toString,
            stateFlow = state,
        )

        try {
            BackupDataMutationBarrier.withConsistentDataSnapshot {
                flow.updateByDecode("40")
                error("synthetic import failure")
            }
            fail("expected import failure")
        } catch (_: IllegalStateException) {
            // The replacement must be discarded by the barrier cleanup.
        }

        assertEquals(1, flow.value)
        assertEquals(1, state.value)
    }

}
