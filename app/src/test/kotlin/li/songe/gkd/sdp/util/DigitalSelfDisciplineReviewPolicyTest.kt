package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UsageReviewRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DigitalSelfDisciplineReviewPolicyTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val testNow = LocalDate.of(2026, 8, 4)
        .atStartOfDay(shanghai)
        .plusHours(12)
        .toInstant()
        .toEpochMilli()

    @Test
    fun rangeBoundsUseRollingDurationAndPreviousPeriod() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_7_DAYS,
            testNow,
            shanghai,
        )

        assertEquals(testNow - 7L * 24L * 60L * 60L * 1_000L, bounds.startAt)
        assertEquals(testNow, bounds.endAt)
        assertEquals(testNow - 14L * 24L * 60L * 60L * 1_000L, bounds.previousStartAt)
        assertEquals(testNow - 7L * 24L * 60L * 60L * 1_000L, bounds.previousEndAt)
        assertTrue(bounds.startAt < bounds.endAt)
    }

    @Test
    fun rangeBoundsKeepRollingWindowsAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val now = LocalDate.of(2026, 3, 8).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
            now,
            zone,
        )
        assertEquals(now, bounds.endAt)
        assertEquals(24L * 60L * 60L * 1_000L, bounds.endAt - bounds.startAt)
    }

    @Test
    fun usageSummarySeparatesCoverageAndCalculatesAverageRatio() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
            testNow,
            shanghai,
        )
        val rows = (0 until 8).map { index ->
            row(
                id = index.toLong() + 1L,
                appId = if (index < 6) "target" else "other",
                requestedAt = bounds.startAt + (index + 1L) * 60_000L,
                gapMs = if (index < 6) {
                    if (index % 2 == 0) 120L * 60_000L else 60L * 60_000L
                } else {
                    null
                },
                durationMinutes = if (index % 2 == 0) 30 else 60,
                tags = if (index % 2 == 0) listOf("学习") else listOf("其他"),
            )
        }

        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = rows,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = shanghai,
        )

        assertEquals(8, summary.coverage.eventCount)
        assertEquals(6, summary.coverage.validIntervalCount)
        assertEquals(6, summary.coverage.validRatioCount)
        assertEquals(2, summary.coverage.excludedIntervalCount)
        assertEquals(2, summary.coverage.excludedRatioCount)
        assertEquals(2.5, requireNotNull(summary.ratioStats).average!!, 0.0001)
        assertEquals(8, summary.recentIntervals.size)
        assertTrue(summary.usageDetails!!.tagBreakdown.isNotEmpty())
        assertEquals(8, summary.usageDetails.appBreakdown.sumOf { it.count })
    }

    @Test
    fun usageDurationTotalsUseLongAndDoNotOverflow() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
            testNow,
            shanghai,
        )
        val rows = listOf(
            row(1L, "target", bounds.startAt + 1_000L, 0L, Int.MAX_VALUE),
            row(2L, "target", bounds.startAt + 2_000L, null, Int.MAX_VALUE),
        )
        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = rows,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
        )

        assertEquals(2L * Int.MAX_VALUE.toLong(), summary.usageDetails!!.totalRequestedMinutes)
    }

    @Test
    fun interceptFilterKeepsRatioEmptyAndMissingDatesMissing() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_7_DAYS,
            testNow,
            shanghai,
        )
        val events = listOf(
            event(1, "app_blocker:a", SelfControlAttempt.KIND_APP_BLOCKER, bounds.startAt + 1_000L, null),
            event(2, "app_blocker:a", SelfControlAttempt.KIND_APP_BLOCKER, bounds.startAt + 2_000L, 1_000L),
            event(3, "url_intercept:7", SelfControlAttempt.KIND_URL_INTERCEPT, bounds.startAt + 3_000L, 2_000L),
        )
        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = emptyList(),
            events = events,
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.AppBlocker,
            zoneId = shanghai,
        )

        assertEquals(2, summary.coverage.eventCount)
        assertEquals(1, summary.coverage.validIntervalCount)
        assertEquals(0, summary.coverage.validRatioCount)
        assertNull(summary.ratioStats)
        assertEquals(1, summary.intervalStats.sampleCount)
        assertEquals(1_000L, summary.intervalStats.averageMs)
        assertTrue(summary.dailyBuckets.all { it.eventCount > 0 })
    }

    @Test
    fun nonPositiveDurationIsExcludedFromRatioButNotIntervalCoverage() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
            testNow,
            shanghai,
        )
        val rows = listOf(
            row(1L, "app", bounds.startAt + 1_000L, 60_000L, 30),
            row(2L, "app", bounds.startAt + 2_000L, 60_000L, 0),
            row(3L, "app", bounds.startAt + 3_000L, 60_000L, -5),
        )
        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = rows,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = shanghai,
        )

        assertEquals(3, summary.coverage.eventCount)
        assertEquals(3, summary.coverage.validIntervalCount)
        assertEquals(1, summary.coverage.validRatioCount)
        assertEquals(0, summary.coverage.excludedIntervalCount)
        assertEquals(2, summary.coverage.excludedRatioCount)
    }

    @Test
    fun rankingsMergeLabelsByStableKeyAndChooseDeterministicLabel() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
            testNow,
            shanghai,
        )
        val rows = listOf(
            row(1L, "same.app", bounds.startAt + 1_000L, 60_000L, 30).copy(appName = "Z 名称"),
            row(2L, "same.app", bounds.startAt + 2_000L, 60_000L, 30).copy(appName = "A 名称"),
        )
        val usage = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = rows,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = shanghai,
        )
        assertEquals(1, usage.usageDetails!!.appBreakdown.size)
        assertEquals(2, usage.usageDetails.appBreakdown.single().count)
        assertEquals("A 名称", usage.usageDetails.appBreakdown.single().label)

        val events = listOf(
            event(1L, "stable-rule", SelfControlAttempt.KIND_SELECTOR_INTERCEPT, bounds.startAt + 1_000L, 1_000L)
                .copy(subjectLabel = "Z 规则"),
            event(2L, "stable-rule", SelfControlAttempt.KIND_SELECTOR_INTERCEPT, bounds.startAt + 2_000L, 1_000L)
                .copy(subjectLabel = "A 规则"),
        )
        val intercept = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = emptyList(),
            events = events,
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = shanghai,
        )
        assertEquals(1, intercept.rankedTargets.size)
        assertEquals(2, intercept.rankedTargets.single().count)
        assertEquals("A 规则", intercept.rankedTargets.single().label)
    }

    @Test
    fun comparisonReportsExactDifferenceWithSmallSamples() {
        val current = SelfControlIntervalPolicy.statsFor(listOf(10L, 20L))
        val previous = SelfControlIntervalPolicy.statsFor(listOf(30L, 40L))

        val comparison = DigitalSelfDisciplineReviewPolicy.compare(current, previous)

        assertEquals(2, comparison.currentSampleCount)
        assertEquals(2, comparison.previousSampleCount)
        assertEquals(-20.0, comparison.metricDelta!!, 0.0001)
        assertEquals(-20L, comparison.deltaAverageMs)
        assertTrue(comparison.message.contains("低"))
    }

    @Test
    fun comparisonHandlesEqualAndMissingSamples() {
        val equal = DigitalSelfDisciplineReviewPolicy.compare(
            current = SelfControlIntervalPolicy.statsFor(listOf(10L, 20L)),
            previous = SelfControlIntervalPolicy.statsFor(listOf(10L, 20L)),
        )
        assertEquals(0.0, equal.metricDelta!!, 0.0001)
        assertTrue(equal.message.contains("相同"))

        val missing = DigitalSelfDisciplineReviewPolicy.compare(
            current = SelfControlIntervalPolicy.statsFor(emptyList()),
            previous = SelfControlIntervalPolicy.statsFor(listOf(10L)),
        )
        assertEquals(null, missing.metricDelta)
        assertTrue(missing.message.contains("暂无"))
    }

    @Test
    fun rangeBoundsClampNegativeNowAndDateBoundaryIsStable() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
            -10L,
            shanghai,
        )
        assertEquals(0L, bounds.startAt)
        assertEquals(0L, bounds.previousStartAt)
        assertEquals(
            true,
            DigitalSelfDisciplineReviewPolicy.hasCrossedDateBoundary(0L, 86_400_000L, shanghai),
        )
        assertEquals(
            false,
            DigitalSelfDisciplineReviewPolicy.hasCrossedDateBoundary(0L, 1_000L, shanghai),
        )
    }

    private fun row(
        id: Long,
        appId: String,
        requestedAt: Long,
        gapMs: Long?,
        durationMinutes: Int,
        tags: List<String> = emptyList(),
    ) = UsageReviewRow(
        id = id,
        appId = appId,
        appName = appId,
        tagNames = tags,
        requestedDurationMinutes = durationMinutes,
        requestedAt = requestedAt,
        endReason = 5,
        requestGapMs = gapMs,
    )

    private fun event(
        id: Long,
        key: String,
        kind: Int,
        at: Long,
        interval: Long?,
    ) = SelfControlAttemptEvent(
        id = id,
        eventKey = key,
        eventKind = kind,
        subjectId = key,
        subjectLabel = key,
        occurredAt = at,
        intervalMs = interval,
    )
}
