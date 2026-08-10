package li.songe.gkd.sdp.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration32To33Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDb::class.java,
        emptyList(),
    )

    @Test
    fun migrates32To33WithoutLosingRowsOrAddingSensitiveValues() {
        helper.createDatabase(TEST_DB, 32).apply {
            execSQL(
                """
                INSERT INTO action_log
                (id, ctime, app_id, activity_id, subs_id, subs_version, group_key, group_type, rule_index, rule_key)
                VALUES (1, 1000, 'com.example.app', 'Activity', 7, 3, 2, 2, 4, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO usage_guard_record
                (id, app_id, app_name, tag_names, reason_text, grant_mode,
                 requested_duration_minutes, requested_at, granted_at, expires_at,
                 ended_at, end_reason)
                VALUES (1, 'com.example.app', 'Example', '[]', 'reason', 1,
                        30, 1000, 1000, 1900000, 1800000, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 33, true, MIGRATION_32_33)

        migrated.query("SELECT * FROM action_log WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("outcome")))
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("matched_at")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("subs_name_snapshot")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("group_name_snapshot")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("rule_name_snapshot")))
        }
        migrated.query("SELECT * FROM usage_guard_record WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("last_usage_ended_at")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("request_gap_ms")))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-32-33-test.db"
    }
}
