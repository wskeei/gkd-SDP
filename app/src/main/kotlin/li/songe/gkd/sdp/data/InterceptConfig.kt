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
@Entity(tableName = "intercept_config")
data class InterceptConfig(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "app_id", defaultValue = "") val appId: String = "",
    @ColumnInfo(name = "group_key") val groupKey: Int,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @ColumnInfo(name = "cooldown_seconds") val cooldownSeconds: Int = 5,
    @ColumnInfo(name = "message") val message: String = "这真的重要吗？",
) {
    @Dao
    interface InterceptConfigDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(config: InterceptConfig): Long

        @Query("SELECT * FROM intercept_config WHERE subs_id = :subsId AND app_id = :appId AND group_key = :groupKey")
        fun getFlow(subsId: Long, appId: String, groupKey: Int): Flow<InterceptConfig?>

        @Query("SELECT * FROM intercept_config WHERE subs_id = :subsId AND app_id = :appId AND group_key = :groupKey")
        suspend fun get(subsId: Long, appId: String, groupKey: Int): InterceptConfig?

        @Query("SELECT * FROM intercept_config")
        fun queryAll(): Flow<List<InterceptConfig>>

        @Query("DELETE FROM intercept_config WHERE subs_id = :subsId AND app_id = :appId AND group_key = :groupKey")
        suspend fun delete(subsId: Long, appId: String, groupKey: Int)
    }
}
