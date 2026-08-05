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
        assertTrue(presentation.semanticSummary.contains("有效样本 25 条"))
        assertTrue(presentation.semanticSummary.contains("图形点"))
        assertTrue(presentation.supportingText.contains("按 1 小时聚合"))
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

    @Test
    fun sameTimestampRowsOnlyMarkThePointContainingCurrentEvent() {
        val samples = listOf(
            SelfControlInsightWindowPolicy.IntervalSample(77L, now, 120L * 60_000L, null),
            SelfControlInsightWindowPolicy.IntervalSample(78L, now, 60L * 60_000L, null),
        )
        val presentation = SelfControlInsightPresentation.from(
            samples = samples,
            insightAnchorAt = now,
            currentReference = SelfControlInsightCurrentReference(
                gapMs = 120L * 60_000L,
                eventId = 77L,
            ),
        )

        assertEquals(listOf(true, false), presentation.chartPoints.map { it.isCurrent })
    }

    @Test
    fun coverageSummarySeparatesRawRowsFromValidPoints() {
        val samples = (0 until 6).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong() + 1,
                occurredAtEpochMs = now - index * 60_000L,
                gapMs = if (index < 4) 60_000L else null,
                requestedDurationMinutes = 1,
            )
        }
        val presentation = SelfControlInsightPresentation.from(samples, now)

        assertTrue(presentation.semanticSummary.contains("总记录 6 条"))
        assertTrue(presentation.semanticSummary.contains("有效样本 4 条"))
        assertTrue(presentation.semanticSummary.contains("图形点 4 个"))
        assertTrue(presentation.semanticSummary.contains("未纳入 2 条"))
    }
}
