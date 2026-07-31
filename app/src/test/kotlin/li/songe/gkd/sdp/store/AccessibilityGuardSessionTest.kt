package li.songe.gkd.sdp.store

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccessibilityGuardSessionTest {
    @Test
    fun defaultsAreInactiveAndBackwardCompatible() {
        val session = Json.decodeFromString<AccessibilityGuardSession>("{}")
        assertEquals(0L, session.generation)
        assertEquals(0L, session.disabledAtEpochMs)
        assertEquals(-1, session.lastReminderIndex)
        assertFalse(session.enforcementStarted)
        assertFalse(session.temporaryShutdownExpected)
        assertEquals(0L, session.grantFlowUntilEpochMs)
    }

    @Test
    fun activeSessionRoundTripsWithoutLosingGeneration() {
        val expected = AccessibilityGuardSession(
            generation = 7,
            disabledAtEpochMs = 1_000,
            lastReminderIndex = 3,
            enforcementStarted = true,
            temporaryShutdownExpected = true,
            grantFlowUntilEpochMs = 2_000,
        )
        val actual = Json.decodeFromString<AccessibilityGuardSession>(
            Json.encodeToString(expected)
        )
        assertEquals(expected, actual)
    }
}
