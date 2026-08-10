package li.songe.gkd.sdp.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardReviewStateContractTest {
    @Test
    fun reviewPageUsesOneStateFlowAndUnifiedSummaryOnly() {
        val source = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/ui/usagereview/Sections.kt").readText()

        assertTrue(source.contains("reviewUiStateFlow"))
        assertTrue(source.contains("selectedMetricFlow"))
        assertFalse(source.contains("usageGuardSummaryFlow"))
        assertFalse(source.contains("UsageRequestSummaryCard"))
        assertFalse(source.contains("legacy_usage_summary"))
        assertFalse(source.contains("UsageGuardReviewPolicy"))
    }

    @Test
    fun reviewPageUsesAccessibleSegmentedFiltersAndAdaptiveWidth() {
        val source = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/ui/usagereview/Sections.kt").readText()

        assertTrue(source.contains("SingleChoiceSegmentedButtonRow"))
        assertTrue(source.contains("SegmentedButtonDefaults.itemShape"))
        assertTrue(source.contains("minimumInteractiveComponentSize"))
        assertTrue(source.contains("stateDescription"))
        assertTrue(source.contains("widthIn(max = 840.dp)"))
        assertTrue(source.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue(source.contains("UsageGuardReviewPagePreviewUsageData"))
        assertTrue(source.contains("UsageGuardReviewPagePreviewUsageEmpty"))
        assertTrue(source.contains("UsageGuardReviewPagePreviewInterceptData"))
        assertTrue(source.contains("invertedTheme = true"))
        assertTrue(source.contains("fontScale = 2f"))
    }

    @Test
    fun trendChartExposesAxisUnitsAndAtMostSixTimeLabels() {
        val source = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineTrendChart.kt").readText()
        val presentation = sourceFile("app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewPresentation.kt").readText()

        assertTrue(source.contains("R.string.s_47d3fa79b4"))
        assertTrue(source.contains("R.string.s_d49e418af8"))
        assertTrue(source.contains("R.string.s_37feaa9b99"))
        assertTrue(presentation.contains("fun xAxisLabels"))
        assertTrue(presentation.contains("maxLabels: Int = 6"))
    }

    private fun sourceFile(relativePath: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        var directory = File(userDir).absoluteFile
        while (!File(directory, "settings.gradle.kts").isFile || !File(directory, "app/src").isDirectory) {
            directory = directory.parentFile ?: error("Repository root marker not found from $userDir")
        }
        return File(directory, relativePath).also { check(it.isFile) { "Missing source: $relativePath" } }
    }
}
