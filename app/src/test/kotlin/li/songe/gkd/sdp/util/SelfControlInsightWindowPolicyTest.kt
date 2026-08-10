package li.songe.gkd.sdp.util

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlInsightWindowPolicyTest {
    private val now = 40L * 24L * 60L * 60L * 1_000L

    private fun sample(
        id: Long,
        atDaysAgo: Long,
        gapMinutes: Long?,
        durationMinutes: Int? = 30,
    ) = SelfControlInsightWindowPolicy.IntervalSample(
        id = id,
        occurredAtEpochMs = now - atDaysAgo * 24L * 60L * 60L * 1_000L,
        gapMs = gapMinutes?.times(60_000L),
        requestedDurationMinutes = durationMinutes,
    )

    @Test
    fun rollingWindowIncludesStartButExcludesNowAndFutureSamples() {
        val samples = listOf(
            sample(1, atDaysAgo = 1, gapMinutes = 10),
            sample(2, atDaysAgo = 7, gapMinutes = 20),
            sample(3, atDaysAgo = 8, gapMinutes = 30),
            sample(4, atDaysAgo = -1, gapMinutes = 40),
        )

        val selected = SelfControlInsightWindowPolicy.samplesInWindow(
            samples = samples,
            nowEpochMs = now,
            window = SelfControlInsightWindowPolicy.Window.LAST_7_DAYS,
        )

        assertEquals(listOf(2L, 1L), selected.map { it.id })
    }

    @Test
    fun chartPointLimitsMatchRequestedWindow() {
        assertEquals(24, SelfControlInsightWindowPolicy.Window.LAST_24_HOURS.maxChartPoints)
        assertEquals(28, SelfControlInsightWindowPolicy.Window.LAST_7_DAYS.maxChartPoints)
        assertEquals(30, SelfControlInsightWindowPolicy.Window.LAST_30_DAYS.maxChartPoints)

        val hourly = (0 until 40).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong(),
                occurredAtEpochMs = now - index * 60L * 60L * 1_000L,
                gapMs = 60_000L,
                requestedDurationMinutes = 1,
            )
        }
        val series = SelfControlInsightWindowPolicy.aggregate(
            samples = hourly,
            nowEpochMs = now,
            window = SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            metric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
        )

        assertTrue(series.points.size <= 24)
        assertEquals(24, series.stats.sampleCount)
    }

    @Test
    fun emptyBucketsAreMissingAndBucketValueIsAverageOfRows() {
        val samples = listOf(
            sample(1, atDaysAgo = 1, gapMinutes = 10),
            sample(2, atDaysAgo = 1, gapMinutes = 20),
        )

        val series = SelfControlInsightWindowPolicy.aggregate(
            samples = samples,
            nowEpochMs = now,
            window = SelfControlInsightWindowPolicy.Window.LAST_7_DAYS,
            metric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
        )

        assertEquals(2, series.stats.sampleCount)
        assertEquals(15.0 * 60_000.0, requireNotNull(series.stats.averageMs), 0.001)
        assertEquals(15.0 * 60_000.0, requireNotNull(series.stats.medianMs), 0.001)
        assertEquals(2, series.points.size)
        assertTrue(series.points.all { it.sampleCount == 1 })
        assertFalse(series.aggregationApplied)
    }

    @Test
    fun ratioMetricExcludesMissingRatioRowsWithoutTurningThemIntoZero() {
        val samples = listOf(
            sample(1, atDaysAgo = 1, gapMinutes = 120, durationMinutes = 30),
            sample(2, atDaysAgo = 1, gapMinutes = null, durationMinutes = 30),
            sample(3, atDaysAgo = 1, gapMinutes = 60, durationMinutes = 60),
        )

        val series = SelfControlInsightWindowPolicy.aggregate(
            samples = samples,
            nowEpochMs = now,
            window = SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            metric = SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
        )

        assertEquals(2, series.stats.sampleCount)
        assertEquals(3, series.rawSampleCount)
        assertEquals(1, series.excludedSampleCount)
        assertEquals(2.5, requireNotNull(series.stats.averageRatio), 0.0001)
        assertEquals(2.5, requireNotNull(series.stats.medianRatio), 0.0001)
        assertFalse(series.points.any { it.value == 0.0 })
    }

    @Test
    fun statsUseRawRowsAndPointsRemainStableByTimeThenId() {
        val samples = listOf(
            SelfControlInsightWindowPolicy.IntervalSample(2L, now - 1L, 10L * 60_000L, 30),
            SelfControlInsightWindowPolicy.IntervalSample(1L, now - 2L, 20L * 60_000L, 30),
            sample(3, atDaysAgo = 2, gapMinutes = 30),
        )
        val series = SelfControlInsightWindowPolicy.aggregate(
            samples = samples,
            nowEpochMs = now,
            window = SelfControlInsightWindowPolicy.Window.LAST_7_DAYS,
            metric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(3, series.stats.sampleCount)
        assertTrue(series.points.zipWithNext().all { (a, b) -> a.bucketStartAt <= b.bucketStartAt })
    }

    @Test
    fun lowVolumeSamplesStayAsIndividualPointsEvenInOneBucket() {
        val samples = (0 until 12).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong() + 1,
                occurredAtEpochMs = now - 30L * 60_000L + index,
                gapMs = 60_000L,
                requestedDurationMinutes = 1,
            )
        }

        val series = SelfControlInsightWindowPolicy.aggregate(
            samples = samples,
            nowEpochMs = now,
            window = SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            metric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
        )

        assertEquals(12, series.points.size)
        assertTrue(series.points.all { it.sampleCount == 1 })
        assertFalse(series.aggregationApplied)
    }

    @Test
    fun aggregationStartsOnlyAfterWindowPointLimit() {
        val samples = (0 until 26).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong() + 1,
                occurredAtEpochMs = now - index * 10L,
                gapMs = 60_000L,
                requestedDurationMinutes = 1,
            )
        }

        val series = SelfControlInsightWindowPolicy.aggregate(
            samples = samples,
            nowEpochMs = now,
            window = SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            metric = SelfControlInsightWindowPolicy.Metric.INTERVAL,
        )

        assertTrue(series.points.size <= 24)
        assertEquals(25, series.stats.sampleCount)
        assertTrue(series.aggregationApplied)
        assertEquals("按 1 小时聚合", series.aggregationLabel)
        assertEquals(25, series.points.sumOf { it.sampleCount })
    }

    @Test
    fun eachWindowUsesItsOwnPointLimit() {
        val sevenDaySamples = (0 until 30).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong() + 1,
                occurredAtEpochMs = now - index * 10L,
                gapMs = 60_000L,
                requestedDurationMinutes = 1,
            )
        }
        val thirtyDaySamples = (0 until 32).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong() + 100,
                occurredAtEpochMs = now - index * 10L,
                gapMs = 60_000L,
                requestedDurationMinutes = 1,
            )
        }

        val sevenDays = SelfControlInsightWindowPolicy.aggregate(
            sevenDaySamples,
            now,
            SelfControlInsightWindowPolicy.Window.LAST_7_DAYS,
            SelfControlInsightWindowPolicy.Metric.INTERVAL,
        )
        val thirtyDays = SelfControlInsightWindowPolicy.aggregate(
            thirtyDaySamples,
            now,
            SelfControlInsightWindowPolicy.Window.LAST_30_DAYS,
            SelfControlInsightWindowPolicy.Metric.INTERVAL,
        )

        assertTrue(sevenDays.aggregationApplied)
        assertEquals("按 6 小时聚合", sevenDays.aggregationLabel)
        assertTrue(thirtyDays.aggregationApplied)
        assertEquals("按 1 天聚合", thirtyDays.aggregationLabel)
    }

    @Test
    fun invalidRowsDoNotTriggerAggregationOrDisappearFromCoverage() {
        val samples = (0 until 40).map { index ->
            SelfControlInsightWindowPolicy.IntervalSample(
                id = index.toLong() + 1,
                occurredAtEpochMs = now - index * 10L,
                gapMs = if (index < 2) 60_000L else null,
                requestedDurationMinutes = if (index < 2) 1 else null,
            )
        }

        val series = SelfControlInsightWindowPolicy.aggregate(
            samples,
            now,
            SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            SelfControlInsightWindowPolicy.Metric.USAGE_RATIO,
        )

        assertEquals(39, series.rawSampleCount)
        assertEquals(1, series.stats.sampleCount)
        assertEquals(38, series.excludedSampleCount)
        assertEquals(1, series.points.size)
        assertFalse(series.aggregationApplied)
    }

    @Test
    fun sameTimestampRowsRemainStableAndCarryDistinctSourceIds() {
        val samples = listOf(
            SelfControlInsightWindowPolicy.IntervalSample(9L, now - 1L, 60_000L, 1),
            SelfControlInsightWindowPolicy.IntervalSample(8L, now - 2L, 120_000L, 1),
        )

        val series = SelfControlInsightWindowPolicy.aggregate(
            samples,
            now,
            SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
            SelfControlInsightWindowPolicy.Metric.INTERVAL,
        )

        assertEquals(listOf(setOf(8L), setOf(9L)), series.points.map { it.sourceIds })
    }
}
