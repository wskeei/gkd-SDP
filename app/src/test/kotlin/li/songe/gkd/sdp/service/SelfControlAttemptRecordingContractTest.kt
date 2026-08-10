package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlAttemptRecordingContractTest {
    @Test
    fun repositoryRecordsSafeDescriptorWithoutSensitiveFields() = runBlocking {
        var captured: SelfControlAttemptEvent? = null
        val repository = SelfControlIntervalRepository(
            usageRecords = unusedUsageSource(),
            attemptEvents = object : SelfControlIntervalRepository.AttemptEventSource {
                override suspend fun recordEventAndGetInsight(
                    event: SelfControlAttemptEvent,
                ): SelfControlAttempt.RecordedAttemptInsight {
                    captured = event
                    return SelfControlAttempt.RecordedAttemptInsight(null, emptyList())
                }

                override fun queryByOccurredAtRange(startAt: Long, endAt: Long): Flow<List<SelfControlAttemptEvent>> = emptyFlow()
            },
        )

        repository.recordIntercept(
            descriptor = SelfControlIntervalRepository.AttemptDescriptor(
                eventKey = "url_intercept:42",
                eventKind = SelfControlAttempt.KIND_URL_INTERCEPT,
                subjectId = "42",
                subjectLabel = "网址规则 #42",
            ),
            occurredAt = 100L,
        )

        val event = requireNotNull(captured)
        assertEquals("url_intercept:42", event.eventKey)
        assertEquals("42", event.subjectId)
        assertEquals("网址规则 #42", event.subjectLabel)
        assertNull(event.intervalMs)
        assertTrue(SelfControlIntervalRepository.normalizeLabel(" 规则   42 ", "fallback") == "规则 42")
    }

    private fun unusedUsageSource() = object : SelfControlIntervalRepository.UsageRecordSource {
        override suspend fun queryRecentRecords(appId: String, limit: Int) = emptyList<li.songe.gkd.sdp.data.UsageGuardRecord>()
        override fun queryByRequestedAtRange(startAt: Long, endAt: Long): Flow<List<li.songe.gkd.sdp.data.UsageGuardRecord>> = emptyFlow()

        override suspend fun queryInsightRows(
            appId: String,
            startAt: Long,
            endAt: Long,
        ): List<li.songe.gkd.sdp.data.UsageRequestInsightRow> = emptyList()

        override suspend fun getLatestInsightRow(
            appId: String,
        ): li.songe.gkd.sdp.data.UsageRequestInsightRow? = null
    }
}
