package li.songe.gkd.sdp.diagnostics

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SupportBundleBuilder(
    private val codec: Json = Json {
        encodeDefaults = true
        prettyPrint = true
    },
) {
    private val lineCodec = Json(from = codec) { prettyPrint = false }

    companion object {
        const val FORMAT_VERSION = 1
        const val MAX_DIAGNOSTIC_EVENTS = 500
        private const val MINUTE_MILLIS = 60_000L
    }

    fun build(outputFile: File, request: SupportBundleRequest): File {
        val generatedAtMinute = request.generatedAtMillis.toMinutePrecision()
        val payloads = linkedMapOf(
            "app-summary.json" to codec.encodeToString(request.appSummary).encodeToByteArray(),
            "capability-summary.json" to codec.encodeToString(request.capabilitySummary)
                .encodeToByteArray(),
            "diagnostic-events.jsonl" to diagnosticEventsPayload(request.diagnosticEvents),
            "crash-summary.json" to crashSummaryPayload(request.crashSummaries),
        )
        val manifest = SupportBundleManifest(
            formatVersion = FORMAT_VERSION,
            appVersionName = request.metadata.appVersionName,
            appVersionCode = request.metadata.appVersionCode,
            flavor = request.metadata.flavor,
            androidApi = request.metadata.androidApi,
            generatedAtMinute = generatedAtMinute,
            files = payloads.map { (name, bytes) ->
                SupportBundleFileDigest(
                    name = name,
                    sha256 = sha256(bytes),
                    sizeBytes = bytes.size.toLong(),
                )
            },
        )
        val entries = linkedMapOf(
            "manifest.json" to codec.encodeToString(manifest).encodeToByteArray(),
        ).apply { putAll(payloads) }

        outputFile.parentFile?.mkdirs()
        val tempFile = outputFile.resolveSibling(".${outputFile.name}.part")
        tempFile.delete()
        try {
            ZipOutputStream(tempFile.outputStream().buffered()).use { zipOutput ->
                entries.forEach { (name, bytes) ->
                    zipOutput.putNextEntry(ZipEntry(name).apply { time = generatedAtMinute })
                    zipOutput.write(bytes)
                    zipOutput.closeEntry()
                }
            }
            moveAtomically(tempFile, outputFile)
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
        return outputFile
    }

    private fun diagnosticEventsPayload(events: List<SupportDiagnosticEvent>): ByteArray {
        if (events.isEmpty()) return ByteArray(0)
        return events.takeLast(MAX_DIAGNOSTIC_EVENTS).joinToString(
            separator = "\n",
            postfix = "\n",
        ) { sample ->
            lineCodec.encodeToString(
                SupportDiagnosticEventEntry(
                    occurredAtMinute = sample.occurredAtMillis.toMinutePrecision(),
                    event = sample.event,
                ),
            )
        }.encodeToByteArray()
    }

    private fun crashSummaryPayload(summaries: List<SupportCrashSummary>): ByteArray =
        codec.encodeToString(
            summaries.map { summary ->
                SupportCrashSummaryEntry(
                    errorCode = summary.errorCode,
                    errorCategory = summary.errorCategory,
                    occurredAtMinute = summary.occurredAtMillis.toMinutePrecision(),
                    appFrames = summary.appFrames,
                    count = summary.count,
                )
            },
        ).encodeToByteArray()

    private fun Long.toMinutePrecision(): Long = this - mod(MINUTE_MILLIS)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

private fun File.resolveSibling(name: String): File =
    parentFile?.resolve(name) ?: File(name)
