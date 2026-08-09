package li.songe.gkd.sdp.util

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionMutationBoundaryContractTest {
    @Test
    fun subscriptionMutationsAreSuspendTransactionalAndAwaitedByEntryPoints() {
        val repository = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/util/SubscriptionMutationRepository.kt",
        ).readText()
        val state = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/util/SubsState.kt",
        ).readText()
        val mainViewModel = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt",
        ).readText()
        val httpService = sourceFile(
            "app/src/main/kotlin/li/songe/gkd/sdp/service/HttpService.kt",
        ).readText()

        assertTrue(repository.contains("BackupDataMutationBarrier.withMutation"))
        assertTrue(repository.contains("updateSubsMutex.withStateLock"))
        assertTrue(repository.contains("DbSet.withTransaction"))
        assertTrue(repository.contains("SUBSCRIPTION_MUTATION_STAGING_PREFIX"))
        assertTrue(state.contains("suspend fun updateSubscription("))
        assertTrue(state.contains("suspend fun deleteSubscription("))
        assertFalse(state.substringAfter("suspend fun updateSubscription(").substringBefore("suspend fun deleteSubscription(").contains("appScope.launch"))
        assertFalse(mainViewModel.contains("DbSet.subsItemDao.insert(newItem)"))
        assertFalse(mainViewModel.contains("DbSet.subsItemDao.update(newItem)"))
        assertFalse(httpService.contains("DbSet.subsItemDao.insert(current.copy"))
    }

    private fun sourceFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return File(relativePath)
        }
        return File(relativePath)
    }
}
