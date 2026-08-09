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
    fun `application startup retries registered cleanup before opening state`() {
        val source = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/App.kt").readText()
        val startup = source.substringAfter("override fun onCreate()")

        assertTrue(startup.contains("retryPendingDataCleanup()"))
        assertTrue(startup.indexOf("retryPendingDataCleanup()") < startup.indexOf("initStore()"))
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
