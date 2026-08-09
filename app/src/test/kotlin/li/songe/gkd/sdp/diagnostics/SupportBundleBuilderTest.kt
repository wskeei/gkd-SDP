package li.songe.gkd.sdp.diagnostics

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class SupportBundleBuilderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `support bundle contains only allowlisted sanitized summaries`() {
        val sourceRoot = temporaryFolder.newFolder("source")
        seedSensitiveSourceTree(sourceRoot)
        val output = temporaryFolder.newFile("support.zip")
        val request = SupportBundleRequest(
            generatedAtMillis = 1_725_000_123_456L,
            metadata = SupportBundleMetadata(
                appVersionName = "2.2.0",
                appVersionCode = 101,
                flavor = "gkd",
                androidApi = 35,
            ),
            appSummary = SupportAppSummary(
                installSourceCategory = "app-store",
                appVersionName = "2.2.0",
                appVersionCode = 101,
                primaryAbi = "arm64-v8a",
                androidApi = 35,
                featureFlags = mapOf("usageGuard" to true),
            ),
            capabilitySummary = SupportCapabilitySummary(
                capabilities = mapOf("overlay" to true, "shizuku" to false),
            ),
            diagnosticEvents = (0 until 700).map { index ->
                SupportDiagnosticEvent(
                    occurredAtMillis = 1_725_000_000_111L + index,
                    event = DiagnosticEvent(
                        eventCode = DiagnosticEventCode.RUNTIME_FAILURE,
                        stage = DiagnosticStage.APP,
                        result = DiagnosticResult.FAILED,
                        count = index,
                    ),
                )
            },
            crashSummaries = listOf(
                SupportCrashSummary(
                    errorCode = "0123456789ab",
                    errorCategory = DiagnosticErrorCategory.UNKNOWN,
                    occurredAtMillis = 1_725_000_123_456L,
                    appFrames = listOf("li.songe.gkd.sdp.App.onCreate"),
                    count = 1,
                ),
            ),
        )

        SupportBundleBuilder().build(output, request)

        ZipFile(output).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(
                setOf(
                    "manifest.json",
                    "app-summary.json",
                    "capability-summary.json",
                    "diagnostic-events.jsonl",
                    "crash-summary.json",
                ),
                names,
            )

            val payloads = names.associateWith { name ->
                zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
            }
            val manifest = json.decodeFromString<SupportBundleManifest>(
                payloads.getValue("manifest.json").decodeToString(),
            )
            assertEquals(1, manifest.formatVersion)
            assertEquals("2.2.0", manifest.appVersionName)
            assertEquals(101, manifest.appVersionCode)
            assertEquals("gkd", manifest.flavor)
            assertEquals(35, manifest.androidApi)
            assertEquals(0L, manifest.generatedAtMinute % 60_000L)
            assertEquals(names - "manifest.json", manifest.files.map { it.name }.toSet())
            manifest.files.forEach { digest ->
                val bytes = payloads.getValue(digest.name)
                assertEquals(bytes.size.toLong(), digest.sizeBytes)
                assertEquals(sha256(bytes), digest.sha256)
            }

            val eventLines = payloads.getValue("diagnostic-events.jsonl")
                .decodeToString()
                .lineSequence()
                .filter(String::isNotBlank)
                .toList()
            assertEquals(500, eventLines.size)
            eventLines.forEach { line ->
                val event = json.decodeFromString<SupportDiagnosticEventEntry>(line)
                assertEquals(0L, event.occurredAtMinute % 60_000L)
            }

            val combinedText = payloads.values.joinToString("\n") { it.decodeToString() }
            assertFalse(combinedText.contains("secret-reason"))
            assertFalse(combinedText.contains("secret-url"))
            assertFalse(combinedText.contains("secret-node"))
            assertFalse(combinedText.contains("secret-cookie"))
            assertFalse(combinedText.contains(sourceRoot.absolutePath))
        }
    }

    private fun seedSensitiveSourceTree(root: File) {
        mapOf(
            "db/app.db" to "secret-reason",
            "store/settings.json" to "secret-url",
            "subscription/raw.json" to "secret-node",
            "snapshot/screen.png" to "secret-screenshot",
            "contacts/list.json" to "secret-contact",
            "log/raw.log" to "secret-cookie",
        ).forEach { (relativePath, content) ->
            root.resolve(relativePath).apply {
                parentFile?.mkdirs()
                writeText("$content ${root.absolutePath}")
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
