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
import java.time.DayOfWeek
import java.time.LocalTime

@Serializable
@Entity(tableName = "focus_rule")
data class FocusRule(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "name") val name: String,  // 规则名称，如"复盘时间"

    @ColumnInfo(name = "rule_type", defaultValue = "0") val ruleType: Int = RULE_TYPE_SCHEDULED,  // 规则类型

    @ColumnInfo(name = "start_time") val startTime: String = "00:00",  // 开始时间 "22:30"（定时规则用）

    @ColumnInfo(name = "end_time") val endTime: String = "00:00",  // 结束时间 "23:00"（定时规则用）

    @ColumnInfo(name = "duration_minutes", defaultValue = "30") val durationMinutes: Int = 30,  // 持续时长（快速启动用）

    @ColumnInfo(name = "days_of_week") val daysOfWeek: String = "",  // 星期几 "1,2,3,4,5"（定时规则用）

    @ColumnInfo(name = "enabled") val enabled: Boolean = true,

    @ColumnInfo(name = "whitelist_apps") val whitelistApps: String = "[]",  // JSON: List<String> 包名列表

    @ColumnInfo(name = "intercept_message") val interceptMessage: String = "专注当下",

    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_end_time") val lockEndTime: Long = 0,  // 锁定结束时间

    @ColumnInfo(name = "lock_duration_minutes", defaultValue = "0") val lockDurationMinutes: Int = 0,  // 锁定持续时长（快速启动用）

    @ColumnInfo(name = "order_index") val orderIndex: Int = 0,
) {
    companion object {
        const val RULE_TYPE_SCHEDULED = 0  // 定时规则
        const val RULE_TYPE_QUICK_START = 1  // 快速启动模板

        fun parseTime(timeStr: String): LocalTime {
            val parts = timeStr.split(":")
            return LocalTime.of(parts[0].toInt(), parts[1].toInt())
        }
    }

    /**
     * 是否为快速启动模板
     */
    val isQuickStart: Boolean
        get() = ruleType == RULE_TYPE_QUICK_START

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
    fun withWhitelistPackages(packages: List<String>): FocusRule {
        return copy(whitelistApps = json.encodeToString(packages))
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
    fun withDaysOfWeek(days: List<Int>): FocusRule {
        return copy(daysOfWeek = days.joinToString(","))
    }

    /**
     * 检查当前时间是否在规则时间段内
     * 注意：快速启动模板始终返回 false，需要手动启动
     */
    fun isActiveNow(): Boolean {
        if (!enabled) return false
        if (isQuickStart) return false  // 快速启动模板不自动激活

        val now = java.time.LocalDateTime.now()
        val currentDayOfWeek = now.dayOfWeek.value  // 1=周一, 7=周日
        val currentTime = now.toLocalTime()

        // 检查星期几
        if (currentDayOfWeek !in getDaysOfWeekList()) return false

        // 检查时间段
        val start = parseTime(startTime)
        val end = parseTime(endTime)

        return if (end.isAfter(start)) {
            // 正常时间段，如 09:00 - 17:00
            currentTime.isAfter(start) && currentTime.isBefore(end) ||
                    currentTime == start
        } else {
            // 跨午夜时间段，如 22:00 - 02:00
            currentTime.isAfter(start) || currentTime.isBefore(end) ||
                    currentTime == start
        }
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
     * 格式化时长显示
     */
    fun formatDuration(): String {
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时"
            else -> "${minutes}分钟"
        }
    }

    @Dao
    interface FocusRuleDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(rule: FocusRule): Long

        @Update
        suspend fun update(rule: FocusRule)

        @Delete
        suspend fun delete(rule: FocusRule)

        @Query("SELECT * FROM focus_rule ORDER BY order_index ASC, id ASC")
        fun queryAll(): Flow<List<FocusRule>>

        @Query("SELECT * FROM focus_rule WHERE enabled = 1 ORDER BY order_index ASC, id ASC")
        fun queryEnabled(): Flow<List<FocusRule>>

        @Query("SELECT * FROM focus_rule WHERE enabled = 1")
        suspend fun getEnabledList(): List<FocusRule>

        @Query("SELECT * FROM focus_rule WHERE id = :id")
        suspend fun getById(id: Long): FocusRule?

        @Query("SELECT * FROM focus_rule WHERE id = :id")
        fun getByIdFlow(id: Long): Flow<FocusRule?>

        @Query("DELETE FROM focus_rule WHERE id = :id")
        suspend fun deleteById(id: Long)

        @Query("SELECT COUNT(*) FROM focus_rule WHERE enabled = 1")
        fun countEnabled(): Flow<Int>
    }
}
