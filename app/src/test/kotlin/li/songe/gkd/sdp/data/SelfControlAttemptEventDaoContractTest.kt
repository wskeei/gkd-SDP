package li.songe.gkd.sdp.data

import androidx.room.Database
import li.songe.gkd.sdp.db.AppDb
import org.junit.Assert.assertEquals
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
    fun appDbDeclaresVersion32AndMigrationPath() {
        val annotation = AppDb::class.java.getAnnotation(Database::class.java)

        assertNotNull(annotation)
        assertEquals(32, annotation.version)
        assertTrue(annotation.entities.any { it.qualifiedName == SelfControlAttemptEvent::class.qualifiedName })
        assertTrue(annotation.autoMigrations.any { it.from == 31 && it.to == 32 })
    }
}
