package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.UsageReviewRow
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DigitalSelfDisciplineReviewPresentationTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun usageTrendDefaultsToRatioAndCanSwitchToGapWithoutChangingCoverage() {
        val summary = summary(DigitalSelfDisciplineReviewPolicy.Range.Today)
        val ratio = DigitalSelfDisciplineReviewPresentation.trend(summary)
        val gap = DigitalSelfDisciplineReviewPresentation.trend(
            summary,
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
        )

        assertEquals(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO, ratio.metric)
        assertEquals(DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP, gap.metric)
        assertEquals(ratio.coverageText, gap.coverageText)
        assertTrue(ratio.semanticSummary.contains("间用比"))
        assertTrue(gap.semanticSummary.contains("未使用间隔"))
    }

    @Test
    fun interceptTrendUsesIntervalAndHasNoRatioSelectorMetric() {
        val summary = interceptSummary()
        val trend = DigitalSelfDisciplineReviewPresentation.trend(summary)

        assertEquals(DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL, trend.metric)
        assertTrue(trend.semanticSummary.contains("拦截间隔"))
        assertTrue(summary.ratioStats == null)
    }

    @Test
    fun gapTrendUsesGapComparisonInsteadOfRatioComparison() {
        val currentBounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            LocalDate.of(2026, 8, 4),
            zone,
        )
        val previousBounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            LocalDate.of(2026, 8, 3),
            zone,
        )
        val previous = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = listOf(
                UsageReviewRow(10L, "old", "Old", emptyList(), 10, previousBounds.startAt + 1_000L, 5, 30L * 60_000L),
            ),
            events = emptyList(),
            bounds = previousBounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = zone,
        )
        val current = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = listOf(
                UsageReviewRow(11L, "new", "New", emptyList(), 30, currentBounds.startAt + 1_000L, 5, 120L * 60_000L),
                UsageReviewRow(12L, "new", "New", emptyList(), 60, currentBounds.startAt + 2_000L, 5, 60L * 60_000L),
            ),
            events = emptyList(),
            bounds = currentBounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = zone,
            previousSummary = previous,
        )

        val gap = DigitalSelfDisciplineReviewPresentation.trend(
            current,
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
            zone,
        )

        assertTrue(gap.previousAverageText.contains("30分"))
        assertTrue(gap.deltaText.contains("1小时"))
        assertTrue(!gap.previousAverageText.contains("×"))
    }

    @Test
    fun trendKeepsSinglePointsForSmallTodayDataAndAggregatesAfterTwentyFour() {
        val small = summary(DigitalSelfDisciplineReviewPolicy.Range.Today)
        assertEquals(2, DigitalSelfDisciplineReviewPresentation.trend(small).points.size)

        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            LocalDate.of(2026, 8, 4),
            zone,
        )
        val rows = (0 until 25).map { index ->
            UsageReviewRow(
                id = index.toLong() + 1,
                appId = "app",
                appName = "App",
                tagNames = emptyList(),
                requestedDurationMinutes = 30,
                requestedAt = bounds.startAt + index * 1_000L,
                endReason = 5,
                requestGapMs = 60_000L,
            )
        }
        val many = DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = rows,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = zone,
        )
        val trend = DigitalSelfDisciplineReviewPresentation.trend(many)
        assertTrue(trend.points.size <= 24)
        assertTrue(trend.points.any { it.sampleCount > 1 })
    }

    @Test
    fun pagePresentationContainsCoverageAndDoesNotExposeSensitiveFields() {
        val page = DigitalSelfDisciplineReviewPresentation.page(summary(DigitalSelfDisciplineReviewPolicy.Range.SevenDays))

        assertTrue(page.coverage.text.contains("总申请"))
        assertTrue(page.trend.semanticSummary.contains("总记录"))
        assertTrue(page.recentRows.first().secondaryText.contains("标签：学习"))
        assertTrue(page.recentRows.first().secondaryText.contains("结束状态：主动结束"))
        assertTrue(page.recentRows.all { row ->
            listOf("reasonText", "http", "pattern", "selector", "node text").none { forbidden ->
                row.primaryText.contains(forbidden, ignoreCase = true) || row.secondaryText.contains(forbidden, ignoreCase = true)
            }
        })
    }

    @Test
    fun homeSummaryAndFilterVisibilityRemainStable() {
        assertEquals("今日 2 次申请 · 3 次拦截", DigitalSelfDisciplineReviewPresentation.homeSummary(2, 3))
        assertTrue(
            DigitalSelfDisciplineReviewPresentation.showInterceptFilters(
                DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt,
            ),
        )
        assertTrue(
            !DigitalSelfDisciplineReviewPresentation.showInterceptFilters(
                DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            ),
        )
    }

    private fun summary(range: DigitalSelfDisciplineReviewPolicy.Range): DigitalSelfDisciplineReviewPolicy.ReviewSummary {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            range,
            LocalDate.of(2026, 8, 4),
            zone,
        )
        val rows = listOf(
            UsageReviewRow(1L, "a", "A", listOf("学习"), 30, bounds.startAt + 1_000L, 5, 120L * 60_000L),
            UsageReviewRow(2L, "a", "A", listOf("学习"), 60, bounds.startAt + 2_000L, 5, 60L * 60_000L),
        )
        return DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = rows,
            events = emptyList(),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = zone,
        )
    }

    private fun interceptSummary(): DigitalSelfDisciplineReviewPolicy.ReviewSummary {
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            LocalDate.of(2026, 8, 4),
            zone,
        )
        val event = li.songe.gkd.sdp.data.SelfControlAttemptEvent(
            id = 1L,
            eventKey = "app_blocker:app",
            eventKind = li.songe.gkd.sdp.data.SelfControlAttempt.KIND_APP_BLOCKER,
            subjectId = "app",
            subjectLabel = "App",
            occurredAt = bounds.startAt + 1_000L,
            intervalMs = 60_000L,
        )
        return DigitalSelfDisciplineReviewPolicy.summarize(
            usageRows = emptyList(),
            events = listOf(event),
            bounds = bounds,
            reviewType = DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt,
            interceptFilter = DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.All,
            zoneId = zone,
        )
    }
}
