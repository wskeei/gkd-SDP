package li.songe.gkd.sdp.diagnostics

import kotlinx.serialization.encodeToString
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.util.json
import li.songe.gkd.sdp.util.logFolder
import li.songe.gkd.sdp.util.privateStoreFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.sql.SQLException
import java.util.concurrent.Executors

object DiagnosticLogger {
    private const val INSTALLATION_SALT_BYTES = 32
    private const val MAX_FILE_BYTES = 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L
    private const val MAX_ROTATED_FILES = 2
    private const val MAX_EVENTS_PER_MINUTE = 20
    private const val EVENT_WINDOW_MILLIS = 60_000L
    private const val SALT_FILENAME = "diagnostic-salt.bin"
    private const val ACTIVE_FILENAME = "diagnostic-events.jsonl"

    private val executor = Executors.newSingleThreadExecutor()
    private val limiter = DiagnosticRateLimiter(
        maxEvents = MAX_EVENTS_PER_MINUTE,
        windowMillis = EVENT_WINDOW_MILLIS,
    )
    private val installationSalt: ByteArray by lazy { loadOrCreateInstallationSalt() }
    private val fileStore: DiagnosticEventFileStore by lazy {
        DiagnosticEventFileStore(
            directory = logFolder,
            activeFilename = ACTIVE_FILENAME,
            maxFileBytes = MAX_FILE_BYTES,
            maxTotalBytes = MAX_TOTAL_BYTES,
            maxRotatedFiles = MAX_ROTATED_FILES,
        )
    }

    fun record(event: DiagnosticEvent) {
        val isDebuggable = runCatching { META.debuggable }.getOrDefault(false)
        if (!isDebuggable && event.eventCode.severity == DiagnosticSeverity.DEBUG) return
        val nowMillis = System.currentTimeMillis()
        if (!limiter.tryAcquire(event, nowMillis)) return
        executor.execute {
            runCatching { append(event) }
        }
    }

    fun recordLegacy(
        arguments: List<Any?>,
        location: String,
        fileName: String,
    ) {
        val throwable = arguments.firstOrNull { it is Throwable } as? Throwable
        record(
            DiagnosticEvent(
                eventCode = if (throwable == null) {
                    DiagnosticEventCode.LEGACY_CALL
                } else {
                    DiagnosticEventCode.LEGACY_FAILURE
                },
                stage = DiagnosticStage.LEGACY,
                result = if (throwable == null) {
                    DiagnosticResult.OBSERVED
                } else {
                    DiagnosticResult.FAILED
                },
                entityHash = stableEntityHash("$fileName:$location"),
                count = arguments.size,
                errorCategory = throwable?.let(::errorCategory),
            ),
        )
    }

    fun stableEntityHash(value: String): String =
        RedactionPolicy.stableIdHash(value, installationSalt)

    fun errorCode(error: Throwable): String {
        val appFrames = applicationFrames(error)
        val signature = buildString {
            append(error::class.java.name)
            appFrames.forEach { append(':').append(it) }
        }
        return stableEntityHash(signature)
    }

    fun userMessage(error: Throwable): String =
        "操作失败（错误码：${errorCode(error)}）"

    fun errorCategory(error: Throwable): DiagnosticErrorCategory = when (error) {
        is SecurityException -> DiagnosticErrorCategory.SECURITY
        is IllegalArgumentException -> DiagnosticErrorCategory.INVALID_INPUT
        is IllegalStateException -> DiagnosticErrorCategory.INVALID_STATE
        is SocketException -> DiagnosticErrorCategory.NETWORK
        is SQLException -> DiagnosticErrorCategory.DATABASE
        is IOException -> DiagnosticErrorCategory.IO
        else -> DiagnosticErrorCategory.UNKNOWN
    }

    fun applicationFrames(error: Throwable): List<String> = error.stackTrace
        .asSequence()
        .filter { it.className.startsWith("li.songe.gkd.sdp.") }
        .map { "${it.className}.${it.methodName}" }
        .distinct()
        .take(12)
        .toList()

    private fun append(event: DiagnosticEvent) {
        val line = json.encodeToString(event) + "\n"
        fileStore.append(line.toByteArray(Charsets.UTF_8))
    }

    private fun loadOrCreateInstallationSalt(): ByteArray {
        val saltFile = privateStoreFolder.resolve(SALT_FILENAME)
        saltFile.takeIf(File::isFile)?.readBytes()?.takeIf {
            it.size == INSTALLATION_SALT_BYTES
        }?.let { return it }

        val salt = ByteArray(INSTALLATION_SALT_BYTES).also(SecureRandom()::nextBytes)
        val tempFile = privateStoreFolder.resolve("$SALT_FILENAME.tmp")
        tempFile.outputStream().use { output ->
            output.write(salt)
            output.flush()
        }
        runCatching {
            Files.move(
                tempFile.toPath(),
                saltFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            tempFile.copyTo(saltFile, overwrite = true)
            tempFile.delete()
        }
        return salt
    }
}

internal class DiagnosticEventFileStore(
    private val directory: File,
    private val activeFilename: String,
    private val maxFileBytes: Long,
    private val maxTotalBytes: Long,
    private val maxRotatedFiles: Int,
) {
    init {
        require(maxFileBytes > 0)
        require(maxTotalBytes >= maxFileBytes)
        require(maxRotatedFiles >= 0)
    }

    @Synchronized
    fun append(line: ByteArray) {
        if (line.isEmpty() || line.size > maxTotalBytes) return
        directory.mkdirs()
        val activeFile = directory.resolve(activeFilename)
        if (activeFile.length() + line.size > maxFileBytes) {
            rotate(activeFile)
        }
        FileOutputStream(activeFile, true).use { output ->
            output.write(line)
            output.flush()
        }
        trimToTotalLimit(activeFile)
    }

    private fun rotate(activeFile: File) {
        if (maxRotatedFiles == 0) {
            activeFile.delete()
            return
        }
        rotatedFile(maxRotatedFiles).delete()
        for (index in maxRotatedFiles - 1 downTo 1) {
            val source = rotatedFile(index)
            if (source.exists()) {
                Files.move(
                    source.toPath(),
                    rotatedFile(index + 1).toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
        if (activeFile.exists()) {
            Files.move(
                activeFile.toPath(),
                rotatedFile(1).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun trimToTotalLimit(activeFile: File) {
        var totalBytes = activeFile.length() +
            (1..maxRotatedFiles).sumOf { rotatedFile(it).length() }
        for (index in maxRotatedFiles downTo 1) {
            if (totalBytes <= maxTotalBytes) break
            val oldestFile = rotatedFile(index)
            val removedBytes = oldestFile.length()
            if (oldestFile.delete()) totalBytes -= removedBytes
        }
    }

    private fun rotatedFile(index: Int): File {
        val extensionIndex = activeFilename.lastIndexOf('.')
        val filename = if (extensionIndex > 0) {
            activeFilename.substring(0, extensionIndex) +
                ".$index" +
                activeFilename.substring(extensionIndex)
        } else {
            "$activeFilename.$index"
        }
        return directory.resolve(filename)
    }
}
