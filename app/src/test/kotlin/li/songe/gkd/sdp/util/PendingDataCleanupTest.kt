package li.songe.gkd.sdp.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingDataCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `failed sensitive staging cleanup remains registered for startup retry`() {
        val root = temporaryFolder.newFolder("data")
        val staging = root.resolve("${SNAPSHOT_DELETE_STAGING_PREFIX}test").apply {
            mkdirs()
            resolve("snapshot.json").writeText("synthetic")
        }

        val failed = PendingDataCleanupPolicy.cleanup(listOf(root)) { false }

        assertEquals(listOf(staging), failed)
        assertTrue(staging.exists())

        val retryFailures = PendingDataCleanupPolicy.cleanup(listOf(root))
        assertTrue(retryFailures.isEmpty())
        assertFalse(staging.exists())
    }

    @Test
    fun `cleanup ignores unrelated directories`() {
        val root = temporaryFolder.newFolder("unrelated")
        val keep = root.resolve("snapshot").apply(File::mkdirs)

        assertTrue(PendingDataCleanupPolicy.cleanup(listOf(root)).isEmpty())
        assertTrue(keep.exists())
    }

    @Test
    fun `staged subscription recovery never overwrites a newer mtime`() {
        assertTrue(isCommittedSubscriptionMtime(20, 20, 10))
        assertTrue(isCommittedSubscriptionMtime(20, 20, null))
        assertFalse(isCommittedSubscriptionMtime(21, 20, 10))
        assertFalse(isCommittedSubscriptionMtime(20, 20, 20))
    }

    @Test
    fun `application startup retries registered cleanup before opening state`() {
        val source = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/App.kt").readText()
        val cleanup = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/util/PendingDataCleanup.kt",
        ).readText()
        val subscription = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/util/SubscriptionMutationRepository.kt",
        ).readText()
        val snapshot = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/util/SnapshotExt.kt",
        ).readText()
        val startup = source.substringAfter("override fun onCreate()")

        assertTrue(startup.contains("retryPendingDataCleanup()"))
        assertTrue(startup.indexOf("retryPendingDataCleanup()") < startup.indexOf("initStore()"))
        assertTrue(cleanup.contains("PENDING_MUTATION_MANIFEST"))
        assertTrue(cleanup.contains("queryById(id)"))
        assertTrue(cleanup.contains("manifest.phase == PENDING_PHASE_COMMITTED"))
        assertTrue(cleanup.contains("candidates.forEach"))
        assertFalse(cleanup.contains("candidates.all(::recoverPendingMutation)"))
        assertTrue(subscription.contains("writePendingDataMutationManifest"))
        assertTrue(subscription.contains("withContext(NonCancellable)"))
        assertTrue(subscription.contains("nextMutationMtime"))
        assertTrue(subscription.contains("blockPendingDataRecovery()"))
        assertTrue(
            subscription.indexOf("subsMapFlow.update") <
                subscription.indexOf("manifest.copy(phase = PENDING_PHASE_COMMITTED)"),
        )
        assertTrue(snapshot.contains("writePendingDataMutationManifest"))
        assertTrue(snapshot.contains("withContext(NonCancellable)"))
        assertTrue(snapshot.contains("blockPendingDataRecovery()"))
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
