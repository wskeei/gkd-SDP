package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 应用安装/卸载记录
 */
@Entity(tableName = "app_install_log")
data class AppInstallLog(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "package_name") val packageName: String,

    @ColumnInfo(name = "app_name") val appName: String,

    @ColumnInfo(name = "action") val action: String,  // "install" or "uninstall"

    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "date") val date: String  // yyyy-MM-dd 格式
) {
    companion object {
        const val ACTION_INSTALL = "install"
        const val ACTION_UNINSTALL = "uninstall"
    }

    @Dao
    interface AppInstallLogDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(log: AppInstallLog)

        @Query("SELECT * FROM app_install_log ORDER BY timestamp DESC")
        fun queryAll(): Flow<List<AppInstallLog>>

        @Query("SELECT * FROM app_install_log ORDER BY timestamp DESC")
        suspend fun getAll(): List<AppInstallLog>

        @Query("SELECT * FROM app_install_log WHERE date = :date ORDER BY timestamp DESC")
        suspend fun getByDate(date: String): List<AppInstallLog>

        @Query("SELECT DISTINCT date FROM app_install_log ORDER BY date DESC")
        fun queryAllDates(): Flow<List<String>>

        /**
         * 获取指定日期安装且目前仍存在的应用
         * 逻辑：该日期有 install 记录，且之后没有 uninstall 记录
         */
        @Query("""
            SELECT DISTINCT l1.package_name FROM app_install_log l1 
            WHERE l1.date = :date AND l1.action = 'install'
            AND NOT EXISTS (
                SELECT 1 FROM app_install_log l2 
                WHERE l2.package_name = l1.package_name 
                AND l2.action = 'uninstall' 
                AND l2.timestamp > l1.timestamp
            )
        """)
        suspend fun getStillInstalledOnDate(date: String): List<String>

        @Query("SELECT EXISTS(SELECT 1 FROM app_install_log WHERE package_name = :packageName AND action = 'install')")
        suspend fun hasInstallLog(packageName: String): Boolean

        /**
         * 获取每天仍存在的应用数量（用于热力图）
         */
        @Query("""
            SELECT date, COUNT(DISTINCT package_name) as count FROM app_install_log l1
            WHERE action = 'install'
            AND NOT EXISTS (
                SELECT 1 FROM app_install_log l2 
                WHERE l2.package_name = l1.package_name 
                AND l2.action = 'uninstall' 
                AND l2.timestamp > l1.timestamp
            )
            GROUP BY date
        """)
        fun queryHeatmapData(): Flow<List<DateCount>>

        @Query("DELETE FROM app_install_log")
        suspend fun deleteAll()
    }
}

/**
 * 日期和计数对，用于热力图
 */
data class DateCount(
    val date: String,
    val count: Int
)
