package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SafeArchivePolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `entry names reject traversal absolute and ambiguous paths`() {
        listOf(
            "../escape",
            "safe/../../escape",
            "/absolute/path",
            "C:/windows/path",
            "safe\\..\\escape",
            "name\u0000.txt",
            "",
            "./same",
            "safe//same",
        ).forEach { name ->
            assertThrows(name, ZipUtils.ArchiveValidationException::class.java) {
                ZipUtils.validateEntryName(name)
            }
        }
        assertEquals("safe/entry.txt", ZipUtils.validateEntryName("safe/entry.txt"))
        assertEquals("safe/folder", ZipUtils.validateEntryName("safe/folder/"))
    }

    @Test
    fun `archive rejects duplicate normalized paths and symbolic links`() {
        val duplicate = createZip(
            "folder" to "file".encodeToByteArray(),
            "folder/" to ByteArray(0),
        )
        assertThrows(ZipUtils.ArchiveValidationException::class.java) {
            ZipUtils.validateArchive(duplicate)
        }

        val symbolicLink = createZip("link" to "target".encodeToByteArray())
        markFirstEntryAsUnixSymlink(symbolicLink)
        assertThrows(ZipUtils.ArchiveValidationException::class.java) {
            ZipUtils.validateArchive(symbolicLink)
        }
    }

    @Test
    fun `archive limits cover compressed file entries and expanded sizes`() {
        val defaults = ZipUtils.ArchiveLimits()
        assertEquals(64L * 1024L * 1024L, defaults.maxArchiveBytes)
        assertEquals(2_000, defaults.maxEntries)
        assertEquals(32L * 1024L * 1024L, defaults.maxEntryUncompressedBytes)
        assertEquals(128L * 1024L * 1024L, defaults.maxTotalUncompressedBytes)

        val oversizedArchive = temporaryFolder.newFile("oversized.zip")
        RandomAccessFile(oversizedArchive, "rw").use {
            it.setLength(defaults.maxArchiveBytes + 1)
        }
        assertThrows(ZipUtils.ArchiveValidationException::class.java) {
            ZipUtils.validateArchive(oversizedArchive)
        }

        val tooManyEntries = createZip(
            "1" to byteArrayOf(1),
            "2" to byteArrayOf(2),
            "3" to byteArrayOf(3),
        )
        assertThrows(ZipUtils.ArchiveValidationException::class.java) {
            ZipUtils.validateArchive(tooManyEntries, defaults.copy(maxEntries = 2))
        }

        val oversizedEntry = createZip("large" to ByteArray(5))
        assertThrows(ZipUtils.ArchiveValidationException::class.java) {
            ZipUtils.validateArchive(
                oversizedEntry,
                defaults.copy(maxEntryUncompressedBytes = 4),
            )
        }

        val oversizedTotal = createZip(
            "one" to ByteArray(4),
            "two" to ByteArray(4),
        )
        assertThrows(ZipUtils.ArchiveValidationException::class.java) {
            ZipUtils.validateArchive(
                oversizedTotal,
                defaults.copy(
                    maxEntryUncompressedBytes = 4,
                    maxTotalUncompressedBytes = 7,
                ),
            )
        }
    }

    @Test
    fun `all entries are validated before writes and interrupted extraction is cleaned`() {
        val invalidArchive = createZip(
            "valid.txt" to "valid".encodeToByteArray(),
            "../escape.txt" to "escape".encodeToByteArray(),
        )
        val invalidDestination = temporaryFolder.root.resolve("invalid-output")
        var invalidTempCreated = false
        assertThrows(ZipUtils.ArchiveValidationException::class.java) {
            ZipUtils.unzipFile(
                invalidArchive,
                invalidDestination,
                tempDirectoryFactory = {
                    invalidTempCreated = true
                    temporaryFolder.newFolder("invalid-staging")
                },
            )
        }
        assertFalse(invalidTempCreated)
        assertFalse(invalidDestination.exists())

        val validArchive = createZip("safe/file.txt" to "content".encodeToByteArray())
        val interruptedDestination = temporaryFolder.root.resolve("interrupted-output")
        lateinit var interruptedStaging: File
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedIOException::class.java) {
                ZipUtils.unzipFile(
                    validArchive,
                    interruptedDestination,
                    tempDirectoryFactory = {
                        temporaryFolder.newFolder("interrupted-staging").also {
                            interruptedStaging = it
                        }
                    },
                )
            }
        } finally {
            Thread.interrupted()
        }
        assertFalse(interruptedDestination.exists())
        assertFalse(interruptedStaging.exists())
    }

    @Test
    fun `validated archive extracts beneath destination`() {
        val archive = createZip(
            "safe/" to ByteArray(0),
            "safe/file.txt" to "content".encodeToByteArray(),
        )
        val destination = temporaryFolder.root.resolve("output")

        ZipUtils.unzipFile(
            archive,
            destination,
            tempDirectoryFactory = { temporaryFolder.newFolder("valid-staging") },
        )

        assertEquals("content", destination.resolve("safe/file.txt").readText())
        assertTrue(destination.canonicalPath.startsWith(temporaryFolder.root.canonicalPath))
    }

    private fun createZip(vararg entries: Pair<String, ByteArray>): File {
        val file = temporaryFolder.newFile("archive-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }

    private fun markFirstEntryAsUnixSymlink(file: File) {
        val bytes = file.readBytes()
        val signature = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
        val offset = bytes.indices.first { index ->
            index + signature.size <= bytes.size &&
                signature.indices.all { signatureIndex ->
                    bytes[index + signatureIndex] == signature[signatureIndex]
                }
        }
        bytes[offset + 5] = 3
        bytes[offset + 40] = 0xff.toByte()
        bytes[offset + 41] = 0xa1.toByte()
        file.writeBytes(bytes)
    }
}
