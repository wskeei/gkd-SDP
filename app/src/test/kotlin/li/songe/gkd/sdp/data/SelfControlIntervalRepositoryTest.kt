package li.songe.gkd.sdp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlIntervalRepositoryTest {
    private class FakeUsageSource(
        private val recent: List<UsageGuardRecord> = emptyList(),
        private val ranged: List<UsageGuardRecord> = emptyList(),
        private val insightRows: List<UsageRequestInsightRow> = emptyList(),
        private val latestInsight: UsageRequestInsightRow? = null,
    ) : SelfControlIntervalRepository.UsageRecordSource {
        var insightQueries = 0
        override suspend fun queryRecentRecords(appId: String, limit: Int): List<UsageGuardRecord> {
            return recent.filter { it.appId == appId }.take(limit)
        }

        override fun queryByRequestedAtRange(startAt: Long, endAt: Long): Flow<List<UsageGuardRecord>> {
            return flowOf(ranged.filter { it.requestedAt in startAt until endAt })
        }

        override suspend fun queryInsightRows(
            appId: String,
            startAt: Long,
            endAt: Long,
        ): List<UsageRequestInsightRow> {
            insightQueries++
            return insightRows.filter { it.requestedAt in startAt..endAt }
        }

        override suspend fun getLatestInsightRow(appId: String): UsageRequestInsightRow? = latestInsight
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
        val latest = UsageRequestInsightRow(
            id = 3L,
            requestedAt = 7_000L,
            requestedDurationMinutes = 10,
            lastUsageEndedAt = 6_000L,
            requestGapMs = 4_000L,
        )
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(
                recent = listOf(
                    record(3L, "target", 7_000L),
                    record(2L, "target", 3_000L),
                    record(1L, "other", 1_000L),
                ),
                insightRows = listOf(latest),
                latestInsight = latest,
            ),
            attemptEvents = FakeAttemptSource(),
        )

        val result = repository.loadUsageRequestOverlayData("target", 7_000L)

        assertEquals(7_000L, result.latestRequestedAt)
        assertEquals(listOf(4_000L), result.samples.mapNotNull { it.gapMs })
        assertEquals(SelfControlIntervalRepository.UsageGapAnchorStatus.Available, result.anchorStatus)
    }

    @Test
    fun emptyUsageOverlayDoesNotInventAnAnchor() = runBlocking {
        val repository = SelfControlIntervalRepository(FakeUsageSource(), FakeAttemptSource())

        val result = repository.loadUsageRequestOverlayData("target", 7_000L)

        assertNull(result.latestRequestedAt)
        assertTrue(result.samples.isEmpty())
        assertEquals(SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest, result.anchorStatus)
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
    fun reviewSourceKeepsOnlyTheRequestedRangeWithoutPredecessorQueries() = runBlocking {
        val current = record(2L, "target", 20_000L)
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(
                ranged = listOf(current),
            ),
            attemptEvents = FakeAttemptSource(),
        )

        val source = repository.observeReviewSource(15_000L, 25_000L).first()

        assertEquals(listOf(current), source.usageRecords)
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

    @Test
    fun usageOverlayUsesStoredGapAndDurationAndDoesNotFallbackPastUnknownAnchor() = runBlocking {
        val latest = UsageRequestInsightRow(
            id = 3L,
            requestedAt = 30_000L,
            requestedDurationMinutes = 30,
            lastUsageEndedAt = null,
            requestGapMs = null,
        )
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(
                insightRows = listOf(
                    UsageRequestInsightRow(1L, 1_000L, 30, 500L, 500L),
                    UsageRequestInsightRow(2L, 2_000L, 60, 1_500L, 1_000L),
                ),
                latestInsight = latest,
            ),
            attemptEvents = FakeAttemptSource(),
        )

        val data = repository.loadUsageRequestOverlayData(
            appId = "target",
            insightAnchorAt = 30_000L,
        )

        assertEquals(SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd, data.anchorStatus)
        assertEquals(null, data.previousLastUsageEndedAt)
        assertEquals(listOf(1L, 2L), data.samples.map { it.id })
        assertEquals(30, data.samples.first().requestedDurationMinutes)
    }

    @Test
    fun usageOverlayKeepsRowsWithUnknownGapsForCoverage() = runBlocking {
        val rows = listOf(
            UsageRequestInsightRow(1L, 1_000L, 30, null, null),
            UsageRequestInsightRow(2L, 2_000L, 30, 1_500L, 500L),
            UsageRequestInsightRow(3L, 3_000L, 30, 2_500L, null),
            UsageRequestInsightRow(4L, 4_000L, 30, 3_500L, 1_000L),
            UsageRequestInsightRow(5L, 5_000L, 30, 4_500L, 1_000L),
            UsageRequestInsightRow(6L, 6_000L, 30, 5_500L, 1_000L),
        )
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(
                insightRows = rows,
                latestInsight = rows.last(),
            ),
            attemptEvents = FakeAttemptSource(),
        )

        val data = repository.loadUsageRequestOverlayData(
            appId = "target",
            insightAnchorAt = 6_000L,
        )

        assertEquals(6, data.samples.size)
        assertEquals(2, data.samples.count { it.gapMs == null })
        assertEquals(listOf(500L, 1_000L, 1_000L, 1_000L), data.samples.mapNotNull { it.gapMs })
    }

    @Test
    fun futureDatedLatestRequestCannotProvideTheCurrentUsageGapAnchor() = runBlocking {
        val latest = UsageRequestInsightRow(
            id = 4L,
            requestedAt = 40_000L,
            requestedDurationMinutes = 30,
            lastUsageEndedAt = 39_000L,
            requestGapMs = 1_000L,
        )
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(latestInsight = latest),
            attemptEvents = FakeAttemptSource(),
        )

        val data = repository.loadUsageRequestOverlayData(
            appId = "target",
            insightAnchorAt = 30_000L,
        )

        assertEquals(SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd, data.anchorStatus)
        assertNull(data.previousLastUsageEndedAt)
    }

    @Test
    fun recordedInterceptInsightCarriesTheFreshThirtyDayDatasetAndCurrentId() = runBlocking {
        val attempts = FakeAttemptSource(
            result = SelfControlAttempt.RecordedAttemptInsight(
                previousOccurredAt = 100L,
                recentCompletedIntervalsMs = listOf(50L),
                samples = listOf(
                    SelfControlInsightWindowPolicy.IntervalSample(
                        id = 7L,
                        occurredAtEpochMs = 200L,
                        gapMs = 100L,
                        requestedDurationMinutes = null,
                    )
                ),
                currentEventId = 7L,
            ),
        )
        val repository = SelfControlIntervalRepository(FakeUsageSource(), attempts)

        val result = repository.recordIntercept(
            descriptor = SelfControlIntervalRepository.AttemptDescriptor(
                eventKey = "selector_intercept:v2:42:demo.app:2:7:key:9",
                eventKind = SelfControlAttempt.KIND_SELECTOR_INTERCEPT,
                subjectId = "demo.app",
                subjectLabel = "确认按钮",
            ),
            occurredAt = 200L,
        )

        val overlay = repository.interceptOverlayData(200L, result)
        assertEquals(7L, overlay.currentEventId)
        assertEquals(1, overlay.samples.size)
        assertEquals(200L, overlay.insightAnchorAt)
    }

    @Test
    fun futureUsageEndIsUnavailableInsteadOfBecomingAZeroGap() = runBlocking {
        val repository = SelfControlIntervalRepository(
            usageRecords = FakeUsageSource(
                latestInsight = UsageRequestInsightRow(
                    id = 3L,
                    requestedAt = 7_000L,
                    requestedDurationMinutes = 10,
                    lastUsageEndedAt = 8_000L,
                    requestGapMs = null,
                ),
            ),
            attemptEvents = FakeAttemptSource(),
        )

        val result = repository.loadUsageRequestOverlayData("target", 7_000L)

        assertEquals(SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd, result.anchorStatus)
        assertNull(result.previousLastUsageEndedAt)
    }
}
