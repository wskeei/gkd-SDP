package li.songe.gkd.sdp.usage

import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageRequestPresenterTest {
    private fun data(
        previousLastUsageEndedAt: Long?,
        anchorStatus: SelfControlIntervalRepository.UsageGapAnchorStatus =
            SelfControlIntervalRepository.UsageGapAnchorStatus.Available,
    ): SelfControlIntervalRepository.UsageRequestOverlayData =
        SelfControlIntervalRepository.UsageRequestOverlayData(
            insightAnchorAt = 1_000L,
            latestRequestedAt = 900L,
            anchorStatus = anchorStatus,
            previousLastUsageEndedAt = previousLastUsageEndedAt,
            samples = emptyList(),
        )

    @Test
    fun firstRequestMapsToFirstStatus() {
        val state = UsageRequestPresenter.present(
            appId = "chat.app",
            appName = "Chat",
            data = data(
                previousLastUsageEndedAt = null,
                anchorStatus = SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest,
            ),
            nowEpochMs = 1_000L,
        )
        assertEquals(UsageRequestUiState.Status.FIRST, state.status)
    }

    @Test
    fun availableAnchorProducesAvailableStateAndGap() {
        val data = data(previousLastUsageEndedAt = 400L)
        val state = UsageRequestPresenter.present(
            appId = "chat.app",
            appName = "Chat",
            data = data,
            nowEpochMs = 1_000L,
        )
        assertEquals(UsageRequestUiState.Status.AVAILABLE, state.status)
        assertEquals(600L, UsageRequestPresenter.currentGapMs(data, 1_000L))
    }

    @Test
    fun missingActualEndAndUnavailableDoNotInventGap() {
        val missing = data(
            previousLastUsageEndedAt = null,
            anchorStatus = SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd,
        )
        assertEquals(
            UsageRequestUiState.Status.MISSING_ACTUAL_END,
            UsageRequestPresenter.present(
                appId = "a",
                appName = "A",
                data = missing,
                nowEpochMs = 1_000L,
            ).status,
        )
        assertEquals(
            UsageRequestUiState.Status.UNAVAILABLE,
            UsageRequestPresenter.present(
                appId = "a",
                appName = "A",
                data = null,
                nowEpochMs = 1_000L,
            ).status,
        )
    }

    @Test
    fun futureEndedAtCannotBeUsedAsCurrentAnchor() {
        val data = data(previousLastUsageEndedAt = 2_000L)
        assertEquals(
            UsageRequestUiState.Status.UNAVAILABLE,
            UsageRequestPresenter.present(
                appId = "a",
                appName = "A",
                data = data,
                nowEpochMs = 1_000L,
            ).status,
        )
    }
}
