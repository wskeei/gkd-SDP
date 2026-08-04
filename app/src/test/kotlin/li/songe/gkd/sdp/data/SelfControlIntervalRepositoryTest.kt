package li.songe.gkd.sdp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlIntervalRepositoryTest {
    private class FakeUsageSource(
        private val recent: List<UsageGuardRecord> = emptyList(),
        private val ranged: List<UsageGuardRecord> = emptyList(),
        private val predecessors: Map<Pair<String, Long>, UsageGuardRecord?> = emptyMap(),
    ) : SelfControlIntervalRepository.UsageRecordSource {
        override suspend fun queryRecentRecords(appId: String, limit: Int): List<UsageGuardRecord> {
            return recent.filter { it.appId == appId }.take(limit)
        }

        override suspend fun getPreviousRecord(
            appId: String,
            requestedAt: Long,
            id: Long,
        ): UsageGuardRecord? = predecessors[appId to id]

        override fun queryByRequestedAtRange(startAt: Long, endAt: Long): Flow<List<UsageGuardRecord>> {
            return flowOf(ranged.filter { it.requestedAt in startAt until endAt })
        }
    }

    private class FakeAttemptSource(
        private val ranged: List<SelfControlAttemptEvent> = emptyList(),
        private val result: SelfControlAttempt.RecordedAttemptInsight =
            SelfControlAttempt.RecordedAttemptInsight(null, emptyList()),
    ) : SelfControlIntervalRepository.AttemptEventSource {
        var recorded: SelfControlAttemptEvent? = null

        override suspend fun recordEventAndGetInsight(
            event: SelfControlAttemptEvent,
        ): SelfControlAttempt.RecordedAttemptInsight {
            recorded = event
            return result
        }

        override fun queryByOccurredAtRange(startAt: Long, endAt: Long): Flow<List<SelfControlAttemptEvent>> {
            return flowOf(ranged.filter { it.occurredAt in startAt until endAt })
        }
    }

    private fun record(id: Long, appId: String, requestedAt: Long) = UsageGuardRecord(
        id = id,
        appId = appId,
        appName = appId,
        tagNames = emptyList(),
        reasonText = "reason is not part of interval source",
        requestedDurationMinutes = 10,
        requestedAt = requestedAt,
        grantedAt = requestedAt,
        expiresAt = requestedAt + 600_000L,
    )

    @Test
    fun usageOverlayUsesOnlyCurrentAppAndReturnsLatestAnchor() = runBlocking {
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(
                recent = listOf(
                    record(3L, "target", 7_000L),
                    record(2L, "target", 3_000L),
                    record(1L, "other", 1_000L),
                ),
            ),
            attemptEvents = FakeAttemptSource(),
        )

        val result = repository.loadUsageRequestOverlay("target")

        assertEquals(7_000L, result.latestRequestedAt)
        assertEquals(listOf(4_000L), result.recentCompletedIntervalsMs)
        assertTrue(result.hasHistory)
    }

    @Test
    fun emptyUsageOverlayDoesNotInventAnAnchor() = runBlocking {
        val repository = SelfControlIntervalRepository(FakeUsageSource(), FakeAttemptSource())

        val result = repository.loadUsageRequestOverlay("target")

        assertNull(result.latestRequestedAt)
        assertTrue(result.recentCompletedIntervalsMs.isEmpty())
        assertTrue(!result.hasHistory)
    }

    @Test
    fun interceptDescriptorIsSanitizedAndNeverCarriesMessageOrUrl() = runBlocking {
        val attempts = FakeAttemptSource(
            result = SelfControlAttempt.RecordedAttemptInsight(100L, listOf(50L)),
        )
        val repository = SelfControlIntervalRepository(FakeUsageSource(), attempts)

        val result = repository.recordIntercept(
            descriptor = SelfControlIntervalRepository.AttemptDescriptor(
                eventKey = "url_intercept:7",
                eventKind = SelfControlAttempt.KIND_URL_INTERCEPT,
                subjectId = "7",
                subjectLabel = "  网址   规则\n#7  ",
            ),
            occurredAt = 200L,
        )

        assertEquals(100L, result.previousOccurredAt)
        assertEquals("网址 规则 #7", attempts.recorded?.subjectLabel)
        assertNull(attempts.recorded?.intervalMs)
        assertTrue(attempts.recorded?.subjectLabel?.contains("http") != true)
    }

    @Test
    fun reviewSourceAddsOneStrictPredecessorPerApp() = runBlocking {
        val current = record(2L, "target", 20_000L)
        val previous = record(1L, "target", 10_000L)
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(
                ranged = listOf(current),
                predecessors = mapOf("target" to 2L to previous),
            ),
            attemptEvents = FakeAttemptSource(),
        )

        val source = repository.observeReviewSource(15_000L, 25_000L).first()

        assertEquals(listOf(previous, current), source.usageRecords)
    }

    @Test
    fun reviewSourceMergesInterceptEventsWithoutUsageReasons() = runBlocking {
        val event = SelfControlAttemptEvent(
            id = 1L,
            eventKey = "app_blocker:target",
            eventKind = SelfControlAttempt.KIND_APP_BLOCKER,
            subjectId = "target",
            subjectLabel = "Target",
            occurredAt = 20_000L,
            intervalMs = 5_000L,
        )
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(),
            attemptEvents = FakeAttemptSource(ranged = listOf(event)),
        )

        val source = repository.observeReviewSource(15_000L, 25_000L).first()

        assertEquals(listOf(event), source.interceptEvents)
        assertTrue(source.interceptEvents.none { it.subjectLabel.contains("reason") })
        assertTrue(SelfControlIntervalPolicy.statsFor(listOf(event.intervalMs!!)).sampleCount == 1)
    }
}
