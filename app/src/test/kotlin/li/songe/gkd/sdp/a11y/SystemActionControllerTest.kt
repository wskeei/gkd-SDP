package li.songe.gkd.sdp.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemActionControllerTest {
    @Test
    fun homeUsesShizukuWhenBothKeyEventsAreAccepted() {
        var fallbackCalls = 0
        val controller = SystemActionController(
            inputHome = { true },
            accessibilityHome = { fallbackCalls++; true },
        )

        assertTrue(controller.performHome())
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun homeFallsBackWhenShizukuInjectionIsRejected() {
        var fallbackCalls = 0
        val controller = SystemActionController(
            inputHome = { false },
            accessibilityHome = { fallbackCalls++; true },
        )

        assertTrue(controller.performHome())
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun missingShizukuAlsoUsesAccessibilityFallback() {
        val controller = SystemActionController(
            inputHome = { null },
            accessibilityHome = { true },
        )

        assertTrue(controller.performHome())
    }

    @Test
    fun homeReportsFailureWhenBothPathsFail() {
        val controller = SystemActionController(
            inputHome = { false },
            accessibilityHome = { false },
        )

        assertEquals(false, controller.performHome())
    }
}
