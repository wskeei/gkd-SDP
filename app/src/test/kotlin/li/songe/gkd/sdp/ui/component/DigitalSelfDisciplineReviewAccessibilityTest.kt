package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.data.UsageReviewRow
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DigitalSelfDisciplineReviewAccessibilityTest {
    @Test
    fun semanticSummaryAndTextRowsDescribeEveryTrendPointWithoutSensitiveContent() {
        val zone = ZoneId.of("Asia/Shanghai")
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.Today,
            LocalDate.of(2026, 8, 4),
            zone,
        )
        val rows = (0 until 3).map { index ->
            UsageReviewRow(
                id = index.toLong() + 1,
                appId = "demo.app",
                appName = "Demo App",
                tagNames = listOf("测试"),
                requestedDurationMinutes = 30,
                requestedAt = bounds.startAt + index * 60_000L,
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

        assertEquals(trend.points.size, trend.textRows.size)
        assertTrue(trend.semanticSummary.contains("有效样本"))
        assertTrue(trend.semanticSummary.contains("总记录"))
        assertTrue(listOf("reasonText", "http", "pattern", "selector", "node text").none { forbidden ->
            trend.semanticSummary.contains(forbidden, ignoreCase = true) ||
                trend.textRows.any { it.contains(forbidden, ignoreCase = true) }
        })
    }
}
