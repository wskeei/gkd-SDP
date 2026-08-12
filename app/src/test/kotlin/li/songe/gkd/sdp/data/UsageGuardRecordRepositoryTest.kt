package li.songe.gkd.sdp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardRecordRepositoryTest {
    @Test
    fun closeRecordDelegatesToActiveUseMutation() = runBlocking {
        val source = FakeSource()
        install(source)
        try {
            val updated = UsageGuardRecordRepository.closeRecordFromActiveUse(
                id = 12L,
                endedAt = 5_000L,
                endReason = UsageGuardRecord.END_REASON_EXPIRED,
            )

            assertEquals(1, updated)
            assertEquals(12L, source.closedActiveId)
            assertEquals(5_000L, source.closedActiveEndedAt)
            assertEquals(UsageGuardRecord.END_REASON_EXPIRED, source.closedActiveReason)
        } finally {
            uninstall()
        }
    }

    @Test
    fun insertWithGapUsesPreviousEndedTimeWhenNoActiveRecord() = runBlocking {
        val previous = record(
            id = 7L,
            requestedAt = 1_000L,
            endedAt = 2_000L,
            lastUsageEndedAt = 2_000L,
        )
        val source = FakeSource(latest = previous)
        install(source)
        try {
            val id = UsageGuardRecordRepository.insertRequestWithGap(
                record = record(id = 0L, requestedAt = 12_000L),
                replacedAt = 99_000L,
            )

            assertEquals(21L, id)
            assertEquals(12_000L - 2_000L, source.inserted!!.requestGapMs)
            assertNull(source.inserted!!.lastUsageEndedAt)
        } finally {
            uninstall()
        }
    }

    @Test
    fun insertWithGapReplacesActiveRecordAndClearsGap() = runBlocking {
        val active = record(id = 7L, requestedAt = 1_000L, endedAt = 0L)
        val source = FakeSource(active = active)
        install(source)
        try {
            val id = UsageGuardRecordRepository.insertRequestWithGap(
                record = record(id = 0L, requestedAt = 12_000L),
                replacedAt = 11_000L,
            )

            assertEquals(21L, id)
            assertNull(source.inserted!!.requestGapMs)
            assertNull(source.inserted!!.lastUsageEndedAt)
            assertEquals(active.id, source.closedReplacedId)
            assertEquals(11_000L, source.closedReplacedEndedAt)
            assertEquals(UsageGuardRecord.END_REASON_REPLACED, source.closedReplacedReason)
            assertTrue(source.closedReplacedCalled)
        } finally {
            uninstall()
        }
    }

    private fun install(source: FakeSource) {
        UsageGuardRecordRepositoryTestHooks.source = source
        UsageGuardRecordRepositoryTestHooks.transaction = object : UsageGuardRecordTransaction {
            override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
        }
    }

    private fun uninstall() {
        UsageGuardRecordRepositoryTestHooks.source = null
        UsageGuardRecordRepositoryTestHooks.transaction = null
    }

    private fun record(
        id: Long,
        requestedAt: Long,
        endedAt: Long = 0L,
        lastUsageEndedAt: Long? = null,
    ) = UsageGuardRecord(
        id = id,
        appId = "chat.app",
        appName = "Chat",
        tagNames = listOf("work"),
        reasonText = "synthetic",
        requestedDurationMinutes = 5,
        requestedAt = requestedAt,
        grantedAt = requestedAt,
        expiresAt = requestedAt + 5L * 60_000L,
        endedAt = endedAt,
        lastUsageEndedAt = lastUsageEndedAt,
    )

    private class FakeSource(
        var active: UsageGuardRecord? = null,
        var latest: UsageGuardRecord? = null,
    ) : UsageGuardRecordSource {
        var inserted: UsageGuardRecord? = null
        var closedActiveId: Long? = null
        var closedActiveEndedAt: Long? = null
        var closedActiveReason: Int? = null
        var closedReplacedCalled = false
        var closedReplacedId: Long? = null
        var closedReplacedEndedAt: Long? = null
        var closedReplacedReason: Int? = null

        override suspend fun getActiveRecord(appId: String): UsageGuardRecord? = active

        override suspend fun getLatestRecord(appId: String): UsageGuardRecord? = latest

        override suspend fun insert(record: UsageGuardRecord): Long {
            inserted = record.copy(id = 21L)
            return 21L
        }

        override suspend fun closeRecord(id: Long, endedAt: Long, endReason: Int): Int {
            closedReplacedCalled = true
            closedReplacedId = id
            closedReplacedEndedAt = endedAt
            closedReplacedReason = endReason
            return 1
        }

        override suspend fun closeRecordFromActiveUse(
            id: Long,
            endedAt: Long,
            endReason: Int,
        ): Int {
            closedActiveId = id
            closedActiveEndedAt = endedAt
            closedActiveReason = endReason
            return 1
        }
    }
}
