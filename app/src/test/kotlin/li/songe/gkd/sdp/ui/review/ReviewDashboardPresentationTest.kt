package li.songe.gkd.sdp.ui.review

import li.songe.gkd.sdp.data.UsageReviewRow
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReviewDashboardPresentationTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val testNow = LocalDate.of(2026, 8, 4)
        .atStartOfDay(zone)
        .plusHours(12)
        .toInstant()
        .toEpochMilli()

    @Test
    fun rollingRangesUseHalfOpenWindowsAndPreviousEqualLengthWindow() {
        DigitalSelfDisciplineReviewPolicy.Range.entries.forEach { range ->
            val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(range, testNow, zone)

            assertEquals(testNow, bounds.endAt)
            assertEquals(range.durationMs, bounds.endAt - bounds.startAt)
            assertEquals(range.durationMs, bounds.previousEndAt - bounds.previousStartAt)
            assertEquals(bounds.startAt, bounds.previousEndAt)
            assertTrue(bounds.contains(testNow - 1L))
            assertTrue(!bounds.contains(testNow))
        }
    }

    @Test
    fun denseTwentyFourHourDataAggregatesToAtMostTwentyFourBuckets() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
            testNow,
            zone,
        )
        val rows = (0 until 25).map { index ->
            UsageReviewRow(
                id = index.toLong() + 1L,
                appId = "app",
                appName = "App",
                tagNames = emptyList(),
                requestedDurationMinutes = 30,
                requestedAt = bounds.startAt + index * 1_000L,
                endReason = 5,
                requestGapMs = 60_000L,
            )
        }
        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = rows,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = zone,
        )
        val trend = DigitalSelfDisciplineReviewPresentation.trend(summary, zoneId = zone)

        assertTrue(trend.points.size <= 24)
        assertTrue(trend.points.any { it.sampleCount > 1 })
    }

    @Test
    fun emptyRollingWindowReturnsEmptyPresentationWithoutTrendArrows() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_7_DAYS,
            testNow,
            zone,
        )
        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = emptyList(),
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = zone,
        )
        val trend = DigitalSelfDisciplineReviewPresentation.trend(summary, zoneId = zone)

        assertTrue(trend.empty)
        assertTrue(trend.points.isEmpty())
        assertEquals("—", trend.currentAverageText)
        assertTrue(!trend.deltaText.contains("增加"))
        assertTrue(!trend.deltaText.contains("减少"))
    }

    @Test
    fun rangeLabelsAndBucketsMatchFixedPlan() {
        assertEquals("近 24 小时", DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS.label)
        assertEquals("近 7 天", DigitalSelfDisciplineReviewPolicy.Range.LAST_7_DAYS.label)
        assertEquals("近 30 天", DigitalSelfDisciplineReviewPolicy.Range.LAST_30_DAYS.label)
        assertEquals(60L * 60L * 1_000L, DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS.bucketMs)
        assertEquals(6L * 60L * 60L * 1_000L, DigitalSelfDisciplineReviewPolicy.Range.LAST_7_DAYS.bucketMs)
        assertEquals(24L * 60L * 60L * 1_000L, DigitalSelfDisciplineReviewPolicy.Range.LAST_30_DAYS.bucketMs)
    }
}
