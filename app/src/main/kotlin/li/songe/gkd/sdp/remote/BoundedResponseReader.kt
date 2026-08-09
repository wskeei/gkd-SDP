package li.songe.gkd.sdp.remote

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream

internal fun isAllowedDeclaredLength(declaredLength: Long?, maxBytes: Long): Boolean =
    declaredLength == null || declaredLength in 0..maxBytes

internal class BoundedByteAccumulator(private val maxBytes: Int) {
    private val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))

    fun append(buffer: ByteArray, count: Int): Boolean {
        require(count in 0..buffer.size)
        if (count > maxBytes - output.size()) return false
        output.write(buffer, 0, count)
        return true
    }

    fun toByteArray(): ByteArray = output.toByteArray()
}

internal suspend fun HttpResponse.readBoundedBody(maxBytes: Int): ByteArray? {
    val declaredHeader = headers[HttpHeaders.ContentLength]
    val declaredLength = declaredHeader?.toLongOrNull()
    if (declaredHeader != null && declaredLength == null) return null
    if (!isAllowedDeclaredLength(declaredLength, maxBytes.toLong())) return null

    val accumulator = BoundedByteAccumulator(maxBytes)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val channel = bodyAsChannel()
    return try {
        while (!channel.isClosedForRead) {
            val count = channel.readAvailable(buffer, 0, buffer.size)
            if (count == -1) break
            if (count == 0) continue
            if (!accumulator.append(buffer, count)) return null
        }
        accumulator.toByteArray()
    } finally {
        channel.cancel(null)
    }
}
