package li.songe.gkd.sdp.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedResponseReaderTest {
    @Test
    fun `fixed response larger than limit is rejected before reading`() {
        assertFalse(isAllowedDeclaredLength(declaredLength = 4_097, maxBytes = 4_096))
        assertTrue(isAllowedDeclaredLength(declaredLength = 4_096, maxBytes = 4_096))
        assertTrue(isAllowedDeclaredLength(declaredLength = null, maxBytes = 4_096))
    }

    @Test
    fun `chunked response cannot grow beyond limit`() {
        val accumulator = BoundedByteAccumulator(maxBytes = 5)

        assertTrue(accumulator.append(byteArrayOf(1, 2, 3), 3))
        assertTrue(accumulator.append(byteArrayOf(4, 5), 2))
        assertFalse(accumulator.append(byteArrayOf(6), 1))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), accumulator.toByteArray())
    }
}
