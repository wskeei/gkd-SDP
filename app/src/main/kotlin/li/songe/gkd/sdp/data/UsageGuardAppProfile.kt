package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.sdp.util.UsageGuardPolicy

@Entity(tableName = "usage_guard_app_profile")
data class UsageGuardAppProfile(
    @PrimaryKey
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "selected_target") val selectedTarget: Boolean = false,
    @ColumnInfo(name = "global_whitelist") val globalWhitelist: Boolean = false,
    @ColumnInfo(name = "grant_mode") val grantMode: Int = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
) {
    @Dao
    interface UsageGuardAppProfileDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(profile: UsageGuardAppProfile)

        @Query("SELECT * FROM usage_guard_app_profile ORDER BY updated_at DESC")
        fun queryAll(): Flow<List<UsageGuardAppProfile>>

        @Query("SELECT * FROM usage_guard_app_profile WHERE app_id = :appId LIMIT 1")
        suspend fun getByAppId(appId: String): UsageGuardAppProfile?

        @Query(
            """
            DELETE FROM usage_guard_app_profile
            WHERE selected_target = 0
              AND global_whitelist = 0
              AND grant_mode = :defaultGrantMode
            """
        )
        suspend fun deleteUnusedProfiles(defaultGrantMode: Int): Int
    }
}
