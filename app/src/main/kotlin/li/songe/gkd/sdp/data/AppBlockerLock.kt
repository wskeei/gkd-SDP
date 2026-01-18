package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "app_blocker_lock")
data class AppBlockerLock(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Long = 1,  // 单例

    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_end_time") val lockEndTime: Long = 0,
) {
    /**
     * 检查是否已锁定
     */
    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()

    companion object {
        val EMPTY = AppBlockerLock(
            id = 1,
            isLocked = false,
            lockEndTime = 0
        )
    }

    @Dao
    interface AppBlockerLockDao {
        @Query("SELECT * FROM app_blocker_lock WHERE id = 1")
        fun getLock(): Flow<AppBlockerLock?>

        @Query("SELECT * FROM app_blocker_lock WHERE id = 1")
        suspend fun getLockNow(): AppBlockerLock?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(lock: AppBlockerLock)

        @Query("UPDATE app_blocker_lock SET is_locked = 0 WHERE id = 1")
        suspend fun unlock()
    }
}
