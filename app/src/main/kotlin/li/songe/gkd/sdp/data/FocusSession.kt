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
import li.songe.gkd.sdp.util.json

@Serializable
@Entity(tableName = "focus_session")
data class FocusSession(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Long = 1,  // 只有一条记录

    @ColumnInfo(name = "is_active") val isActive: Boolean = false,

    @ColumnInfo(name = "rule_id") val ruleId: Long? = null,  // 关联的规则ID

    @ColumnInfo(name = "start_time") val startTime: Long = 0,

    @ColumnInfo(name = "end_time") val endTime: Long = 0,

    @ColumnInfo(name = "whitelist_apps") val whitelistApps: String = "[]",  // 当前生效的白名单

    @ColumnInfo(name = "wechat_whitelist", defaultValue = "[]") val wechatWhitelist: String = "[]",  // 微信联系人白名单

    @ColumnInfo(name = "intercept_message") val interceptMessage: String = "专注当下",

    @ColumnInfo(name = "is_manual") val isManual: Boolean = false,  // 是否手动开启

    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_end_time") val lockEndTime: Long = 0,
) {
    /**
     * 获取白名单应用包名列表
     */
    fun getWhitelistPackages(): List<String> {
        return try {
            json.decodeFromString<List<String>>(whitelistApps)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 设置白名单应用包名列表
     */
    fun withWhitelistPackages(packages: List<String>): FocusSession {
        return copy(whitelistApps = json.encodeToString(packages))
    }

    /**
     * 获取微信白名单联系人列表
     */
    fun getWechatWhitelist(): List<String> {
        return try {
            json.decodeFromString<List<String>>(wechatWhitelist)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 设置微信白名单联系人列表
     */
    fun withWechatWhitelist(wechatIds: List<String>): FocusSession {
        return copy(wechatWhitelist = json.encodeToString(wechatIds))
    }

    /**
     * 检查会话是否有效（在时间范围内）
     */
    fun isValidNow(): Boolean {
        if (!isActive) return false
        val now = System.currentTimeMillis()
        return now in startTime..endTime
    }

    /**
     * 获取剩余时间（毫秒）
     */
    fun getRemainingTime(): Long {
        if (!isActive) return 0
        return (endTime - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /**
     * 检查是否已锁定
     */
    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()

    /**
     * 检查包名是否在白名单中
     */
    fun isInWhitelist(packageName: String): Boolean {
        return packageName in getWhitelistPackages()
    }

    companion object {
        val EMPTY = FocusSession(
            id = 1,
            isActive = false,
            ruleId = null,
            startTime = 0,
            endTime = 0,
            whitelistApps = "[]",
            wechatWhitelist = "[]",
            interceptMessage = "专注当下",
            isManual = false,
            isLocked = false,
            lockEndTime = 0
        )
    }

    @Dao
    interface FocusSessionDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(session: FocusSession)

        @Query("SELECT * FROM focus_session WHERE id = 1")
        fun getSession(): Flow<FocusSession?>

        @Query("SELECT * FROM focus_session WHERE id = 1")
        suspend fun getSessionNow(): FocusSession?

        @Query("UPDATE focus_session SET is_active = 0 WHERE id = 1")
        suspend fun deactivate()

        @Query("UPDATE focus_session SET whitelist_apps = :whitelistApps WHERE id = 1")
        suspend fun updateWhitelist(whitelistApps: String)

        @Query("UPDATE focus_session SET wechat_whitelist = :wechatWhitelist WHERE id = 1")
        suspend fun updateWechatWhitelist(wechatWhitelist: String)

        @Query("DELETE FROM focus_session WHERE id = 1")
        suspend fun clear()
    }
}
