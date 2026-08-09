package li.songe.gkd.sdp.util

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SafeArchiveInstrumentedTest {
    @Test
    fun extractionAcceptsSafeArchiveAndRejectsTraversalBeforeWriting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.cacheDir.resolve("safe-archive-test-${System.nanoTime()}")
        root.mkdirs()
        try {
            val safeArchive = root.resolve("safe.zip")
            ZipOutputStream(safeArchive.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("folder/file.txt"))
                output.write("content".encodeToByteArray())
                output.closeEntry()
            }
            val safeOutput = root.resolve("safe-output")
            ZipUtils.unzipFile(safeArchive, safeOutput)
            assertEquals("content", safeOutput.resolve("folder/file.txt").readText())

            val unsafeArchive = root.resolve("unsafe.zip")
            ZipOutputStream(unsafeArchive.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("../escape.txt"))
                output.write("escape".encodeToByteArray())
                output.closeEntry()
            }
            val unsafeOutput = root.resolve("unsafe-output")
            assertThrows(ZipUtils.ArchiveValidationException::class.java) {
                ZipUtils.unzipFile(unsafeArchive, unsafeOutput)
            }
            assertFalse(unsafeOutput.exists())
            assertFalse(requireNotNull(root.parentFile).resolve("escape.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun uriCopyStreamsWithSizeLimitAndRemovesPartialOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.cacheDir.resolve("safe-uri-test-${System.nanoTime()}")
        root.mkdirs()
        try {
            val source = root.resolve("source.bin").apply { writeText("12345") }
            val accepted = root.resolve("accepted.bin")
            assertEquals(
                5L,
                UriUtils.copyUriToFile(Uri.fromFile(source), accepted, maxBytes = 5),
            )
            assertEquals("12345", accepted.readText())

            val rejected = root.resolve("rejected.bin")
            assertThrows(UriUtils.UriSizeLimitException::class.java) {
                UriUtils.copyUriToFile(Uri.fromFile(source), rejected, maxBytes = 4)
            }
            assertFalse(rejected.exists())
            assertFalse(root.resolve(".rejected.bin.part").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
