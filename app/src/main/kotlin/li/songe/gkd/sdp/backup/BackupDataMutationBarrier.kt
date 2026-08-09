package li.songe.gkd.sdp.backup

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes manual backup replacement with non-Room stores and managed files.
 * Room mutations are additionally fenced by the outer AppDb transaction used by
 * [AppBackupRepository]. Import internals bypass this barrier only while that
 * outer transaction already owns it.
 */
object BackupDataMutationBarrier {
    private val mutex = Mutex()

    suspend fun <T> withMutation(block: suspend () -> T): T = mutex.withLock { block() }

    fun <T> mutateBlocking(block: () -> T): T = runBlocking {
        mutex.withLock { block() }
    }
}
