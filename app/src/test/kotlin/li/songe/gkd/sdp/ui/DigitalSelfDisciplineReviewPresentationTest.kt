package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DigitalSelfDisciplineReviewPresentationTest {
    @Test
    fun summaryPresentationUsesRecentSamplesForTodayAndDailyMediansForLongRanges() {
        val today = summary(DigitalSelfDisciplineReviewPolicy.Range.Today)
        assertEquals(listOf("1", "2"), DigitalSelfDisciplineReviewPresentation.chartPoints(today).map { it.label })

        val sevenDays = summary(DigitalSelfDisciplineReviewPolicy.Range.SevenDays)
        assertEquals(
            listOf("08-01", "08-02"),
            DigitalSelfDisciplineReviewPresentation.chartPoints(sevenDays).map { it.label },
        )
    }

    @Test
    fun homeSummaryContainsBothEventTypesAndEmptyStateIsExplicit() {
        assertEquals("今日 2 次申请 · 3 次拦截", DigitalSelfDisciplineReviewPresentation.homeSummary(2, 3))
        assertTrue(DigitalSelfDisciplineReviewPresentation.emptyText.contains("暂无"))
    }

    @Test
    fun filterVisibilityOnlyShowsInterceptSubtypesOnInterceptTab() {
        assertTrue(
            DigitalSelfDisciplineReviewPresentation.showInterceptFilters(
                DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt
            )
        )
        assertTrue(
            !DigitalSelfDisciplineReviewPresentation.showInterceptFilters(
                DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest
            )
        )
    }

    private fun summary(range: DigitalSelfDisciplineReviewPolicy.Range): DigitalSelfDisciplineReviewPolicy.ReviewSummary {
        val dates = if (range == DigitalSelfDisciplineReviewPolicy.Range.Today) {
            listOf(LocalDate.of(2026, 8, 4))
        } else {
            listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2))
        }
        val buckets = dates.map { date ->
            DigitalSelfDisciplineReviewPolicy.DailyIntervalBucket(date, 1, 60_000L, 60_000L)
        }
        val recent = listOf(
            DigitalSelfDisciplineReviewPolicy.RecentIntervalItem(2L, 60_000L, "A", "a"),
            DigitalSelfDisciplineReviewPolicy.RecentIntervalItem(1L, 30_000L, "A", "a"),
        )
        return DigitalSelfDisciplineReviewPolicy.ReviewSummary(
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            range = range,
            eventCount = 2,
            requestCount = 2,
            interceptCount = 0,
            intervalsMs = listOf(30_000L, 60_000L),
            stats = SelfControlIntervalPolicy.statsFor(listOf(30_000L, 60_000L)),
            dailyBuckets = buckets,
            chartDates = dates,
            recentIntervals = recent,
            rankedTargets = emptyList(),
            comparison = DigitalSelfDisciplineReviewPolicy.PeriodComparison(2, 0, null, "暂无上一周期数据"),
        )
    }
}
