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
        assertTrue(shouldRollbackSubscriptionStaging(null, null, targetExisted = false))
        assertFalse(shouldRollbackSubscriptionStaging(null, null, targetExisted = true))
        assertTrue(shouldRollbackSubscriptionStaging(10, 10, targetExisted = true))
        assertFalse(shouldRollbackSubscriptionStaging(11, 10, targetExisted = true))
    }

}
