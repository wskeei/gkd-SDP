package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardReviewStateContractTest {
    @Test
    fun reviewUiStateIsTypedAndUnified() {
        val loading: DigitalSelfDisciplineReviewUiState = DigitalSelfDisciplineReviewUiState.Loading
        val ready: DigitalSelfDisciplineReviewUiState = DigitalSelfDisciplineReviewUiState.Ready(
            summary = DigitalSelfDisciplineReviewPolicy.ReviewSummary(
                reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
                range = DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
                eventCount = 0,
                requestCount = 0,
                interceptCount = 0,
                coverage = DigitalSelfDisciplineReviewPolicy.DataCoverage(0, 0, 0, 0, 0),
                intervalStats = li.songe.gkd.sdp.util.SelfControlIntervalPolicy.statsFor(emptyList()),
                ratioStats = null,
                usageDetails = null,
                dailyBuckets = emptyList(),
                trendIntervals = emptyList(),
                recentIntervals = emptyList(),
                rankedTargets = emptyList(),
                comparison = DigitalSelfDisciplineReviewPolicy.PeriodComparison(
                    currentEventCount = 0,
                    previousEventCount = 0,
                    currentMetricValue = null,
                    previousMetricValue = null,
                    metricDelta = null,
                    currentSampleCount = 0,
                    previousSampleCount = 0,
                    message = "",
                ),
            ),
        )

        assertTrue(loading is DigitalSelfDisciplineReviewUiState.Loading)
        assertTrue(ready is DigitalSelfDisciplineReviewUiState.Ready)
    }

    @Test
    fun chartLabelsAreBoundedAndUseStableAxisUnits() {
        val points = (0 until 12).map { index ->
            DigitalSelfDisciplineReviewPresentation.TrendPoint(
                label = "08:$index",
                value = index.toDouble(),
                sampleCount = 1,
                occurredAt = 1_000L + index,
            )
        }

        assertTrue(DigitalSelfDisciplineReviewPresentation.xAxisLabels(points).size <= 6)
        assertTrue(
            DigitalSelfDisciplineReviewPresentation.axisUnitLabel(
                DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO,
            ).isNotBlank(),
        )
        assertEquals(
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO,
            DigitalSelfDisciplineReviewPresentation.defaultMetric(
                DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            ),
        )
    }
}
