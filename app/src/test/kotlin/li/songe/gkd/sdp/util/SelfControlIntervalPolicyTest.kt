package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlIntervalPolicyTest {
    private fun event(
        key: String,
        occurredAt: Long,
        id: Long,
    ) = SelfControlIntervalPolicy.Event(
        key = key,
        occurredAtEpochMs = occurredAt,
        id = id,
    )

    @Test
    fun intervalsAreCalculatedOnlyForTheRequestedKeyAndStableById() {
        val intervals = SelfControlIntervalPolicy.intervalsForKey(
            events = listOf(
                event("other", 10_000L, 1L),
                event("target", 3_000L, 3L),
                event("target", 1_000L, 1L),
                event("target", 1_000L, 2L),
                event("target", 7_000L, 4L),
            ),
            key = "target",
        )

        assertEquals(listOf(0L, 2_000L, 4_000L), intervals)
    }

    @Test
    fun recentIntervalsKeepTheNewestFiveInChronologicalOrder() {
        val recent = SelfControlIntervalPolicy.recentCompletedIntervals(
            intervalsMs = (1L..7L).toList(),
            limit = 5,
        )

        assertEquals(listOf(3L, 4L, 5L, 6L, 7L), recent)
    }

    @Test
    fun negativeIntervalsAreExcludedFromStatistics() {
        val stats = SelfControlIntervalPolicy.statsFor(listOf(10L, -1L, 30L))

        assertEquals(2, stats.sampleCount)
        assertEquals(20L, stats.averageMs)
        assertEquals(20L, stats.medianMs)
    }

    @Test
    fun meanAndMedianHandleOddAndEvenSamples() {
        assertEquals(20L, SelfControlIntervalPolicy.statsFor(listOf(10L, 20L, 30L)).medianMs)
        assertEquals(25L, SelfControlIntervalPolicy.statsFor(listOf(10L, 20L, 30L, 40L)).medianMs)
        assertEquals(25L, SelfControlIntervalPolicy.statsFor(listOf(10L, 20L, 30L, 40L)).averageMs)
        assertEquals(1L, SelfControlIntervalPolicy.statsFor(listOf(0L, 1L)).averageMs)
        assertEquals(1L, SelfControlIntervalPolicy.statsFor(listOf(0L, 1L)).medianMs)
    }

    @Test
    fun largeValuesDoNotOverflowMeanOrMedian() {
        val stats = SelfControlIntervalPolicy.statsFor(
            listOf(Long.MAX_VALUE, Long.MAX_VALUE - 2L),
        )

        assertEquals(Long.MAX_VALUE - 1L, stats.averageMs)
        assertEquals(Long.MAX_VALUE - 1L, stats.medianMs)
    }

    @Test
    fun overlayInsightExcludesCurrentElapsedValueFromHistoryStats() {
        val insight = SelfControlIntervalPolicy.overlayInsight(
            anchorAtEpochMs = 1_000L,
            firstOccurrence = false,
            recentCompletedIntervalsMs = listOf(10_000L, 20_000L),
            nowEpochMs = 31_000L,
        )

        assertEquals(30_000L, insight.currentElapsedMs)
        assertEquals(15_000L, insight.stats.averageMs)
        assertEquals(15_000L, insight.stats.medianMs)
        assertEquals(15_000L, insight.comparison?.deltaMs)
    }

    @Test
    fun overlayInsightClampsFutureAnchorAndMarksFirstOccurrence() {
        val insight = SelfControlIntervalPolicy.overlayInsight(
            anchorAtEpochMs = 20_000L,
            firstOccurrence = true,
            recentCompletedIntervalsMs = emptyList(),
            nowEpochMs = 19_000L,
        )

        assertEquals(0L, insight.currentElapsedMs)
        assertTrue(insight.firstOccurrence)
        assertNull(insight.comparison)
        assertEquals(SelfControlIntervalPolicy.SampleQuality.NoSample, insight.stats.quality)
    }

    @Test
    fun sampleQualityDistinguishesInsufficientAndReadyData() {
        assertEquals(
            SelfControlIntervalPolicy.SampleQuality.NoSample,
            SelfControlIntervalPolicy.statsFor(emptyList()).quality,
        )
        assertEquals(
            SelfControlIntervalPolicy.SampleQuality.Limited,
            SelfControlIntervalPolicy.statsFor(listOf(1L)).quality,
        )
        assertEquals(
            SelfControlIntervalPolicy.SampleQuality.Ready,
            SelfControlIntervalPolicy.statsFor(listOf(1L, 2L)).quality,
        )
    }

    @Test
    fun durationFormattingSelectsReadableUnits() {
        assertEquals("45秒", SelfControlIntervalPolicy.formatDurationCompact(45_000L))
        assertEquals("2分 05秒", SelfControlIntervalPolicy.formatDurationCompact(125_000L))
        assertEquals("3小时 04分", SelfControlIntervalPolicy.formatDurationCompact((3 * 60 + 4) * 60_000L))
        assertEquals("2天 03小时", SelfControlIntervalPolicy.formatDurationCompact(2 * 86_400_000L + 3 * 3_600_000L))
    }

    @Test
    fun axisUnitUsesTheLargestPointWithoutChangingData() {
        assertEquals(
            SelfControlIntervalPolicy.AxisUnit.Seconds,
            SelfControlIntervalPolicy.chooseAxisUnit(59_000L),
        )
        assertEquals(
            SelfControlIntervalPolicy.AxisUnit.Minutes,
            SelfControlIntervalPolicy.chooseAxisUnit(60_000L),
        )
        assertEquals(
            SelfControlIntervalPolicy.AxisUnit.Hours,
            SelfControlIntervalPolicy.chooseAxisUnit(3_600_000L),
        )
        assertEquals(
            SelfControlIntervalPolicy.AxisUnit.Days,
            SelfControlIntervalPolicy.chooseAxisUnit(86_400_000L),
        )
    }
}
