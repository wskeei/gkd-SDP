package li.songe.gkd.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "focus_lock")
data class FocusLock(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "locked_rules") val lockedRules: String, // JSON: List<LockedRule>
) {
    @Serializable
    data class LockedRule(
        val subsId: Long,
        val groupKey: Int,
        val appId: String? = null, // null = 全局规则
    )

    val isActive: Boolean get() = System.currentTimeMillis() < endTime
    val remainingTime: Long get() = (endTime - System.currentTimeMillis()).coerceAtLeast(0)

    @Dao
    interface FocusLockDao {
        @Insert
        suspend fun insert(vararg locks: FocusLock): List<Long>

        @Query("SELECT * FROM focus_lock WHERE end_time > :currentTime ORDER BY end_time DESC LIMIT 1")
        fun queryActive(currentTime: Long = System.currentTimeMillis()): Flow<FocusLock?>

        @Query("DELETE FROM focus_lock WHERE end_time <= :currentTime")
        suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis()): Int
    }
}
