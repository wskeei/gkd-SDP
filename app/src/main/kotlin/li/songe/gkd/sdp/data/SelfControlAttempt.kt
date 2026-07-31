package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(tableName = "self_control_attempt")
data class SelfControlAttempt(
    @PrimaryKey
    @ColumnInfo(name = "event_key") val eventKey: String,
    @ColumnInfo(name = "event_kind") val eventKind: Int,
    @ColumnInfo(name = "last_occurred_at") val lastOccurredAt: Long,
) {
    companion object {
        const val KIND_APP_BLOCKER = 1
        const val KIND_SELECTOR_INTERCEPT = 2
        const val KIND_URL_INTERCEPT = 3
    }

    @Dao
    interface SelfControlAttemptDao {
        @Query("SELECT * FROM self_control_attempt WHERE event_key = :eventKey LIMIT 1")
        suspend fun getByKey(eventKey: String): SelfControlAttempt?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(attempt: SelfControlAttempt)

        @Transaction
        suspend fun recordAndGetPrevious(attempt: SelfControlAttempt): Long? {
            val previous = getByKey(attempt.eventKey)?.lastOccurredAt
            insert(attempt)
            return previous
        }
    }
}
