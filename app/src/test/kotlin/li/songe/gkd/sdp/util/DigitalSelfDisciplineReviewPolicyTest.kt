package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UsageGuardRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DigitalSelfDisciplineReviewPolicyTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun rangeBoundsUseNaturalDaysAndPreviousPeriod() {
        val today = LocalDate.of(2026, 8, 4)
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.SevenDays,
            today,
            shanghai,
        )

        assertEquals(LocalDate.of(2026, 7, 29), bounds.startDate)
        assertEquals(LocalDate.of(2026, 8, 5), bounds.endDateExclusive)
        assertEquals(LocalDate.of(2026, 7, 22), bounds.previousStartDate)
        assertEquals(LocalDate.of(2026, 7, 29), bounds.previousEndDateExclusive)
        assertTrue(bounds.startAt < bounds.endAt)
    }

    @Test
    fun dstBoundaryStillUsesZoneStartOfDay() {
        val zone = ZoneId.of("America/New_York")
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            LocalDate.of(2026, 3, 8),
            zone,
        )
        val next = LocalDate.of(2026, 3, 9).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(next, bounds.endAt)
        assertEquals(23L * 60L * 60L * 1_000L, bounds.endAt - bounds.startAt)
    }

    @Test
    fun epochClockCanBeInjectedForDeterministicDateSelection() {
        val epoch = LocalDate.of(2026, 8, 4).atStartOfDay(shanghai).toInstant().toEpochMilli()
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            nowEpochMs = epoch + 12 * 60 * 60 * 1_000L,
            zoneId = shanghai,
        )
        assertEquals(LocalDate.of(2026, 8, 4), bounds.startDate)
    }

    @Test
    fun usageIntervalsUseFrozenActualEndGapAndExcludeUnknownLegacyRows() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            LocalDate.of(2026, 8, 4),
            shanghai,
        )
        val start = bounds.startAt
        val records = listOf(
            record(1, "target", start - 10_000L, null),
            record(2, "target", start + 10_000L, 20_000L),
            record(3, "target", start + 40_000L, 30_000L),
            record(4, "other", start + 50_000L, null),
        )

        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            records = records,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = shanghai,
        )

        assertEquals(3, summary.requestCount)
        assertEquals(listOf(20_000L, 30_000L), summary.intervalsMs)
        assertEquals(2, summary.stats.sampleCount)
        assertEquals(1, summary.dailyBuckets.size)
    }

    @Test
    fun interceptFiltersDoNotPairDifferentKeysAndMissingDaysAreNotZero() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.SevenDays,
            LocalDate.of(2026, 8, 4),
            shanghai,
        )
        val events = listOf(
            event(1, "app_blocker:a", SelfControlAttempt.KIND_APP_BLOCKER, 1_000L, null),
            event(2, "app_blocker:a", SelfControlAttempt.KIND_APP_BLOCKER, 2_000L, 1_000L),
            event(3, "url_intercept:7", SelfControlAttempt.KIND_URL_INTERCEPT, 3_000L, 2_000L),
        ).map { it.copy(occurredAt = bounds.startAt + it.occurredAt) }

        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            records = emptyList(),
            events = events,
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.AppBlocker,
            zoneId = shanghai,
        )

        assertEquals(2, summary.eventCount)
        assertEquals(listOf(1_000L), summary.intervalsMs)
        assertTrue(summary.dailyBuckets.all { it.sampleCount > 0 })
        assertTrue(summary.chartDates.none { it == LocalDate.of(2026, 7, 30) })
    }

    @Test
    fun usageRequestCountAndValidGapCountAreReportedSeparately() {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            LocalDate.of(2026, 8, 4),
            shanghai,
        )
        val records = listOf(
            record(1, "target", bounds.startAt + 1_000L, 0L),
            record(2, "target", bounds.startAt + 2_000L, null),
        )

        val summary = DigitalSelfDisciplineReviewPolicy.summarize(
            records = records,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = shanghai,
        )

        assertEquals(2, summary.requestCount)
        assertEquals(1, summary.stats.sampleCount)
        assertEquals(listOf(0L), summary.intervalsMs)
    }

    @Test
    fun comparisonNeedsThreeSamplesInBothPeriods() {
        val current = SelfControlIntervalPolicy.statsFor(listOf(10L, 20L, 30L))
        val previousTooSmall = SelfControlIntervalPolicy.statsFor(listOf(10L, 20L))
        val insufficient = DigitalSelfDisciplineReviewPolicy.compare(current, previousTooSmall)
        assertNull(insufficient.deltaAverageMs)
        assertTrue(insufficient.message.contains("样本不足"))

        val previous = SelfControlIntervalPolicy.statsFor(listOf(10L, 20L, 30L))
        val sufficient = DigitalSelfDisciplineReviewPolicy.compare(current, previous)
        assertEquals(0L, sufficient.deltaAverageMs)
    }

    private fun record(id: Long, appId: String, at: Long, gapMs: Long?) = UsageGuardRecord(
        id = id,
        appId = appId,
        appName = appId,
        tagNames = emptyList(),
        reasonText = "test reason",
        requestedDurationMinutes = 10,
        requestedAt = at,
        grantedAt = at,
        expiresAt = at + 600_000L,
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
