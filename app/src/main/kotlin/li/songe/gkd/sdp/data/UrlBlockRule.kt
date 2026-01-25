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
@Entity(tableName = "url_block_rule")
data class UrlBlockRule(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "pattern") val pattern: String,  // 匹配模式，如 "bilibili.com"

    @ColumnInfo(name = "match_type") val matchType: Int = MATCH_TYPE_DOMAIN,  // 0=域名匹配, 1=前缀匹配

    @ColumnInfo(name = "enabled") val enabled: Boolean = true,

    @ColumnInfo(name = "name") val name: String = "",  // 规则名称，如 "B站"

    @ColumnInfo(name = "redirect_url") val redirectUrl: String = DEFAULT_REDIRECT_URL,  // 跳转目标

    @ColumnInfo(name = "show_intercept") val showIntercept: Boolean = true,  // 是否显示全屏拦截

    @ColumnInfo(name = "intercept_message") val interceptMessage: String = "这真的重要吗？",

    @ColumnInfo(name = "order_index") val orderIndex: Int = 0,  // 排序索引

    @ColumnInfo(name = "group_id", defaultValue = "0") val groupId: Long = 0,  // 所属规则组ID，0表示不属于任何组

    @ColumnInfo(name = "is_locked", defaultValue = "0") val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_end_time", defaultValue = "0") val lockEndTime: Long = 0,
) {
    companion object {
        const val MATCH_TYPE_DOMAIN = 0  // 域名匹配
        const val MATCH_TYPE_PREFIX = 1  // 前缀匹配
        const val DEFAULT_REDIRECT_URL = "https://www.google.com"
    }

    /**
     * 检查是否已锁定
     */
    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()

    /**
     * 检查 URL 是否匹配此规则
     */
    fun matches(url: String): Boolean {
        if (!enabled) return false
        val normalizedUrl = url.lowercase().removePrefix("http://").removePrefix("https://").removePrefix("www.")
        val normalizedPattern = pattern.lowercase().removePrefix("http://").removePrefix("https://").removePrefix("www.")

        return when (matchType) {
            MATCH_TYPE_DOMAIN -> {
                // 域名匹配：URL 的域名部分包含 pattern
                val urlDomain = normalizedUrl.substringBefore("/").substringBefore("?")
                urlDomain == normalizedPattern || urlDomain.endsWith(".$normalizedPattern")
            }
            MATCH_TYPE_PREFIX -> {
                // 前缀匹配：URL 以 pattern 开头
                normalizedUrl.startsWith(normalizedPattern)
            }
            else -> false
        }
    }

    @Dao
    interface UrlBlockRuleDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(rule: UrlBlockRule): Long

        @Update
        suspend fun update(rule: UrlBlockRule)

        @Delete
        suspend fun delete(rule: UrlBlockRule)

        @Query("SELECT * FROM url_block_rule ORDER BY order_index ASC, id ASC")
        fun queryAll(): Flow<List<UrlBlockRule>>

        @Query("SELECT * FROM url_block_rule WHERE enabled = 1 ORDER BY order_index ASC, id ASC")
        fun queryEnabled(): Flow<List<UrlBlockRule>>

        @Query("SELECT * FROM url_block_rule WHERE enabled = 1")
        suspend fun getEnabledList(): List<UrlBlockRule>

        @Query("SELECT * FROM url_block_rule WHERE id = :id")
        suspend fun getById(id: Long): UrlBlockRule?

        @Query("DELETE FROM url_block_rule WHERE id = :id")
        suspend fun deleteById(id: Long)

        @Query("SELECT COUNT(*) FROM url_block_rule WHERE enabled = 1")
        fun countEnabled(): Flow<Int>

        @Query("SELECT * FROM url_block_rule WHERE group_id = :groupId ORDER BY order_index ASC, id ASC")
        fun queryByGroupId(groupId: Long): Flow<List<UrlBlockRule>>
    }
}
