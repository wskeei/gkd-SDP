package li.songe.gkd.sdp.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtils {
    private const val BUFFER_LEN = 8192
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
    private const val END_OF_CENTRAL_DIRECTORY_MIN_BYTES = 22
    private const val MAX_ZIP_COMMENT_BYTES = 65_535
    private const val UNIX_HOST_SYSTEM = 3
    private const val UNIX_FILE_TYPE_MASK = 0xF000
    private const val UNIX_SYMBOLIC_LINK = 0xA000

    data class ArchiveLimits(
        val maxArchiveBytes: Long = 64L * 1024L * 1024L,
        val maxEntries: Int = 2_000,
        val maxEntryUncompressedBytes: Long = 32L * 1024L * 1024L,
        val maxTotalUncompressedBytes: Long = 128L * 1024L * 1024L,
    ) {
        init {
            require(maxArchiveBytes > 0)
            require(maxEntries > 0)
            require(maxEntryUncompressedBytes > 0)
            require(maxTotalUncompressedBytes >= maxEntryUncompressedBytes)
        }
    }

    enum class ArchiveError {
        INVALID_NAME,
        DUPLICATE_PATH,
        SYMBOLIC_LINK,
        RESOURCE_LIMIT,
        MALFORMED_ARCHIVE,
        DESTINATION_CONFLICT,
    }

    class ArchiveValidationException(
        val error: ArchiveError,
        cause: Throwable? = null,
    ) : IOException("archive_${error.name.lowercase()}", cause)

    data class ValidatedArchiveEntry(
        val originalName: String,
        val normalizedName: String,
        val isDirectory: Boolean,
        val uncompressedBytes: Long,
    )

    fun validateEntryName(name: String): String {
        if (
            name.isBlank() ||
            name.indexOf('\u0000') >= 0 ||
            '\\' in name ||
            name.startsWith('/') ||
            name.startsWith("//") ||
            WINDOWS_DRIVE_PREFIX.containsMatchIn(name)
        ) {
            throw ArchiveValidationException(ArchiveError.INVALID_NAME)
        }
        val withoutDirectorySuffix = name.removeSuffix("/")
        val segments = withoutDirectorySuffix.split('/')
        if (
            withoutDirectorySuffix.isBlank() ||
            segments.any { it.isBlank() || it == "." || it == ".." }
        ) {
            throw ArchiveValidationException(ArchiveError.INVALID_NAME)
        }
        return segments.joinToString("/")
    }

    fun validateArchive(
        zipFile: File,
        limits: ArchiveLimits = ArchiveLimits(),
    ): List<ValidatedArchiveEntry> {
        if (!zipFile.isFile || zipFile.length() > limits.maxArchiveBytes) {
            throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT)
        }
        return try {
            val centralEntries = readCentralDirectory(zipFile)
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > limits.maxEntries) {
                    throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT)
                }
                if (entries.size != centralEntries.size) {
                    throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE)
                }
                val seenPaths = mutableSetOf<String>()
                var totalBytes = 0L
                val validatedEntries = entries.mapIndexed { index, entry ->
                    val centralEntry = centralEntries[index]
                    if (centralEntry.encrypted) {
                        throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE)
                    }
                    if (centralEntry.symbolicLink) {
                        throw ArchiveValidationException(ArchiveError.SYMBOLIC_LINK)
                    }
                    val normalizedName = validateEntryName(entry.name)
                    if (!seenPaths.add(normalizedName)) {
                        throw ArchiveValidationException(ArchiveError.DUPLICATE_PATH)
                    }
                    val size = entry.size
                    if (
                        size < 0 ||
                        size > limits.maxEntryUncompressedBytes ||
                        (entry.isDirectory && size != 0L)
                    ) {
                        throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT)
                    }
                    totalBytes = try {
                        Math.addExact(totalBytes, size)
                    } catch (error: ArithmeticException) {
                        throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT, error)
                    }
                    if (totalBytes > limits.maxTotalUncompressedBytes) {
                        throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT)
                    }
                    ValidatedArchiveEntry(
                        originalName = entry.name,
                        normalizedName = normalizedName,
                        isDirectory = entry.isDirectory,
                        uncompressedBytes = size,
                    )
                }
                rejectFileParentCollisions(validatedEntries)
                validatedEntries
            }
        } catch (error: ArchiveValidationException) {
            throw error
        } catch (error: IOException) {
            throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE, error)
        }
    }

    fun unzipFile(
        zipFile: File,
        destDir: File,
        limits: ArchiveLimits = ArchiveLimits(),
        tempDirectoryFactory: () -> File = ::createGkdTempDir,
    ) {
        val validatedEntries = validateArchive(zipFile, limits)
        if (destDir.exists()) {
            throw ArchiveValidationException(ArchiveError.DESTINATION_CONFLICT)
        }
        val destinationRoot = destDir.canonicalFile
        validatedEntries.forEach { entry ->
            ensureChildPath(destinationRoot, destinationRoot.resolve(entry.normalizedName))
        }

        val tempRoot = tempDirectoryFactory()
        val extractionRoot = tempRoot.resolve("archive").apply {
            if (!mkdirs() && !isDirectory) {
                tempRoot.deleteRecursively()
                throw IOException("archive_staging_unavailable")
            }
        }.canonicalFile
        try {
            val budget = ExtractionBudget(limits.maxTotalUncompressedBytes)
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                validatedEntries.forEachIndexed { index, validated ->
                    checkInterrupted()
                    val entry = entries[index]
                    val output = extractionRoot.resolve(validated.normalizedName).canonicalFile
                    ensureChildPath(extractionRoot, output)
                    if (validated.isDirectory) {
                        if (!output.mkdirs() && !output.isDirectory) {
                            throw IOException("archive_directory_unavailable")
                        }
                    } else {
                        val parent = output.parentFile
                        if (parent != null && !parent.mkdirs() && !parent.isDirectory) {
                            throw IOException("archive_directory_unavailable")
                        }
                        val copiedBytes = zip.getInputStream(entry).use { rawInput ->
                            BoundedArchiveInputStream(
                                delegate = rawInput,
                                maxEntryBytes = limits.maxEntryUncompressedBytes,
                                budget = budget,
                            ).use { input ->
                                FileOutputStream(output).use { outputStream ->
                                    input.copyTo(outputStream, BUFFER_LEN)
                                }
                            }
                        }
                        if (copiedBytes != validated.uncompressedBytes) {
                            throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE)
                        }
                    }
                }
            }
            checkInterrupted()
            destDir.parentFile?.mkdirs()
            try {
                Files.move(
                    extractionRoot.toPath(),
                    destDir.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(extractionRoot.toPath(), destDir.toPath())
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun zipFile(
        srcFile: File,
        rawRootPath: String,
        output: ZipOutputStream,
        comment: String?,
    ): Boolean {
        val rootPath = rawRootPath +
            (if (rawRootPath.isBlank()) "" else File.separator) +
            srcFile.name
        if (srcFile.isDirectory) {
            val fileList = srcFile.listFiles()
            if (fileList.isNullOrEmpty()) {
                output.putNextEntry(ZipEntry("$rootPath/").apply { this.comment = comment })
                output.closeEntry()
            } else {
                for (file in fileList) {
                    if (!zipFile(file, rootPath, output, comment)) return false
                }
            }
        } else {
            BufferedInputStream(FileInputStream(srcFile)).use { input ->
                output.putNextEntry(ZipEntry(rootPath).apply { this.comment = comment })
                input.copyTo(output, BUFFER_LEN)
                output.closeEntry()
            }
        }
        return true
    }

    fun zipFiles(srcFiles: Collection<File>, zipFile: File): Boolean {
        ZipOutputStream(FileOutputStream(zipFile)).use { output ->
            for (srcFile in srcFiles) {
                if (!zipFile(srcFile, "", output, null)) return false
            }
        }
        return true
    }

    private fun rejectFileParentCollisions(entries: List<ValidatedArchiveEntry>) {
        val filePaths = entries.filterNot(ValidatedArchiveEntry::isDirectory)
            .mapTo(mutableSetOf(), ValidatedArchiveEntry::normalizedName)
        entries.forEach { entry ->
            var parent = entry.normalizedName.substringBeforeLast('/', missingDelimiterValue = "")
            while (parent.isNotEmpty()) {
                if (parent in filePaths) {
                    throw ArchiveValidationException(ArchiveError.DUPLICATE_PATH)
                }
                parent = parent.substringBeforeLast('/', missingDelimiterValue = "")
            }
        }
    }

    private fun ensureChildPath(root: File, output: File) {
        val rootPath = root.canonicalPath
        val outputPath = output.canonicalPath
        if (!outputPath.startsWith(rootPath + File.separator)) {
            throw ArchiveValidationException(ArchiveError.INVALID_NAME)
        }
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("archive_interrupted")
        }
    }

    private data class CentralDirectoryEntry(
        val encrypted: Boolean,
        val symbolicLink: Boolean,
    )

    private fun readCentralDirectory(file: File): List<CentralDirectoryEntry> {
        RandomAccessFile(file, "r").use { input ->
            val searchLength = minOf(
                input.length(),
                (END_OF_CENTRAL_DIRECTORY_MIN_BYTES + MAX_ZIP_COMMENT_BYTES).toLong(),
            ).toInt()
            if (searchLength < END_OF_CENTRAL_DIRECTORY_MIN_BYTES) {
                throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE)
            }
            val searchOffset = input.length() - searchLength
            val tail = ByteArray(searchLength)
            input.seek(searchOffset)
            input.readFully(tail)
            val eocdOffsetInTail = (tail.size - END_OF_CENTRAL_DIRECTORY_MIN_BYTES downTo 0)
                .firstOrNull { offset ->
                    unsignedInt(tail, offset) == END_OF_CENTRAL_DIRECTORY_SIGNATURE &&
                        offset + END_OF_CENTRAL_DIRECTORY_MIN_BYTES +
                        unsignedShort(tail, offset + 20) == tail.size
                } ?: throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE)
            val diskNumber = unsignedShort(tail, eocdOffsetInTail + 4)
            val centralDisk = unsignedShort(tail, eocdOffsetInTail + 6)
            val entriesOnDisk = unsignedShort(tail, eocdOffsetInTail + 8)
            val entryCount = unsignedShort(tail, eocdOffsetInTail + 10)
            val centralSize = unsignedInt(tail, eocdOffsetInTail + 12)
            val centralOffset = unsignedInt(tail, eocdOffsetInTail + 16)
            if (
                diskNumber != 0 ||
                centralDisk != 0 ||
                entriesOnDisk != entryCount ||
                entryCount == 0xffff ||
                centralSize == 0xffffffffL ||
                centralOffset == 0xffffffffL ||
                centralOffset + centralSize > searchOffset + eocdOffsetInTail
            ) {
                throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE)
            }

            input.seek(centralOffset)
            return List(entryCount) {
                val header = ByteArray(46)
                input.readFully(header)
                if (unsignedInt(header, 0) != CENTRAL_DIRECTORY_SIGNATURE) {
                    throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE)
                }
                val hostSystem = header[5].toInt() and 0xff
                val flags = unsignedShort(header, 8)
                val externalAttributes = unsignedInt(header, 38)
                val unixMode = ((externalAttributes ushr 16) and 0xffff).toInt()
                val nameLength = unsignedShort(header, 28)
                val extraLength = unsignedShort(header, 30)
                val commentLength = unsignedShort(header, 32)
                val nextOffset = input.filePointer + nameLength + extraLength + commentLength
                if (nextOffset > input.length()) {
                    throw ArchiveValidationException(ArchiveError.MALFORMED_ARCHIVE)
                }
                input.seek(nextOffset)
                CentralDirectoryEntry(
                    encrypted = flags and 1 != 0,
                    symbolicLink = hostSystem == UNIX_HOST_SYSTEM &&
                        unixMode and UNIX_FILE_TYPE_MASK == UNIX_SYMBOLIC_LINK,
                )
            }
        }
    }

    private class ExtractionBudget(private val maxBytes: Long) {
        private var bytesRead = 0L

        fun add(byteCount: Long) {
            bytesRead = try {
                Math.addExact(bytesRead, byteCount)
            } catch (error: ArithmeticException) {
                throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT, error)
            }
            if (bytesRead > maxBytes) {
                throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT)
            }
        }
    }

    private class BoundedArchiveInputStream(
        delegate: InputStream,
        private val maxEntryBytes: Long,
        private val budget: ExtractionBudget,
    ) : FilterInputStream(delegate) {
        private var entryBytesRead = 0L

        override fun read(): Int {
            checkInterrupted()
            return super.read().also { value ->
                if (value >= 0) count(1)
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkInterrupted()
            return super.read(buffer, offset, length).also { count ->
                if (count > 0) count(count.toLong())
            }
        }

        private fun count(bytes: Long) {
            entryBytesRead = try {
                Math.addExact(entryBytesRead, bytes)
            } catch (error: ArithmeticException) {
                throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT, error)
            }
            if (entryBytesRead > maxEntryBytes) {
                throw ArchiveValidationException(ArchiveError.RESOURCE_LIMIT)
            }
            budget.add(bytes)
        }
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        unsignedShort(bytes, offset).toLong() or
            (unsignedShort(bytes, offset + 2).toLong() shl 16)

    private val WINDOWS_DRIVE_PREFIX = Regex("^[A-Za-z]:")
}
