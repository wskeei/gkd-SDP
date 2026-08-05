package li.songe.gkd.sdp.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardReviewStateContractTest {
    @Test
    fun reviewPageUsesOneStateFlowAndUnifiedSummaryOnly() {
        val source = File("app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewPage.kt").readText()

        assertTrue(source.contains("val reviewUiStateFlow"))
        assertTrue(source.contains("selectedMetricFlow"))
        assertFalse(source.contains("usageGuardSummaryFlow"))
        assertFalse(source.contains("UsageRequestSummaryCard"))
        assertFalse(source.contains("legacy_usage_summary"))
        assertFalse(source.contains("UsageGuardReviewPolicy"))
    }
}
