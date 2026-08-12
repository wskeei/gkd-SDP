package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.RawSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubscriptionMutationRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun negativeLocalSubscriptionIncrementsVersionForSameCurrentVersion() {
        val old = RawSubscription(
            id = -1L,
            name = "local",
            version = 7,
            apps = listOf(RawSubscription.RawApp(id = "demo.app", name = "Demo")),
        )
        subsMapFlow.value = mapOf(-1L to old)
        try {
            val normalized = SubscriptionMutationRepository.normalizeSubscription(old)

            assertEquals(8, normalized.version)
            assertEquals(emptyList<RawSubscription.RawApp>(), normalized.apps)
        } finally {
            subsMapFlow.value = emptyMap()
        }
    }

    @Test
    fun normalPositiveSubscriptionIsNotRewritten() {
        val subscription = RawSubscription(
            id = 42L,
            name = "remote",
            version = 3,
        )

        val normalized = SubscriptionMutationRepository.normalizeSubscription(subscription)

        assertEquals(subscription, normalized)
    }

    @Test
    fun mutationMtimeIsMonotonicAndRespectsPreviousValue() {
        val first = SubscriptionMutationRepository.nextMutationMtime(previous = null)
        val second = SubscriptionMutationRepository.nextMutationMtime(previous = null)
        val withFloor = SubscriptionMutationRepository.nextMutationMtime(previous = Long.MAX_VALUE - 1)

        assertTrue(second > first)
        assertTrue(withFloor > second)
        assertEquals(Long.MAX_VALUE, withFloor)
    }

    @Test
    fun restoreRemovesReplacedTargetAndMovesPreviousBack() {
        val target = temporaryFolder.newFile("target.json")
        val stagedPrevious = temporaryFolder.newFile("previous.json")
        target.writeText("replaced")
        stagedPrevious.writeText("original")

        SubscriptionMutationRepository.restoreSubscriptionFile(
            targetFile = target,
            stagedPreviousFile = stagedPrevious,
            previousStaged = true,
            targetReplaced = true,
        )

        assertEquals("original", target.readText())
        assertFalse(stagedPrevious.exists())
    }

    @Test
    fun restoreLeavesTargetInPlaceWhenPreviousWasNotStaged() {
        val target = temporaryFolder.newFile("target.json")
        target.writeText("unchanged")

        SubscriptionMutationRepository.restoreSubscriptionFile(
            targetFile = target,
            stagedPreviousFile = temporaryFolder.newFile("previous.json"),
            previousStaged = false,
            targetReplaced = false,
        )

        assertEquals("unchanged", target.readText())
    }
}
