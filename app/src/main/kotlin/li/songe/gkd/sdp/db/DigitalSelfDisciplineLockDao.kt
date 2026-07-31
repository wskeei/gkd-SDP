package li.songe.gkd.sdp.db

import androidx.room.Dao
import androidx.room.Query

/**
 * Read-only cross-feature lock lookup used before a user disables a guard.
 *
 * This DAO intentionally does not introduce a table. Room observes the
 * existing tables referenced by the query, while the disable path asks for a
 * fresh timestamped answer instead of trusting a potentially stale UI flow.
 */
@Dao
interface DigitalSelfDisciplineLockDao {
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM constraint_config
                WHERE lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM focus_lock
                WHERE end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM focus_session
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM focus_rule
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM app_blocker_lock
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM app_group
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM block_time_rule
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM url_blocker_lock
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM url_rule_group
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM url_block_rule
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
            UNION ALL
            SELECT 1 FROM url_time_rule
                WHERE is_locked = 1 AND lock_end_time > :nowEpochMs
        )
        """
    )
    suspend fun hasAnyActiveLock(nowEpochMs: Long): Boolean
}
