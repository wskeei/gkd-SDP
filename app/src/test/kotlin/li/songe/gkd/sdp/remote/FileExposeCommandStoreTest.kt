package li.songe.gkd.sdp.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileExposeCommandStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missingAndEmptyFilesRoundTripAsEmptyLists() = runBlocking {
        val file = temporaryFolder.newFile("commands.json")
        val store = FileExposeCommandStore(file)

        assertEquals(emptyList<ExposeCommandRecord>(), store.load())
        store.save(emptyList())
        assertEquals(emptyList<ExposeCommandRecord>(), store.load())
    }

    @Test
    fun savedRecordsAreLoadedBackAndEmptySaveClearsFile() = runBlocking {
        val file = temporaryFolder.newFile("commands.json")
        val store = FileExposeCommandStore(file)
        val record = ExposeCommandRecord(
            tokenHash = "hash",
            action = ExposeAction.SYNC_FIX,
            channel = ExposeChannel.INTERNAL,
            expiresAtMillis = 123L,
        )

        store.save(listOf(record))
        assertEquals(listOf(record), store.load())

        store.save(emptyList())
        assertEquals(emptyList<ExposeCommandRecord>(), store.load())
        assertFalse(file.exists())
    }
}
