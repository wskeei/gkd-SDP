package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRequestRhythmPresentationTest {
    private val now = 12L * 60L * 60L * 1_000L

    private fun data(
        anchor: Long? = 10L * 60L * 60L * 1_000L,
        samples: List<SelfControlInsightWindowPolicy.IntervalSample> = listOf(
            SelfControlInsightWindowPolicy.IntervalSample(
                id = 1L,
                occurredAtEpochMs = now - 30L * 60_000L,
                gapMs = 120L * 60_000L,
                requestedDurationMinutes = 30,
            ),
        ),
    ) = SelfControlIntervalRepository.UsageRequestOverlayData(
        insightAnchorAt = now,
        latestRequestedAt = now - 30L * 60_000L,
        anchorStatus = if (anchor == null) {
            SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd
        } else {
            SelfControlIntervalRepository.UsageGapAnchorStatus.Available
        },
        previousLastUsageEndedAt = anchor,
        samples = samples,
    )

    @Test
    fun currentRatioUsesGapFromActualEndAndSelectedDuration() {
        val presentation = UsageRequestRhythmPresentation.from(data(), now, 30)

        assertEquals(2L * 60L * 60L * 1_000L, presentation.currentGapMs)
        assertEquals(4.0, presentation.currentRatio!!, 0.0001)
        assertTrue(presentation.averageRatioByWindow.values.any { it == 4.0 })
    }

    @Test
    fun changingDurationChangesOnlyCurrentRatio() {
        val thirty = UsageRequestRhythmPresentation.from(data(), now, 30)
        val sixty = UsageRequestRhythmPresentation.from(data(), now, 60)

        assertEquals(4.0, thirty.currentRatio!!, 0.0001)
        assertEquals(2.0, sixty.currentRatio!!, 0.0001)
        assertEquals(thirty.averageRatioByWindow, sixty.averageRatioByWindow)
    }

    @Test
    fun missingDurationOrClockRollbackDoesNotInventRatio() {
        assertNull(UsageRequestRhythmPresentation.from(data(), now, 0).currentRatio)
        val rollback = UsageRequestRhythmPresentation.from(data(), 9L * 60L * 60L * 1_000L, 30)
        assertNull(rollback.currentRatio)
        assertNull(rollback.currentGapMs)
        assertEquals(UsageRequestRhythmPresentation.Status.UNAVAILABLE, rollback.status)
        assertEquals(
            UsageRequestRhythmPresentation.Status.MISSING_ACTUAL_END,
            UsageRequestRhythmPresentation.from(data(anchor = null), now, 30).status,
        )
    }

    @Test
    fun firstAndUnavailableStatesHaveDistinctCopy() {
        val first = UsageRequestRhythmPresentation.from(
            SelfControlIntervalRepository.UsageRequestOverlayData(
                insightAnchorAt = now,
                latestRequestedAt = null,
                anchorStatus = SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest,
                previousLastUsageEndedAt = null,
                samples = emptyList(),
            ),
            now,
            30,
        )
        val unavailable = UsageRequestRhythmPresentation.from(null, now, 30)

        assertEquals(UsageRequestRhythmPresentation.Status.FIRST, first.status)
        assertTrue(first.statusText.contains("此前没有"))
        assertEquals(UsageRequestRhythmPresentation.Status.UNAVAILABLE, unavailable.status)
        assertTrue(unavailable.statusText.contains("暂时无法"))
    }
}
