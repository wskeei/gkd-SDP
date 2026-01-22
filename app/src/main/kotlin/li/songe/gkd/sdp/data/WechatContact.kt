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
@Entity(tableName = "wechat_contact")
data class WechatContact(
    @PrimaryKey
    @ColumnInfo(name = "wechat_id") val wechatId: String,  // 微信号（主键）

    @ColumnInfo(name = "nickname") val nickname: String,  // 昵称

    @ColumnInfo(name = "remark") val remark: String = "",  // 备注名

    @ColumnInfo(name = "avatar_url") val avatarUrl: String = "",  // 头像（可选）

    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "shortcut_id", defaultValue = "") val shortcutId: String = ""  // 桌面快捷方式 ID
) {
    val displayName: String get() = remark.ifEmpty { nickname }
    
    // 检查是否有有效的快捷方式 ID
    val hasShortcut: Boolean get() = shortcutId.isNotBlank()

    @Dao
    interface WechatContactDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertAll(contacts: List<WechatContact>)

        @Query("SELECT * FROM wechat_contact ORDER BY remark, nickname")
        fun queryAll(): Flow<List<WechatContact>>

        @Query("SELECT * FROM wechat_contact WHERE wechat_id IN (:ids)")
        suspend fun getByIds(ids: List<String>): List<WechatContact>

        @Query("SELECT wechat_id FROM wechat_contact WHERE nickname = :name OR remark = :name LIMIT 1")
        suspend fun findIdByName(name: String): String?

        @Query("SELECT * FROM wechat_contact WHERE nickname = :name OR remark = :name LIMIT 1")
        suspend fun findByDisplayName(name: String): WechatContact?

        @Query("DELETE FROM wechat_contact")
        suspend fun deleteAll()
    }
}
