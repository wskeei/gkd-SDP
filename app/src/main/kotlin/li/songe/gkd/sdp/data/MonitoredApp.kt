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

/**
 * 监控的应用列表
 */
@Entity(tableName = "monitored_app")
data class MonitoredApp(
    @PrimaryKey
    @ColumnInfo(name = "package_name") val packageName: String,

    @ColumnInfo(name = "display_name") val displayName: String,

    @ColumnInfo(name = "enabled") val enabled: Boolean = true,

    @ColumnInfo(name = "is_currently_installed") val isCurrentlyInstalled: Boolean = false
) {
    companion object {
        // 预置监控应用列表
        val DEFAULT_APPS = listOf(
            MonitoredApp("com.ss.android.ugc.aweme", "抖音"),
            MonitoredApp("tv.danmaku.bili", "哔哩哔哩"),
            MonitoredApp("com.zhihu.android", "知乎"),
            MonitoredApp("com.xingin.xhs", "小红书"),
            MonitoredApp("com.smile.gifmaker", "快手"),
            MonitoredApp("com.kuaishou.nebula", "快手极速版"),
            MonitoredApp("com.ss.android.ugc.aweme.lite", "抖音极速版"),
            MonitoredApp("com.duowan.kiwi", "虎牙直播"),
            MonitoredApp("com.douyu.game", "斗鱼直播")
        )
    }

    @Dao
    interface MonitoredAppDao {
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertAll(apps: List<MonitoredApp>)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(app: MonitoredApp)

        @Update
        suspend fun update(app: MonitoredApp)

        @Delete
        suspend fun delete(app: MonitoredApp)

        @Query("SELECT * FROM monitored_app ORDER BY display_name")
        fun queryAll(): Flow<List<MonitoredApp>>

        @Query("SELECT * FROM monitored_app WHERE enabled = 1")
        fun queryEnabled(): Flow<List<MonitoredApp>>

        @Query("SELECT package_name FROM monitored_app WHERE enabled = 1")
        suspend fun getEnabledPackageNames(): List<String>

        @Query("SELECT * FROM monitored_app WHERE package_name = :packageName")
        suspend fun getByPackageName(packageName: String): MonitoredApp?

        @Query("UPDATE monitored_app SET is_currently_installed = :installed WHERE package_name = :packageName")
        suspend fun updateInstalledStatus(packageName: String, installed: Boolean)

        @Query("DELETE FROM monitored_app")
        suspend fun deleteAll()
    }
}
