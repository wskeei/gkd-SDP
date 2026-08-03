package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlIntervalPresentationTest {
    private val now = 10_000_000L

    @Test
    fun noHistoryDoesNotRenderAZeroBar() {
        val presentation = SelfControlIntervalPresentation.from(
            SelfControlIntervalPolicy.overlayInsight(
                anchorAtEpochMs = now,
                firstOccurrence = true,
                recentCompletedIntervalsMs = emptyList(),
                nowEpochMs = now,
            )
        )

        assertTrue(presentation.chartPoints.isEmpty())
        assertTrue(presentation.supportingText.contains("完成"))
        assertFalse(presentation.semanticSummary.contains("0秒"))
    }

    @Test
    fun oneAndFiveHistoryPointsKeepOldestToNewestThenCurrentOrder() {
        val one = SelfControlIntervalPresentation.from(
            SelfControlIntervalPolicy.overlayInsight(
                anchorAtEpochMs = now - 65_000L,
                firstOccurrence = false,
                recentCompletedIntervalsMs = listOf(60_000L),
                nowEpochMs = now,
            )
        )
        assertEquals(listOf("第1次", "本次"), one.chartPoints.map { it.label })

        val five = SelfControlIntervalPresentation.from(
            SelfControlIntervalPolicy.overlayInsight(
                anchorAtEpochMs = now - 6_000L,
                firstOccurrence = false,
                recentCompletedIntervalsMs = listOf(1L, 2L, 3L, 4L, 5L),
                nowEpochMs = now,
            )
        )
        assertEquals(
            listOf("第1次", "第2次", "第3次", "第4次", "第5次", "本次"),
            five.chartPoints.map { it.label },
        )
        assertEquals(6, five.chartPoints.size)
    }

    @Test
    fun currentValueIsExcludedFromHistoryStatsButAppearsInSummary() {
        val presentation = SelfControlIntervalPresentation.from(
            SelfControlIntervalPolicy.overlayInsight(
                anchorAtEpochMs = now - 121_000L,
                firstOccurrence = false,
                recentCompletedIntervalsMs = listOf(60_000L, 120_000L),
                nowEpochMs = now,
            )
        )

        assertEquals(90_000L, presentation.stats.averageMs)
        assertEquals(90_000L, presentation.stats.medianMs)
        assertEquals("本次间隔比平均值多 31 秒", presentation.comparisonText)
        assertTrue(presentation.semanticSummary.contains("2 个"))
        assertTrue(presentation.semanticSummary.contains("平均 1分 30秒"))
        assertTrue(presentation.semanticSummary.contains("中位数 1分 30秒"))
    }

    @Test
    fun largeSpanGetsAReadableHintAndNeverIncludesReasonText() {
        val presentation = SelfControlIntervalPresentation.from(
            SelfControlIntervalPolicy.overlayInsight(
                anchorAtEpochMs = now - 3_600_000L,
                firstOccurrence = false,
                recentCompletedIntervalsMs = listOf(1_000L, 30_000L),
                nowEpochMs = now,
            )
        )

        assertTrue(presentation.supportingText.contains("跨度较大"))
        assertFalse(presentation.semanticSummary.contains("理由"))
        assertFalse(presentation.semanticSummary.contains("reason"))
    }
}
