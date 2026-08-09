package li.songe.gkd.sdp.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import li.songe.gkd.sdp.util.launchTry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RedactionPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `redaction removes sensitive Android and user supplied values`() {
        val sensitiveValues = listOf(
            "Intent{data=https://example.test/private?q=secret-query extras=Bundle{reason=secret-reason}}",
            "content://snapshots/secret-node-text",
            "Authorization: Bearer secret-token",
            "Cookie: session=secret-cookie",
            "contact=13800138000 user@example.test",
            "/Users/private-person/Documents/secret-file.txt",
            "notificationText=secret-notification",
            "Throwable(message=secret-throwable-message)",
        )

        val result = sensitiveValues.joinToString("|") { RedactionPolicy.redact(it) }

        for (secret in listOf(
            "secret-query",
            "secret-reason",
            "secret-node-text",
            "secret-token",
            "secret-cookie",
            "13800138000",
            "user@example.test",
            "private-person",
            "secret-notification",
            "secret-throwable-message",
        )) {
            assertFalse(secret, result.contains(secret))
        }
    }

    @Test
    fun `redacted values are bounded`() {
        assertTrue(RedactionPolicy.redact("x".repeat(400)).length <= 80)
    }

    @Test
    fun `stable identifiers use a salted twelve digit hash`() {
        val first = RedactionPolicy.stableIdHash("same-id", ByteArray(32) { 1 })
        val second = RedactionPolicy.stableIdHash("same-id", ByteArray(32) { 2 })

        assertTrue(first.matches(Regex("[0-9a-f]{12}")))
        assertNotEquals(first, second)
        assertEquals(first, RedactionPolicy.stableIdHash("same-id", ByteArray(32) { 1 }))
    }

    @Test
    fun `diagnostic event exposes only approved production fields`() {
        val fields = Json.encodeToJsonElement(
            DiagnosticEvent.serializer(),
            DiagnosticEvent(
                eventCode = DiagnosticEventCode.RUNTIME_FAILURE,
                stage = DiagnosticStage.APP,
                result = DiagnosticResult.FAILED,
                entityHash = "0123456789ab",
                count = 1,
                durationBucket = DiagnosticDurationBucket.UNDER_1_SECOND,
                errorCategory = DiagnosticErrorCategory.UNKNOWN,
            ),
        ).jsonObject.keys

        assertEquals(
            setOf(
                "eventCode",
                "stage",
                "result",
                "entityHash",
                "count",
                "durationBucket",
                "errorCategory",
            ),
            fields,
        )
    }

    @Test
    fun `rate limiter caps identical events at twenty per minute`() {
        val limiter = DiagnosticRateLimiter(maxEvents = 20, windowMillis = 60_000L)
        val event = DiagnosticEvent(eventCode = DiagnosticEventCode.LEGACY_CALL)

        assertEquals(20, (1..25).count { limiter.tryAcquire(event, nowMillis = 10_000L) })
        assertTrue(limiter.tryAcquire(event, nowMillis = 70_001L))
    }

    @Test
    fun `diagnostic files stay within total limit and retain at most two rotations`() {
        val directory = temporaryFolder.newFolder("diagnostics")
        val store = DiagnosticEventFileStore(
            directory = directory,
            activeFilename = "events.jsonl",
            maxFileBytes = 10,
            maxTotalBytes = 25,
            maxRotatedFiles = 2,
        )

        repeat(8) { store.append("1234567\n".toByteArray()) }

        val files = directory.listFiles().orEmpty()
        assertTrue(files.sumOf { it.length() } <= 25)
        assertTrue(files.count { it.name.matches(Regex("events\\.\\d+\\.jsonl")) } <= 2)
        assertEquals(
            setOf("events.jsonl", "events.1.jsonl", "events.2.jsonl"),
            files.map { it.name }.toSet(),
        )
    }

    @Test
    fun `launchTry preserves coroutine cancellation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launchTry {
            throw CancellationException("stop")
        }

        job.join()
        assertTrue(job.isCancelled)
    }
}
