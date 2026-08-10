package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRequestRhythmPolicyTest {
    @Test
    fun gapUsesLastActualUsageEndRatherThanPreviousRequestStart() {
        val tenAm = 10L * 60L * 60L * 1_000L
        val noon = 12L * 60L * 60L * 1_000L

        assertEquals(2L * 60L * 60L * 1_000L, UsageRequestRhythmPolicy.gapMs(tenAm, noon))
    }

    @Test
    fun missingOrFutureAnchorProducesNoGap() {
        assertNull(UsageRequestRhythmPolicy.gapMs(null, 100L))
        assertNull(UsageRequestRhythmPolicy.gapMs(101L, 100L))
        assertEquals(0L, UsageRequestRhythmPolicy.gapMs(100L, 100L))
    }

    @Test
    fun gapDoesNotOverflowAtLongBoundary() {
        assertEquals(
            Long.MAX_VALUE,
            UsageRequestRhythmPolicy.gapMs(0L, Long.MAX_VALUE),
        )
        assertNull(UsageRequestRhythmPolicy.gapMs(-1L, Long.MAX_VALUE))
    }

    @Test
    fun ratioIsGapDividedByRequestedDuration() {
        assertEquals(4.0, requireNotNull(UsageRequestRhythmPolicy.ratio(120L * 60_000L, 30)), 0.0001)
        assertEquals(3.0, requireNotNull(UsageRequestRhythmPolicy.ratio(90L * 60_000L, 30)), 0.0001)
    }

    @Test
    fun invalidRatioInputsAreMissingAndNeverInfinite() {
        assertNull(UsageRequestRhythmPolicy.ratio(null, 30))
        assertNull(UsageRequestRhythmPolicy.ratio(-1L, 30))
        assertNull(UsageRequestRhythmPolicy.ratio(60_000L, 0))
        assertNull(UsageRequestRhythmPolicy.ratio(60_000L, -1))
        assertFalse(UsageRequestRhythmPolicy.ratio(Long.MAX_VALUE, 1)!!.isInfinite())
    }

    @Test
    fun ratioAverageUsesPerRequestRatiosRatherThanSumOverSums() {
        val average = UsageRequestRhythmPolicy.averageRatio(
            listOf(
                UsageRequestRhythmPolicy.Sample(gapMs = 120L * 60_000L, durationMinutes = 30),
                UsageRequestRhythmPolicy.Sample(gapMs = 60L * 60_000L, durationMinutes = 60),
            ),
        )

        assertEquals(2.5, requireNotNull(average), 0.0001)
    }

    @Test
    fun currentRatioChangesWithSelectedDurationButHistoryAverageDoesNot() {
        val history = listOf(
            UsageRequestRhythmPolicy.Sample(gapMs = 120L * 60_000L, durationMinutes = 30),
            UsageRequestRhythmPolicy.Sample(gapMs = 60L * 60_000L, durationMinutes = 60),
        )
        val historyAverage = UsageRequestRhythmPolicy.averageRatio(history)

        assertEquals(4.0, requireNotNull(UsageRequestRhythmPolicy.currentRatio(120L * 60_000L, 30)), 0.0001)
        assertEquals(2.0, requireNotNull(UsageRequestRhythmPolicy.currentRatio(120L * 60_000L, 60)), 0.0001)
        assertEquals(2.5, requireNotNull(historyAverage), 0.0001)
    }

    @Test
    fun ratioFormattingIsStableForUi() {
        assertEquals("4.0", UsageRequestRhythmPolicy.formatRatio(4.0))
        assertNull(UsageRequestRhythmPolicy.formatRatio(null))
        assertTrue(UsageRequestRhythmPolicy.formatRatio(1.234)!!.contains("1.23"))
        assertEquals("10.0", UsageRequestRhythmPolicy.formatRatio(10.0))
        assertEquals("100", UsageRequestRhythmPolicy.formatRatio(100.0))
        assertEquals("120", UsageRequestRhythmPolicy.formatRatio(120.0))
        assertEquals("0.0", UsageRequestRhythmPolicy.formatRatio(0.0))
        assertEquals("<0.01", UsageRequestRhythmPolicy.formatRatio(0.001))
    }

    @Test
    fun formulaUsesOneCommonUnitForBothOperands() {
        val seconds = requireNotNull(UsageRequestRhythmPolicy.formula(3_000L, 2))
        assertEquals(UsageRequestRhythmPolicy.FormulaUnit.SECONDS, seconds.unit)
        assertEquals(3.0, seconds.gapValue.toDouble(), 0.0001)
        assertEquals(120.0, seconds.durationValue.toDouble(), 0.0001)
        assertEquals(0.025, seconds.ratio, 0.000001)

        val minutes = requireNotNull(UsageRequestRhythmPolicy.formula(2L * 60L * 60L * 1_000L, 30))
        assertEquals(UsageRequestRhythmPolicy.FormulaUnit.MINUTES, minutes.unit)
        assertEquals(120.0, minutes.gapValue.toDouble(), 0.0001)
        assertEquals(30.0, minutes.durationValue.toDouble(), 0.0001)
        assertEquals(4.0, minutes.ratio, 0.0001)

        val hours = requireNotNull(UsageRequestRhythmPolicy.formula(3L * 60L * 60L * 1_000L, 120))
        assertEquals(UsageRequestRhythmPolicy.FormulaUnit.HOURS, hours.unit)
        assertEquals(3.0, hours.gapValue.toDouble(), 0.0001)
        assertEquals(2.0, hours.durationValue.toDouble(), 0.0001)
        assertEquals(1.5, hours.ratio, 0.0001)
    }

    @Test
    fun formulaKeepsSmallPositiveRatiosVisible() {
        val formula = requireNotNull(UsageRequestRhythmPolicy.formula(1_000L, 10))

        assertTrue(formula.ratio > 0.0)
        assertEquals("<0.01", UsageRequestRhythmPolicy.formatRatio(formula.ratio))
    }
}
