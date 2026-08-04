package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardRequestIntervalContractTest {
    @Test
    fun overlayUsesOnlyTheSelectedAppAndAtMostFiveCompletedIntervals() = runBlocking {
        val records = listOf(
            record(1, "target", 1_000L, null),
            record(2, "target", 2_000L, 1_000L),
            record(3, "other", 3_000L, null),
            record(4, "target", 4_000L, 2_000L),
            record(5, "target", 5_000L, 1_000L),
            record(6, "target", 6_000L, 1_000L),
            record(7, "target", 7_000L, 1_000L),
        )
        val repository = SelfControlIntervalRepository(
            usageRecords = object : SelfControlIntervalRepository.UsageRecordSource {
                override suspend fun queryRecentRecords(appId: String, limit: Int): List<UsageGuardRecord> =
                    records.filter { it.appId == appId }.sortedByDescending { it.requestedAt }.take(limit)

                override suspend fun getPreviousRecord(appId: String, requestedAt: Long, id: Long): UsageGuardRecord? = null
                override fun queryByRequestedAtRange(startAt: Long, endAt: Long): Flow<List<UsageGuardRecord>> = emptyFlow()
            },
            attemptEvents = unusedAttemptSource(),
        )

        val overlay = repository.loadUsageRequestOverlayData("target", 7_000L)

        assertEquals(7_000L, overlay.latestRequestedAt)
        assertEquals(
            listOf(2_000L, 1_000L, 1_000L, 1_000L, 1_000L),
            overlay.samples.mapNotNull { it.gapMs }.takeLast(5),
        )
    }

    @Test
    fun cancellationDoesNotCreateAnElapsedAnchor() {
        val state = SelfControlElapsedPolicy.stateForUsageRequest(previousRequestedAt = null)
        assertTrue(state is SelfControlElapsedPolicy.ElapsedState.NoHistory)
        assertFalse(state is SelfControlElapsedPolicy.ElapsedState.Running)
    }

    private fun record(id: Long, appId: String, requestedAt: Long, requestGapMs: Long?) = UsageGuardRecord(
        id = id,
        appId = appId,
        appName = appId,
        tagNames = emptyList(),
        reasonText = "local test reason",
        requestedDurationMinutes = 10,
        requestedAt = requestedAt,
        grantedAt = requestedAt,
        expiresAt = requestedAt + 600_000L,
        requestGapMs = requestGapMs,
    )

    private fun unusedAttemptSource() = object : SelfControlIntervalRepository.AttemptEventSource {
        override suspend fun recordEventAndGetInsight(event: SelfControlAttemptEvent) =
            SelfControlAttempt.RecordedAttemptInsight(null, emptyList())

        override fun queryByOccurredAtRange(startAt: Long, endAt: Long): Flow<List<SelfControlAttemptEvent>> = emptyFlow()
    }
}
