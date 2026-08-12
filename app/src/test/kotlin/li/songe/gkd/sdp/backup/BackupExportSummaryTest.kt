package li.songe.gkd.sdp.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExportSummaryTest {
    @Test
    fun summaryCarriesStableExportMetadata() {
        val summary = BackupExportSummary(
            file = File("backup.gkd"),
            categoryIds = listOf("settings", "subscriptions"),
            objectCount = 12,
            encryptedBytes = 2048L,
        )

        assertEquals(listOf("settings", "subscriptions"), summary.categoryIds)
        assertEquals(12, summary.objectCount)
        assertEquals(2048L, summary.encryptedBytes)
    }
}
