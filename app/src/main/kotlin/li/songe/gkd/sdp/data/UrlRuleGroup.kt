package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "url_rule_group")
data class UrlRuleGroup(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "name") val name: String,  // 组名，如"视频网站"

    @ColumnInfo(name = "enabled") val enabled: Boolean = true,

    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_end_time") val lockEndTime: Long = 0,

    @ColumnInfo(name = "order_index") val orderIndex: Int = 0,
) {
    /**
     * 检查是否已锁定
     */
    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()

    @Dao
    interface UrlRuleGroupDao {
        @Query("SELECT * FROM url_rule_group ORDER BY order_index ASC, id ASC")
        fun queryAll(): Flow<List<UrlRuleGroup>>

        @Query("SELECT * FROM url_rule_group WHERE enabled = 1 ORDER BY order_index ASC, id ASC")
        fun queryEnabled(): Flow<List<UrlRuleGroup>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(group: UrlRuleGroup): Long

        @Update
        suspend fun update(group: UrlRuleGroup)

        @Delete
        suspend fun delete(group: UrlRuleGroup)

        @Query("SELECT * FROM url_rule_group WHERE id = :id")
        suspend fun getById(id: Long): UrlRuleGroup?

        @Query("SELECT * FROM url_rule_group WHERE id = :id")
        fun getByIdFlow(id: Long): Flow<UrlRuleGroup?>

        @Query("DELETE FROM url_rule_group WHERE id = :id")
        suspend fun deleteById(id: Long)

        @Query("SELECT COUNT(*) FROM url_rule_group WHERE enabled = 1")
        fun countEnabled(): Flow<Int>
    }
}
