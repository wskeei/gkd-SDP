package li.songe.gkd.sdp.backup

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal interface BackupDataMutationParticipant {
    fun beginConsistentSnapshot()
    fun commitConsistentSnapshot()
    fun finishConsistentSnapshot()
}

/**
 * Serializes manual backup snapshots/replacement with managed files and Room mutations.
 *
 * Synchronous StateFlow setters must never wait for this coroutine mutex. Registered
 * participants therefore buffer their mutations while a consistent snapshot is active and
 * replay them after the snapshot/import has finished. The context marker makes nested calls
 * from the import transaction re-entrant without weakening the outer exclusion boundary.
 */
object BackupDataMutationBarrier {
    private object HeldKey : CoroutineContext.Key<HeldContext>
    private class HeldContext : AbstractCoroutineContextElement(HeldKey)

    private val mutex = Mutex()
    private val participants = CopyOnWriteArraySet<BackupDataMutationParticipant>()
    private val participantStateLock = Any()
    private var consistentSnapshotActive = false
    private val activeParticipants = linkedSetOf<BackupDataMutationParticipant>()

    internal fun register(participant: BackupDataMutationParticipant) {
        synchronized(participantStateLock) {
            participants += participant
            if (consistentSnapshotActive && activeParticipants.add(participant)) {
                participant.beginConsistentSnapshot()
            }
        }
    }

    suspend fun <T> withMutation(block: suspend () -> T): T {
        if (currentCoroutineContext()[HeldKey] != null) return block()
        return mutex.withLock {
            withContext(HeldContext()) { block() }
        }
    }

    suspend fun <T> withConsistentDataSnapshot(block: suspend () -> T): T {
        if (currentCoroutineContext()[HeldKey] != null) return block()
        return mutex.withLock {
            beginConsistentSnapshot()
            try {
                withContext(HeldContext()) { block() }
            } finally {
                finishConsistentSnapshot()
            }
        }
    }

    suspend fun commitPendingDataReplacements() {
        check(currentCoroutineContext()[HeldKey] != null)
        synchronized(participantStateLock) {
            check(consistentSnapshotActive)
            activeParticipants.forEach(BackupDataMutationParticipant::commitConsistentSnapshot)
        }
    }

    private fun beginConsistentSnapshot() {
        synchronized(participantStateLock) {
            check(!consistentSnapshotActive)
            consistentSnapshotActive = true
            try {
                participants.forEach { participant ->
                    participant.beginConsistentSnapshot()
                    activeParticipants += participant
                }
            } catch (error: Throwable) {
                activeParticipants.toList().asReversed().forEach { participant ->
                    runCatching { participant.finishConsistentSnapshot() }
                }
                activeParticipants.clear()
                consistentSnapshotActive = false
                throw error
            }
        }
    }

    private fun finishConsistentSnapshot() {
        synchronized(participantStateLock) {
            var firstFailure: Throwable? = null
            activeParticipants.toList().asReversed().forEach { participant ->
                try {
                    participant.finishConsistentSnapshot()
                } catch (error: Throwable) {
                    if (firstFailure == null) firstFailure = error
                }
            }
            activeParticipants.clear()
            consistentSnapshotActive = false
            firstFailure?.let { throw it }
        }
    }
}
