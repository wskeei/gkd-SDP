package li.songe.gkd.sdp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReviewDashboardFlowTest {
    @Test
    fun rollingWindowIsHalfOpenAndChartLabelsAreBounded() {
        val now = 1_000_000L
        val bounds = DigitalSelfDisciplineReviewPolicy.rangeBounds(
            DigitalSelfDisciplineReviewPolicy.Range.LAST_24_HOURS,
            now,
            ZoneId.of("Asia/Shanghai"),
        )

        assertTrue(bounds.contains(now - 1L))
        assertTrue(!bounds.contains(now))
        val points = (0 until 10).map { index ->
            DigitalSelfDisciplineReviewPresentation.TrendPoint(
                label = "08:$index",
                value = index.toDouble(),
                sampleCount = 1,
                occurredAt = bounds.startAt + index,
            )
        }
        assertTrue(DigitalSelfDisciplineReviewPresentation.xAxisLabels(points).size <= 6)
        assertEquals(
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO,
            DigitalSelfDisciplineReviewPresentation.defaultMetric(
                DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest,
            ),
        )
    }
}
