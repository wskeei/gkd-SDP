package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardServicePresentersTest {
    @Test
    fun countdownPresenterDelegatesToStableDurationFormatting() {
        assertEquals("00:00", countdownRemainingText(0L))
        assertEquals("09:58", countdownRemainingText(598_000L))
        assertEquals("1:02:11", countdownRemainingText(3_731_000L))
        assertEquals(
            UsageGuardCountdownUiState(remainingMillis = 1L, reasonText = "test", showTerminateConfirm = false).remainingMillis,
            1L,
        )
    }

    @Test
    fun requestElapsedStateMapsLoadingAndUnavailable() {
        assertEquals(
            SelfControlElapsedPolicy.ElapsedState.Loading,
            usageRequestElapsedState(UsageRequestDatasetState.Loading, 1_000L),
        )
        assertEquals(
            SelfControlElapsedPolicy.ElapsedState.Unavailable,
            usageRequestElapsedState(UsageRequestDatasetState.Unavailable, 1_000L),
        )
    }

    @Test
    fun requestElapsedStateMapsReadyAnchors() {
        val ready = UsageRequestDatasetState.Ready(
            SelfControlIntervalRepository.UsageRequestOverlayData(
                insightAnchorAt = 1_000L,
                latestRequestedAt = 500L,
                anchorStatus = SelfControlIntervalRepository.UsageGapAnchorStatus.Available,
                previousLastUsageEndedAt = 900L,
                samples = emptyList(),
            ),
        )
        val running = usageRequestElapsedState(ready, 1_000L)
        assertTrue(running is SelfControlElapsedPolicy.ElapsedState.Running)

        val missing = usageRequestElapsedState(
            UsageRequestDatasetState.Ready(
                ready.data.copy(
                    anchorStatus = SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd,
                    previousLastUsageEndedAt = null,
                ),
            ),
            1_000L,
        )
        assertEquals(SelfControlElapsedPolicy.ElapsedState.MissingActualEnd, missing)
    }

    @Test
    fun requestElapsedStateMapsNoHistoryAndUnavailableReadyStates() {
        val base = SelfControlIntervalRepository.UsageRequestOverlayData(
            insightAnchorAt = 1_000L,
            latestRequestedAt = null,
            anchorStatus = SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest,
            previousLastUsageEndedAt = null,
            samples = emptyList(),
        )
        assertEquals(
            SelfControlElapsedPolicy.ElapsedState.NoHistory,
            usageRequestElapsedState(UsageRequestDatasetState.Ready(base), 1_000L),
        )
        assertEquals(
            SelfControlElapsedPolicy.ElapsedState.Unavailable,
            usageRequestElapsedState(
                UsageRequestDatasetState.Ready(
                    base.copy(
                        anchorStatus = SelfControlIntervalRepository.UsageGapAnchorStatus.Available,
                    ),
                ),
                1_000L,
            ),
        )
        assertEquals(
            SelfControlElapsedPolicy.ElapsedState.Unavailable,
            usageRequestElapsedState(
                UsageRequestDatasetState.Ready(
                    base.copy(
                        anchorStatus = SelfControlIntervalRepository.UsageGapAnchorStatus.Available,
                        previousLastUsageEndedAt = 2_000L,
                    ),
                ),
                1_000L,
            ),
        )
    }
}
