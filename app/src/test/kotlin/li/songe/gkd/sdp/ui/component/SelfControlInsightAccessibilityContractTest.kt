package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM contract for the semantics-friendly, bounded presentation model. */
class SelfControlInsightAccessibilityContractTest {
    @Test
    fun chartTextRowsHaveOneToOneBoundedDescriptions() {
        val now = 30L * 24L * 60L * 60L * 1_000L
        val samples = (0 until 90).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong(),
                occurredAtEpochMs = now - index * 6L * 60L * 60L * 1_000L,
                gapMs = 60_000L,
                requestedDurationMinutes = null,
            )
        }
        val presentation = SelfControlInsightPresentation.from(samples, now)

        assertTrue(presentation.chartPoints.size <= SelfControlInsightWindowPolicy.MAX_CHART_POINTS)
        assertEquals(presentation.chartPoints.size, presentation.textRows.size)
        assertTrue(presentation.semanticSummary.isNotBlank())
        assertTrue(presentation.semanticSummary.contains("总记录"))
        assertTrue(presentation.semanticSummary.contains("图形点"))
    }

    @Test
    fun emptyHistoryHasTextualStateAndNoChartRows() {
        val presentation = SelfControlInsightPresentation.from(
            samples = emptyList(),
            insightAnchorAt = 1_000L,
        )

        assertTrue(presentation.chartPoints.isEmpty())
        assertTrue(presentation.textRows.isEmpty())
        assertTrue(presentation.semanticSummary.contains("暂无"))
    }
}
