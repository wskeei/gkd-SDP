package li.songe.gkd.sdp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal const val SNAPSHOT_DELETE_STAGING_PREFIX = ".snapshot-delete-"
internal const val SUBSCRIPTION_MUTATION_STAGING_PREFIX = ".subs-mutation-"

class PendingDataCleanupException : IllegalStateException("pending_data_cleanup")

internal object PendingDataCleanupPolicy {
    fun cleanup(
        roots: Collection<File>,
        deleteDirectory: (File) -> Boolean = { directory -> directory.deleteRecursively() },
    ): List<File> = roots
        .flatMap { root -> root.listFiles().orEmpty().asList() }
        .filter { candidate ->
            candidate.isDirectory && (
                candidate.name.startsWith(SNAPSHOT_DELETE_STAGING_PREFIX) ||
                    candidate.name.startsWith(SUBSCRIPTION_MUTATION_STAGING_PREFIX)
                )
        }
        .filterNot(deleteDirectory)
}

fun requirePendingDataCleanup(directory: File) {
    if (directory.exists() && !directory.deleteRecursively()) {
        throw PendingDataCleanupException()
    }
}

suspend fun retryPendingDataCleanup(): Boolean = withContext(Dispatchers.IO) {
    PendingDataCleanupPolicy.cleanup(
        roots = listOf(
            requireNotNull(snapshotFolder.parentFile),
            subsFolder,
        ),
    ).isEmpty()
}
