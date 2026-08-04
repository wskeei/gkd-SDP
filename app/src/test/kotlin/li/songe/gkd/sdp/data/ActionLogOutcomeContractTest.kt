package li.songe.gkd.sdp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLogOutcomeContractTest {
    @Test
    fun outcomeConstantsKeepLegacyExecutedRowsAndAddInterceptedRows() {
        assertEquals(1, ActionLog.OUTCOME_ACTION_EXECUTED)
        assertEquals(2, ActionLog.OUTCOME_INTERCEPTED)
    }

    @Test
    fun boundedRetentionContractIsIndependentFromActionCount() {
        assertEquals(500, ActionLog.MAX_ROWS)
        assertEquals(100, ActionLog.PRUNE_EVERY_ROWS)
    }

    @Test
    fun legacyRowsDefaultToExecutedOutcome() {
        assertEquals(ActionLog.OUTCOME_ACTION_EXECUTED, ActionLog.DEFAULT_OUTCOME)
    }

    @Test
    fun outcomeHelpersDistinguishDisplayedInterceptsFromExecutedActions() {
        assertTrue(ActionLog.OUTCOME_INTERCEPTED.isIntercepted())
        assertFalse(ActionLog.OUTCOME_INTERCEPTED.isExecuted())
        assertTrue(ActionLog.OUTCOME_ACTION_EXECUTED.isExecuted())
        assertFalse(ActionLog.OUTCOME_ACTION_EXECUTED.isIntercepted())
    }
}
