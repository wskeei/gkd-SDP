package li.songe.gkd.sdp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import li.songe.gkd.sdp.data.SubsItem
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.writeTextAtomically
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal const val SNAPSHOT_DELETE_STAGING_PREFIX = ".snapshot-delete-"
internal const val SUBSCRIPTION_MUTATION_STAGING_PREFIX = ".subs-mutation-"
internal const val PENDING_MUTATION_MANIFEST = "manifest.json"
internal const val PENDING_KIND_SUBSCRIPTION_UPSERT = "subscription-upsert"
internal const val PENDING_KIND_SUBSCRIPTION_DELETE = "subscription-delete"
internal const val PENDING_KIND_SNAPSHOT_DELETE = "snapshot-delete"
internal const val PENDING_PHASE_STAGED = "staged"
internal const val PENDING_PHASE_COMMITTED = "committed"

@Serializable
internal data class PendingDataMutationManifest(
    val version: Int = 1,
    val kind: String,
    val ids: List<Long>,
    val phase: String = PENDING_PHASE_STAGED,
    val targetExisted: Boolean = false,
    val expectedMtime: Long? = null,
    val requiredPreviousMtime: Long? = null,
    val expectedSubsItem: SubsItem? = null,
)

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

internal fun writePendingDataMutationManifest(
    directory: File,
    manifest: PendingDataMutationManifest,
) {
    writeTextAtomically(
        directory.resolve(PENDING_MUTATION_MANIFEST),
        json.encodeToString(manifest),
    )
}

suspend fun retryPendingDataCleanup(): Boolean = withContext(Dispatchers.IO) {
    val candidates = listOf(
        requireNotNull(snapshotFolder.parentFile),
        subsFolder,
    ).flatMap { root ->
        root.listFiles().orEmpty().filter { candidate ->
            candidate.isDirectory && (
                candidate.name.startsWith(SNAPSHOT_DELETE_STAGING_PREFIX) ||
                    candidate.name.startsWith(SUBSCRIPTION_MUTATION_STAGING_PREFIX)
                )
        }
    }
    candidates.all { candidate -> recoverPendingMutation(candidate) }
}

private suspend fun recoverPendingMutation(directory: File): Boolean {
    val manifest = runCatching {
        json.decodeFromString<PendingDataMutationManifest>(
            directory.resolve(PENDING_MUTATION_MANIFEST).readText(),
        )
    }.getOrNull() ?: return false
    if (
        manifest.version != 1 ||
        manifest.ids.isEmpty() ||
        manifest.phase !in setOf(PENDING_PHASE_STAGED, PENDING_PHASE_COMMITTED)
    ) return false

    return runCatching {
        when (manifest.kind) {
            PENDING_KIND_SUBSCRIPTION_UPSERT -> recoverSubscriptionUpsert(directory, manifest)
            PENDING_KIND_SUBSCRIPTION_DELETE -> recoverSubscriptionDelete(directory, manifest)
            PENDING_KIND_SNAPSHOT_DELETE -> recoverSnapshotDelete(directory, manifest)
            else -> false
        }
    }.getOrDefault(false)
}

private suspend fun recoverSubscriptionUpsert(
    directory: File,
    manifest: PendingDataMutationManifest,
): Boolean {
    if (manifest.ids.size != 1) return false
    val id = manifest.ids.single()
    val current = DbSet.subsItemDao.queryById(id)
    val committed = current != null && when {
        manifest.expectedSubsItem != null -> current == manifest.expectedSubsItem
        manifest.expectedMtime != null &&
            manifest.requiredPreviousMtime == null -> current.mtime == manifest.expectedMtime
        manifest.expectedMtime != null -> current.mtime == manifest.expectedMtime
        else -> false
    }
    val target = subsFolder.resolve("$id.json")
    val stagedNew = directory.resolve("new.json")
    if (committed) {
        if (stagedNew.exists()) {
            if (target.exists()) target.delete()
            movePath(stagedNew, target)
        }
    } else {
        val stagedPrevious = directory.resolve("previous.json")
        if (stagedPrevious.exists()) {
            if (target.exists()) target.delete()
            movePath(stagedPrevious, target)
        } else if (target.exists() && !manifest.targetExisted) {
            target.delete()
        }
    }
    return deleteDirectory(directory)
}

private suspend fun recoverSubscriptionDelete(
    directory: File,
    manifest: PendingDataMutationManifest,
): Boolean {
    val committed = manifest.ids.all { DbSet.subsItemDao.queryById(it) == null }
    if (!committed) {
        manifest.ids.forEach { id ->
            val staged = directory.resolve("$id.json")
            if (staged.exists()) {
                val target = subsFolder.resolve("$id.json")
                if (target.exists()) target.delete()
                movePath(staged, target)
            }
        }
    }
    return deleteDirectory(directory)
}

private suspend fun recoverSnapshotDelete(
    directory: File,
    manifest: PendingDataMutationManifest,
): Boolean {
    val committed = manifest.ids.all { DbSet.snapshotDao.queryById(it) == null }
    if (!committed) {
        manifest.ids.forEach { id ->
            val staged = directory.resolve(id.toString())
            if (staged.exists()) {
                val target = snapshotFolder.resolve(id.toString())
                if (target.exists()) target.deleteRecursively()
                movePath(staged, target)
            }
        }
    }
    return deleteDirectory(directory)
}

private fun deleteDirectory(directory: File): Boolean =
    !directory.exists() || directory.deleteRecursively()

private fun movePath(source: File, target: File) {
    target.parentFile?.mkdirs()
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source.toPath(), target.toPath())
    }
}
