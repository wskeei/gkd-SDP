package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import java.time.format.DateTimeFormatter

object DigitalSelfDisciplineReviewPresentation {
    val emptyText: String = "暂无可绘制的有效间隔"

    data class ChartPoint(
        val label: String,
        val valueMs: Long,
    )

    fun chartPoints(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary): List<ChartPoint> {
        return if (summary.range == DigitalSelfDisciplineReviewPolicy.Range.Today) {
            summary.recentIntervals
                .asReversed()
                .mapIndexed { index, item -> ChartPoint("${index + 1}", item.intervalMs) }
        } else {
            summary.dailyBuckets.map { bucket ->
                ChartPoint(
                    label = bucket.date.format(DateTimeFormatter.ofPattern("MM-dd")),
                    valueMs = bucket.medianMs,
                )
            }
        }
    }

    fun homeSummary(requestCount: Int, interceptCount: Int): String {
        return "今日 ${requestCount.coerceAtLeast(0)} 次申请 · ${interceptCount.coerceAtLeast(0)} 次拦截"
    }

    fun showInterceptFilters(reviewType: DigitalSelfDisciplineReviewPolicy.ReviewType): Boolean {
        return reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt
    }
}
