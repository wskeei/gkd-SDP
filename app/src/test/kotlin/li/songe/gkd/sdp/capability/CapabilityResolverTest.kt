package li.songe.gkd.sdp.capability

import li.songe.gkd.sdp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityResolverTest {
    private fun input(
        chosenMode: RuntimeModeChoice? = RuntimeModeChoice.ACCESSIBILITY,
        a11yReady: Boolean = true,
        shizukuReady: Boolean = false,
        overlayReady: Boolean = true,
        notificationReady: Boolean = true,
        batteryExempted: Boolean = true,
        a11yGuardEnabled: Boolean = true,
        appListReady: Boolean = true,
        selfControlLocked: Boolean = false,
        isGkdFlavor: Boolean = true,
    ) = CapabilityInput(
        chosenMode = chosenMode,
        a11yReady = a11yReady,
        shizukuReady = shizukuReady,
        overlayReady = overlayReady,
        notificationReady = notificationReady,
        batteryExempted = batteryExempted,
        a11yGuardEnabled = a11yGuardEnabled,
        appListReady = appListReady,
        selfControlLocked = selfControlLocked,
        isGkdFlavor = isGkdFlavor,
    )

    @Test
    fun everyNodeHasOneOfTheFiveStatuses() {
        val graph = CapabilityResolver.resolve(input())
        assertEquals(7, graph.nodes.size)
        graph.nodes.forEach { node ->
            assertTrue(
                node.status in setOf(
                    CapabilityStatus.UNAVAILABLE,
                    CapabilityStatus.ACTION_REQUIRED,
                    CapabilityStatus.READY,
                    CapabilityStatus.ACTIVE,
                    CapabilityStatus.LIMITED,
                ),
            )
        }
    }

    @Test
    fun actionRequiredNodesExposeExactlyOnePrimaryAction() {
        val graph = CapabilityResolver.resolve(
            input(
                a11yReady = false,
                overlayReady = false,
                notificationReady = false,
                appListReady = false,
            ),
        )
        val actionRequired = graph.nodes.filter { it.status == CapabilityStatus.ACTION_REQUIRED }
        assertTrue(actionRequired.isNotEmpty())
        actionRequired.forEach { node ->
            check(node.primaryAction != null)
        }
        // non-action nodes carry no primary action
        graph.nodes.filter { it.status != CapabilityStatus.ACTION_REQUIRED }.forEach { node ->
            assertNull(node.primaryAction)
        }
    }

    @Test
    fun nextStepFollowsFixedOrder() {
        val graph = CapabilityResolver.resolve(
            input(
                chosenMode = null,
                a11yReady = true,
                overlayReady = false,
                notificationReady = false,
            ),
        )
        assertEquals(CapabilityId.RUNTIME_MODE, graph.nextStep?.id)
    }

    @Test
    fun nextStepIsNullWhenEverythingReady() {
        val graph = CapabilityResolver.resolve(input())
        assertNull(graph.nextStep)
    }

    @Test
    fun accessibilityModeNeverForcesShizuku() {
        val graph = CapabilityResolver.resolve(input(chosenMode = RuntimeModeChoice.ACCESSIBILITY))
        val shizuku = graph.nodes.first { it.id == CapabilityId.SHIZUKU }
        assertEquals(CapabilityStatus.UNAVAILABLE, shizuku.status)
        assertNull(shizuku.primaryAction)
    }

    @Test
    fun automationModeDoesNotReportAccessibilityAsBlocker() {
        val graph = CapabilityResolver.resolve(
            input(
                chosenMode = RuntimeModeChoice.AUTOMATION,
                a11yReady = false,
                shizukuReady = true,
            ),
        )
        val runtime = graph.nodes.first { it.id == CapabilityId.RUNTIME_MODE }
        assertEquals(CapabilityStatus.ACTIVE, runtime.status)
        val a11yGuard = graph.nodes.first { it.id == CapabilityId.A11Y_GUARD }
        assertEquals(CapabilityStatus.UNAVAILABLE, a11yGuard.status)
        assertNull(a11yGuard.primaryAction)
    }

    @Test
    fun automationModeRequiresShizukuAuthorization() {
        val graph = CapabilityResolver.resolve(
            input(
                chosenMode = RuntimeModeChoice.AUTOMATION,
                shizukuReady = false,
            ),
        )
        val runtime = graph.nodes.first { it.id == CapabilityId.RUNTIME_MODE }
        assertEquals(CapabilityStatus.ACTION_REQUIRED, runtime.status)
        assertEquals(CapabilityActionTarget.OPEN_SHIZUKU, runtime.primaryAction?.target)
    }

    @Test
    fun batteryExemptionIsLimitedNotBlocking() {
        val graph = CapabilityResolver.resolve(input(batteryExempted = false))
        val battery = graph.nodes.first { it.id == CapabilityId.BATTERY_EXEMPTION }
        assertEquals(CapabilityStatus.LIMITED, battery.status)
        assertNull(graph.nextStep)
    }

    @Test
    fun lockedGuardStaysActiveWithoutDisableAction() {
        val graph = CapabilityResolver.resolve(
            input(selfControlLocked = true, a11yGuardEnabled = true),
        )
        val guard = graph.nodes.first { it.id == CapabilityId.A11Y_GUARD }
        assertEquals(CapabilityStatus.ACTIVE, guard.status)
        assertNull(guard.primaryAction)
        assertEquals(R.string.capability_guard_locked, guard.summaryRes)
    }

    @Test
    fun playFlavorHidesGuardNode() {
        val graph = CapabilityResolver.resolve(input(isGkdFlavor = false))
        val guard = graph.nodes.first { it.id == CapabilityId.A11Y_GUARD }
        assertEquals(CapabilityStatus.UNAVAILABLE, guard.status)
    }

    @Test
    fun guardCanBeEnabledWhileAccessibilityIsOff() {
        val graph = CapabilityResolver.resolve(
            input(a11yReady = false, a11yGuardEnabled = false),
        )
        val guard = graph.nodes.first { it.id == CapabilityId.A11Y_GUARD }
        assertEquals(CapabilityStatus.READY, guard.status)
        assertEquals(CapabilityActionTarget.TOGGLE_A11Y_GUARD, guard.primaryAction?.target)
    }

    @Test
    fun a11yGuardStatusFollowsFlavorModeServiceAndLockMatrix() {
        data class Case(
            val isGkdFlavor: Boolean,
            val chosenMode: RuntimeModeChoice?,
            val a11yReady: Boolean,
            val a11yGuardEnabled: Boolean,
            val selfControlLocked: Boolean,
            val expected: CapabilityStatus,
            val expectAction: Boolean,
        )

        val cases = listOf(
            Case(false, RuntimeModeChoice.ACCESSIBILITY, true, false, false, CapabilityStatus.UNAVAILABLE, false),
            Case(true, RuntimeModeChoice.AUTOMATION, false, true, false, CapabilityStatus.UNAVAILABLE, false),
            Case(true, null, false, true, false, CapabilityStatus.UNAVAILABLE, false),
            Case(true, RuntimeModeChoice.ACCESSIBILITY, false, false, false, CapabilityStatus.READY, true),
            Case(true, RuntimeModeChoice.ACCESSIBILITY, false, true, false, CapabilityStatus.ACTIVE, false),
            Case(true, RuntimeModeChoice.ACCESSIBILITY, true, true, true, CapabilityStatus.ACTIVE, false),
        )

        cases.forEachIndexed { index, c ->
            val graph = CapabilityResolver.resolve(
                input(
                    isGkdFlavor = c.isGkdFlavor,
                    chosenMode = c.chosenMode,
                    a11yReady = c.a11yReady,
                    a11yGuardEnabled = c.a11yGuardEnabled,
                    selfControlLocked = c.selfControlLocked,
                ),
            )
            val guard = graph.nodes.first { it.id == CapabilityId.A11Y_GUARD }
            assertEquals("case $index status", c.expected, guard.status)
            assertEquals("case $index action", c.expectAction, guard.primaryAction != null)
        }
    }
}
