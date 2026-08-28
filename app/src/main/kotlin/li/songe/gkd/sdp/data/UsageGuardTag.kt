package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "usage_guard_tag")
data class UsageGuardTag(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_preset") val isPreset: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val OTHER_TAG_NAME = "其他"
        const val QUERY_ALL_SQL = "SELECT * FROM usage_guard_tag " +
            "ORDER BY CASE WHEN TRIM(name) = '" + OTHER_TAG_NAME + "' THEN 1 ELSE 0 END ASC, " +
            "is_preset DESC, created_at ASC, id ASC"
    }

    @Dao
    interface UsageGuardTagDao {
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insert(tag: UsageGuardTag): Long

        @Query(QUERY_ALL_SQL)
        fun queryAll(): Flow<List<UsageGuardTag>>

        @Query("DELETE FROM usage_guard_tag WHERE id = :id AND is_preset = 0")
        suspend fun deleteCustomTag(id: Long): Int

        @Query("SELECT COUNT(*) FROM usage_guard_tag")
        suspend fun count(): Int
    }
}
