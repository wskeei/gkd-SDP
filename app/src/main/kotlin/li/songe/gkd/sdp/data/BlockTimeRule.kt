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
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
@Entity(tableName = "block_time_rule")
data class BlockTimeRule(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "target_type") val targetType: Int,  // 0=单个应用, 1=应用组

    @ColumnInfo(name = "target_id") val targetId: String,  // 应用包名 或 应用组ID

    @ColumnInfo(name = "start_time") val startTime: String,  // "22:00"

    @ColumnInfo(name = "end_time") val endTime: String,  // "08:00"

    @ColumnInfo(name = "days_of_week") val daysOfWeek: String,  // "1,2,3,4,5"

    @ColumnInfo(name = "enabled") val enabled: Boolean = true,

    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_end_time") val lockEndTime: Long = 0,

    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "intercept_message") val interceptMessage: String = "这真的重要吗？",

    // 是否为允许模式（反选）：true = 时间段内允许，其他时间拦截
    @ColumnInfo(name = "is_allow_mode", defaultValue = "0") val isAllowMode: Boolean = false,
) {
    companion object {
        const val TARGET_TYPE_APP = 0
        const val TARGET_TYPE_GROUP = 1

        fun parseTime(timeStr: String): LocalTime {
            val parts = timeStr.split(":")
            return LocalTime.of(parts[0].toInt(), parts[1].toInt())
        }

        // 预设模板
        data class TimeTemplate(
            val name: String,
            val startTime: String,
            val endTime: String,
            val daysOfWeek: String,
            val description: String
        )

        val TEMPLATES = listOf(
            TimeTemplate("工作日", "09:00", "18:00", "1,2,3,4,5", "周一至周五 9:00-18:00"),
            TimeTemplate("周末", "00:00", "23:59", "6,7", "周六日全天"),
            TimeTemplate("每晚", "22:00", "08:00", "1,2,3,4,5,6,7", "每天 22:00-次日08:00"),
            TimeTemplate("午休", "12:00", "14:00", "1,2,3,4,5", "周一至周五 12:00-14:00"),
            TimeTemplate("全天候", "00:00", "23:59", "1,2,3,4,5,6,7", "每天全天"),
        )
    }

    /**
     * 获取星期几列表
     */
    fun getDaysOfWeekList(): List<Int> {
        return if (daysOfWeek.isBlank()) {
            emptyList()
        } else {
            daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
    }

    /**
     * 设置星期几列表
     */
    fun withDaysOfWeek(days: List<Int>): BlockTimeRule {
        return copy(daysOfWeek = days.joinToString(","))
    }

    /**
     * 检查当前时间是否应该拦截
     * - 禁止模式：时间段内拦截 (返回 true)
     * - 允许模式：时间段外拦截 (时间段内返回 false，时间段外返回 true)
     */
    fun isActiveNow(): Boolean = isActiveAt(LocalDateTime.now())

    /** Safe time evaluation for persisted rules, including legacy bad input. */
    fun isActiveAt(now: LocalDateTime): Boolean {
        if (!enabled) return false
        val start = runCatching { parseTime(startTime) }.getOrNull() ?: return false
        val end = runCatching { parseTime(endTime) }.getOrNull() ?: return false
        if (!Regex("\\d{2}:\\d{2}").matches(startTime) ||
            !Regex("\\d{2}:\\d{2}").matches(endTime)
        ) return false

        val days = getDaysOfWeekList().filter { it in 1..7 }.toSet()
        val currentDay = now.dayOfWeek.value
        val currentTime = now.toLocalTime()
        val inWindow = when {
            start == end -> currentDay in days
            end.isAfter(start) && currentDay in days -> {
                if (end == LocalTime.of(23, 59)) {
                    !currentTime.isBefore(start)
                } else {
                    !currentTime.isBefore(start) && currentTime.isBefore(end)
                }
            }
            end.isAfter(start) -> false
            else -> {
                val previousDay = now.toLocalDate().minusDays(1).dayOfWeek.value
                (currentDay in days && !currentTime.isBefore(start)) ||
                    (previousDay in days && currentTime.isBefore(end))
            }
        }
        return if (isAllowMode) !inWindow else inWindow
    }

    /**
     * 获取模式描述
     */
    fun formatModeDescription(): String {
        return if (isAllowMode) "允许时间段" else "禁止时间段"
    }

    /**
     * 检查是否已锁定
     */
    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()

    /**
     * 格式化星期几显示
     */
    fun formatDaysOfWeek(): String {
        val days = getDaysOfWeekList()
        if (days.isEmpty()) return "未设置"
        if (days.size == 7) return "每天"
        if (days == listOf(1, 2, 3, 4, 5)) return "工作日"
        if (days == listOf(6, 7)) return "周末"

        val dayNames = mapOf(
            1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
            5 to "周五", 6 to "周六", 7 to "周日"
        )
        return days.mapNotNull { dayNames[it] }.joinToString("、")
    }

    /**
     * 格式化时间段显示
     */
    fun formatTimeRange(): String {
        return "$startTime-$endTime"
    }

    @Dao
    interface BlockTimeRuleDao {
        @Query("SELECT * FROM block_time_rule WHERE target_type = :type AND target_id = :id ORDER BY created_at DESC")
        fun queryByTarget(type: Int, id: String): Flow<List<BlockTimeRule>>

        @Query("SELECT * FROM block_time_rule WHERE enabled = 1")
        fun queryEnabled(): Flow<List<BlockTimeRule>>

        @Query("SELECT * FROM block_time_rule ORDER BY created_at DESC")
        fun queryAll(): Flow<List<BlockTimeRule>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(rule: BlockTimeRule): Long

        @Update
        suspend fun update(rule: BlockTimeRule)

        @Delete
        suspend fun delete(rule: BlockTimeRule)

        @Query("SELECT * FROM block_time_rule WHERE id = :id")
        suspend fun getById(id: Long): BlockTimeRule?

        @Query("DELETE FROM block_time_rule WHERE id = :id")
        suspend fun deleteById(id: Long)

        @Query("DELETE FROM block_time_rule WHERE target_type = :type AND target_id = :id")
        suspend fun deleteByTarget(type: Int, id: String)

        @Query("UPDATE block_time_rule SET enabled = 1 WHERE enabled = 0")
        suspend fun enableAllDisabled(): Int
    }
}
