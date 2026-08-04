package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlIntervalPresentationTest {
    private val now = 40L * 24L * 60L * 60L * 1_000L

    @Test
    fun datasetPresentationDefaultsToTwentyFourHoursAndReusesAllSamplesOnRangeSwitch() {
        val samples = listOf(
            SelfControlInsightWindowPolicy.IntervalSample(1L, now - 60_000L, 120_000L, 30),
            SelfControlInsightWindowPolicy.IntervalSample(
                2L,
                now - 2L * 24L * 60L * 60L * 1_000L,
                60_000L,
                60,
            ),
        )

        val day = SelfControlInsightPresentation.from(samples, now)
        val month = SelfControlInsightPresentation.from(
            samples = samples,
            insightAnchorAt = now,
            selectedWindow = SelfControlInsightWindowPolicy.Window.LAST_30_DAYS,
        )

        assertEquals(SelfControlInsightWindowPolicy.Window.LAST_24_HOURS, day.selectedWindow)
        assertEquals(1, day.selectedSeries.stats.sampleCount)
        assertEquals(2, month.selectedSeries.stats.sampleCount)
        assertEquals(2, month.selectedSeries.rawSampleCount)
    }

    @Test
    fun ratioPresentationKeepsMissingRowsOutOfAverageAndCurrentOutOfHistory() {
        val samples = listOf(
            SelfControlInsightWindowPolicy.IntervalSample(1L, now - 1_000L, 120L * 60_000L, 30),
            SelfControlInsightWindowPolicy.IntervalSample(2L, now - 2_000L, null, 30),
        )
        val presentation = SelfControlInsightPresentation.from(
            samples = samples,
            insightAnchorAt = now,
            selectedMetric = SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
            supportsUsageRatio = true,
            currentReference = SelfControlInsightCurrentReference(
                gapMs = 120L * 60_000L,
                durationMinutes = 60,
            ),
        )

        assertEquals(1, presentation.selectedSeries.stats.sampleCount)
        assertEquals(4.0, presentation.selectedSeries.stats.averageRatio!!, 0.0001)
        assertTrue(presentation.comparisonText!!.contains("低"))
        assertTrue(presentation.ratioSeriesByWindow.isNotEmpty())
    }

    @Test
    fun textRowsAreBoundedByChartPointsAndMarkBucketAverages() {
        val samples = (0 until 40).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong(),
                occurredAtEpochMs = now - index * 60L * 60L * 1_000L,
                gapMs = 60_000L,
                requestedDurationMinutes = null,
            )
        }
        val presentation = SelfControlInsightPresentation.from(samples, now)

        assertTrue(presentation.chartPoints.size <= 24)
        assertEquals(presentation.chartPoints.size, presentation.textRows.size)
        assertTrue(presentation.textRows.any { it.sampleCount > 1 })
    }

    @Test
    fun currentEventIsMarkedInChartAndTextModel() {
        val sample = SelfControlInsightWindowPolicy.IntervalSample(
            id = 77L,
            occurredAtEpochMs = now - 30L * 60_000L,
            gapMs = 120L * 60_000L,
            requestedDurationMinutes = null,
        )
        val presentation = SelfControlInsightPresentation.from(
            samples = listOf(sample),
            insightAnchorAt = now,
            currentReference = SelfControlInsightCurrentReference(
                gapMs = 120L * 60_000L,
                eventId = sample.id,
            ),
        )

        assertTrue(presentation.chartPoints.single().isCurrent)
        assertTrue(presentation.textRows.single().isCurrent)
    }
}
