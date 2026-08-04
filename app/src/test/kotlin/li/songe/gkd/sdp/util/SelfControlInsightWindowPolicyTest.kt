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
    fun rollingWindowIncludesStartAndNowButExcludesOlderAndFutureSamples() {
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

        assertEquals(listOf(1L, 2L), selected.map { it.id })
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
        assertEquals(25, series.stats.sampleCount)
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
        assertEquals(15.0 * 60_000.0, series.stats.averageMs, 0.001)
        assertEquals(1, series.points.size)
        assertEquals(2, series.points.single().sampleCount)
        assertEquals(15.0 * 60_000.0, series.points.single().value, 0.001)
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
        assertEquals(2.5, series.stats.averageRatio, 0.0001)
        assertFalse(series.points.any { it.value == 0.0 })
    }

    @Test
    fun statsUseRawRowsAndPointsRemainStableByTimeThenId() {
        val samples = listOf(
            sample(2, atDaysAgo = 0, gapMinutes = 10),
            sample(1, atDaysAgo = 0, gapMinutes = 20),
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
}
