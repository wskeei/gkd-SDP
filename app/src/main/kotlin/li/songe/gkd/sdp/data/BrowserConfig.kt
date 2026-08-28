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
@Entity(tableName = "browser_config")
data class BrowserConfig(
    @PrimaryKey
    @ColumnInfo(name = "package_name") val packageName: String,  // 如 "com.android.chrome"

    @ColumnInfo(name = "name") val name: String,  // 如 "Chrome"

    @ColumnInfo(name = "url_bar_id") val urlBarId: String,  // 地址栏节点 ID

    @ColumnInfo(name = "enabled") val enabled: Boolean = true,

    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,  // 内置浏览器不可删除
) {
    companion object {
        /**
         * 内置浏览器配置列表
         */
        val BUILTIN_BROWSERS = listOf(
            BrowserConfig(
                packageName = "com.android.chrome",
                name = "Chrome",
                urlBarId = "com.android.chrome:id/url_bar",
                enabled = true,
                isBuiltin = true
            ),
            BrowserConfig(
                packageName = "com.microsoft.emmx",
                name = "Edge",
                urlBarId = "com.microsoft.emmx:id/url_bar",
                enabled = true,
                isBuiltin = true
            ),
            BrowserConfig(
                packageName = "org.mozilla.firefox",
                name = "Firefox",
                urlBarId = "org.mozilla.firefox:id/url_bar_title",
                enabled = true,
                isBuiltin = true
            ),
            BrowserConfig(
                packageName = "com.opera.browser",
                name = "Opera",
                urlBarId = "com.opera.browser:id/url_field",
                enabled = true,
                isBuiltin = true
            ),
            BrowserConfig(
                packageName = "com.brave.browser",
                name = "Brave",
                urlBarId = "com.brave.browser:id/url_bar",
                enabled = true,
                isBuiltin = true
            ),
            BrowserConfig(
                packageName = "com.vivaldi.browser",
                name = "Vivaldi",
                urlBarId = "com.vivaldi.browser:id/url_bar",
                enabled = true,
                isBuiltin = true
            ),
            BrowserConfig(
                packageName = "com.sec.android.app.sbrowser",
                name = "Samsung Browser",
                urlBarId = "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                enabled = true,
                isBuiltin = true
            ),
            BrowserConfig(
                packageName = "mark.via",
                name = "Via",
                urlBarId = "mark.via:id/ck",
                enabled = true,
                isBuiltin = true
            ),
            BrowserConfig(
                packageName = "com.quark.browser",
                name = "夸克",
                urlBarId = "com.quark.browser:id/url-bar",
                enabled = true,
                isBuiltin = true
            ),
        )

        /**
         * 获取所有内置浏览器的包名集合
         */
        val BUILTIN_PACKAGE_NAMES = BUILTIN_BROWSERS.map { it.packageName }.toSet()
    }

    @Dao
    interface BrowserConfigDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(config: BrowserConfig)

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertIgnore(configs: List<BrowserConfig>)

        @Update
        suspend fun update(config: BrowserConfig)

        @Delete
        suspend fun delete(config: BrowserConfig)

        @Query("SELECT * FROM browser_config ORDER BY is_builtin DESC, name ASC")
        fun queryAll(): Flow<List<BrowserConfig>>

        @Query("SELECT * FROM browser_config WHERE enabled = 1")
        fun queryEnabled(): Flow<List<BrowserConfig>>

        @Query("SELECT * FROM browser_config WHERE enabled = 1")
        suspend fun getEnabledList(): List<BrowserConfig>

        @Query("SELECT * FROM browser_config WHERE package_name = :packageName")
        suspend fun getByPackageName(packageName: String): BrowserConfig?

        @Query("DELETE FROM browser_config WHERE package_name = :packageName AND is_builtin = 0")
        suspend fun deleteByPackageName(packageName: String)

        @Query("SELECT COUNT(*) FROM browser_config WHERE enabled = 1")
        fun countEnabled(): Flow<Int>
    }
}
