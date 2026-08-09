package li.songe.gkd.sdp.util

import android.net.Uri
import li.songe.gkd.sdp.app
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object UriUtils {
    class UriSizeLimitException : IOException("uri_size_limit_exceeded")

    fun copyUriToFile(
        uri: Uri,
        target: File,
        maxBytes: Long,
    ): Long {
        require(maxBytes > 0)
        target.parentFile?.mkdirs()
        val tempFile = target.parentFile?.resolve(".${target.name}.part")
            ?: File(".${target.name}.part")
        tempFile.delete()
        try {
            val copiedBytes = app.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedIOException("uri_copy_interrupted")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes = Math.addExact(totalBytes, count.toLong())
                        if (totalBytes > maxBytes) throw UriSizeLimitException()
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    totalBytes
                }
            } ?: throw FileNotFoundException("uri_input_unavailable")
            moveAtomically(tempFile, target)
            return copiedBytes
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

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
