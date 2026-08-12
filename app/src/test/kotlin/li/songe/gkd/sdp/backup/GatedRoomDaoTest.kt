package li.songe.gkd.sdp.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal interface GatedRoomDaoTestContract {
    suspend fun immediate(value: String): String
    suspend fun delayed(value: String): String
    suspend fun failing(): String

    suspend fun transactionStep(value: String): String

    suspend fun transactionLike(value: String): String {
        val first = transactionStep(value)
        val second = transactionStep("-second")
        return first + second
    }
}

private class GatedRoomDaoTestDelegate : GatedRoomDaoTestContract {
    val delayedValue = CompletableDeferred<String>()
    val transactionResume = CompletableDeferred<Unit>()

    override suspend fun immediate(value: String): String = value

    override suspend fun delayed(value: String): String = delayedValue.await() + value

    override suspend fun failing(): String = error("synthetic dao failure")

    override suspend fun transactionStep(value: String): String {
        transactionResume.await()
        return value
    }
}

class GatedRoomDaoTest {
    @Test
    fun `proxy requires an interface token`() {
        val result = runCatching {
            gateRoomDao(String::class.java, "delegate")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `proxy obeys the suspend JVM ABI for sync async and failure paths`() = runBlocking {
        val delegate = GatedRoomDaoTestDelegate()
        val dao: GatedRoomDaoTestContract = gateRoomDao(GatedRoomDaoTestContract::class.java, delegate)
        val immediate = GatedRoomDaoTestContract::class.java.getMethod(
            "immediate",
            String::class.java,
            Continuation::class.java,
        )
        val delayed = GatedRoomDaoTestContract::class.java.getMethod(
            "delayed",
            String::class.java,
            Continuation::class.java,
        )
        val failing = GatedRoomDaoTestContract::class.java.getMethod(
            "failing",
            Continuation::class.java,
        )

        val syncCompletion = RecordingContinuation()
        assertEquals("sync", immediate.invoke(dao, "sync", syncCompletion))
        assertEquals(0, syncCompletion.resumptionCount)

        val failureCompletion = RecordingContinuation()
        val failure = runCatching { failing.invoke(dao, failureCompletion) }.exceptionOrNull()
        assertTrue(failure is InvocationTargetException)
        assertEquals("synthetic dao failure", failure?.cause?.message)
        assertEquals(0, failureCompletion.resumptionCount)

        val asyncCompletion = RecordingContinuation()
        assertSame(
            COROUTINE_SUSPENDED,
            delayed.invoke(dao, "-suffix", asyncCompletion),
        )
        assertEquals(0, asyncCompletion.resumptionCount)
        delegate.delayedValue.complete("async")
        yield()
        assertEquals(1, asyncCompletion.resumptionCount)
        assertEquals("async-suffix", asyncCompletion.result?.getOrNull())
    }

    @Test
    fun `proxy preserves synchronous and suspending dao results`() = runBlocking {
        val delegate = GatedRoomDaoTestDelegate()
        val dao: GatedRoomDaoTestContract = gateRoomDao(GatedRoomDaoTestContract::class.java, delegate)

        assertEquals("sync", dao.immediate("sync"))
        val delayed = async { dao.delayed("-suffix") }
        yield()
        assertFalse(delayed.isCompleted)
        delegate.delayedValue.complete("async")
        assertEquals("async-suffix", delayed.await())
    }

    @Test
    fun `proxy propagates delegate failures`() = runBlocking {
        val dao: GatedRoomDaoTestContract = gateRoomDao(
            GatedRoomDaoTestContract::class.java,
            GatedRoomDaoTestDelegate(),
        )

        val result = runCatching { dao.failing() }

        assertTrue(result.isFailure)
        assertEquals("synthetic dao failure", result.exceptionOrNull()?.message)
    }

    @Test
    fun `proxy waits for and can cancel while mutation gate is held`() = runBlocking {
        val gateEntered = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        val dao: GatedRoomDaoTestContract = gateRoomDao(
            GatedRoomDaoTestContract::class.java,
            GatedRoomDaoTestDelegate(),
        )
        val holder = async {
            withBackupDataMutationGate {
                gateEntered.complete(Unit)
                releaseGate.await()
            }
        }
        gateEntered.await()

        val queued = async { dao.immediate("queued") }
        yield()
        assertFalse(queued.isCompleted)
        queued.cancelAndJoin()

        releaseGate.complete(Unit)
        holder.await()
        assertTrue(queued.isCancelled)
    }

    @Test
    fun `transaction-like default method holds gate across its suspension`() = runBlocking {
        val delegate = GatedRoomDaoTestDelegate()
        val dao: GatedRoomDaoTestContract = gateRoomDao(GatedRoomDaoTestContract::class.java, delegate)
        val transaction = async { dao.transactionLike("first") }
        yield()

        val queuedMutation = async {
            withBackupDataMutationGate { "queued" }
        }
        yield()
        assertFalse(queuedMutation.isCompleted)

        delegate.transactionResume.complete(Unit)
        assertEquals("first-second", transaction.await())
        assertEquals("queued", queuedMutation.await())
    }

    private class RecordingContinuation : Continuation<Any?> {
        override val context = EmptyCoroutineContext
        var resumptionCount = 0
        var result: Result<Any?>? = null

        override fun resumeWith(result: Result<Any?>) {
            resumptionCount += 1
            this.result = result
        }
    }
}
