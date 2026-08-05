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

    @Test
    fun reviewPageUsesAccessibleSegmentedFiltersAndAdaptiveWidth() {
        val source = File("app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewPage.kt").readText()

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
        val source = File("app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineTrendChart.kt").readText()
        val presentation = File("app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewPresentation.kt").readText()

        assertTrue(source.contains("纵轴："))
        assertTrue(source.contains("最大"))
        assertTrue(source.contains("最小"))
        assertTrue(presentation.contains("fun xAxisLabels"))
        assertTrue(presentation.contains("maxLabels: Int = 6"))
    }
}
