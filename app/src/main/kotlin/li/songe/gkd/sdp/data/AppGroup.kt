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
import li.songe.gkd.sdp.util.json

@Serializable
@Entity(tableName = "app_group")
data class AppGroup(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "name") val name: String,  // 组名，如"短视频"

    @ColumnInfo(name = "app_ids") val appIds: String = "[]",  // JSON: List<String> 包名列表

    @ColumnInfo(name = "enabled") val enabled: Boolean = true,

    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_end_time") val lockEndTime: Long = 0,

    @ColumnInfo(name = "order_index") val orderIndex: Int = 0,
) {
    /**
     * 获取应用列表
     */
    fun getAppList(): List<String> {
        return try {
            json.decodeFromString<List<String>>(appIds)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 设置应用列表
     */
    fun withAppList(apps: List<String>): AppGroup {
        return copy(appIds = json.encodeToString(apps))
    }

    /**
     * 检查是否已锁定
     */
    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()

    /**
     * 检查应用是否在组内
     */
    fun containsApp(packageName: String): Boolean {
        return packageName in getAppList()
    }

    @Dao
    interface AppGroupDao {
        @Query("SELECT * FROM app_group ORDER BY order_index ASC, id ASC")
        fun queryAll(): Flow<List<AppGroup>>

        @Query("SELECT * FROM app_group WHERE enabled = 1 ORDER BY order_index ASC, id ASC")
        fun queryEnabled(): Flow<List<AppGroup>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(group: AppGroup): Long

        @Update
        suspend fun update(group: AppGroup)

        @Delete
        suspend fun delete(group: AppGroup)

        @Query("SELECT * FROM app_group WHERE id = :id")
        suspend fun getById(id: Long): AppGroup?

        @Query("SELECT * FROM app_group WHERE id = :id")
        fun getByIdFlow(id: Long): Flow<AppGroup?>

        @Query("DELETE FROM app_group WHERE id = :id")
        suspend fun deleteById(id: Long)

        @Query("SELECT COUNT(*) FROM app_group WHERE enabled = 1")
        fun countEnabled(): Flow<Int>
    }
}
