package li.songe.gkd.sdp.data

import li.songe.gkd.sdp.db.AppDb
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfControlAttemptEventDaoContractTest {
    @Test
    fun eventEntityContainsOnlyLocalIntervalFields() {
        val columns = SelfControlAttemptEvent::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(columns.contains("eventKey"))
        assertTrue(columns.contains("eventKind"))
        assertTrue(columns.contains("subjectId"))
        assertTrue(columns.contains("subjectLabel"))
        assertTrue(columns.contains("occurredAt"))
        assertTrue(columns.contains("intervalMs"))
        assertTrue(columns.none { it in setOf("reasonText", "pattern", "actualUrl") })
    }

    @Test
    fun exactKeyRangeQueryAndBoundedLatestStateQueriesExist() {
        val methods = SelfControlAttempt.SelfControlAttemptDao::class.java.declaredMethods
            .map { it.name }
            .toSet()
        assertTrue(methods.contains("queryByEventKeyAndOccurredAtRange"))
        assertTrue(methods.contains("deleteAttemptsBefore"))
        assertTrue(methods.contains("countAttempts"))
        assertTrue(methods.contains("deleteOldestAttempts"))
    }

    @Test
    fun appDbExposesEventDaoContract() {
        // Room's @Database annotation is not retained for runtime reflection. The
        // generated schema artifact and KSP validation cover version/migration
        // metadata; this runtime contract only checks that AppDb exposes the DAO.
        assertTrue(
            AppDb::class.java.declaredMethods.any { it.name == "selfControlAttemptDao" }
        )
    }
}
