package li.songe.gkd.sdp.data

import android.content.Context
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
import li.songe.gkd.sdp.R
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
@Entity(tableName = "url_time_rule")
data class UrlTimeRule(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "target_type") val targetType: Int,  // 0=单个网址规则, 1=网址组

    @ColumnInfo(name = "target_id") val targetId: Long,  // UrlBlockRule.id 或 UrlRuleGroup.id

    @ColumnInfo(name = "start_time") val startTime: String,  // "22:00"

    @ColumnInfo(name = "end_time") val endTime: String,  // "08:00"

    @ColumnInfo(name = "days_of_week") val daysOfWeek: String,  // "1,2,3,4,5"

    @ColumnInfo(name = "enabled") val enabled: Boolean = true,

    @ColumnInfo(name = "is_locked", defaultValue = "0") val isLocked: Boolean = false,

    @ColumnInfo(name = "lock_end_time", defaultValue = "0") val lockEndTime: Long = 0,

    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "intercept_message") val interceptMessage: String = "",

    // 是否为允许模式（反选）：true = 时间段内允许，其他时间拦截
    @ColumnInfo(name = "is_allow_mode", defaultValue = "0") val isAllowMode: Boolean = false,
) {
    companion object {
        const val TARGET_TYPE_RULE = 0
        const val TARGET_TYPE_GROUP = 1

        fun parseTime(timeStr: String): LocalTime {
            val parts = timeStr.split(":")
            return LocalTime.of(parts[0].toInt(), parts[1].toInt())
        }

        // 预设模板
        data class TimeTemplate(
            val nameRes: Int,
            val startTime: String,
            val endTime: String,
            val daysOfWeek: String,
            val descriptionRes: Int,
        )

        val TEMPLATES = listOf(
            TimeTemplate(R.string.time_template_workday, "09:00", "18:00", "1,2,3,4,5", R.string.time_template_workday_desc),
            TimeTemplate(R.string.time_template_weekend, "00:00", "23:59", "6,7", R.string.time_template_weekend_desc),
            TimeTemplate(R.string.time_template_every_night, "22:00", "08:00", "1,2,3,4,5,6,7", R.string.time_template_every_night_desc),
            TimeTemplate(R.string.time_template_lunch_break, "12:00", "14:00", "1,2,3,4,5", R.string.time_template_lunch_break_desc),
            TimeTemplate(R.string.time_template_all_day, "00:00", "23:59", "1,2,3,4,5,6,7", R.string.time_template_all_day_desc),
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
    fun withDaysOfWeek(days: List<Int>): UrlTimeRule {
        return copy(daysOfWeek = days.joinToString(","))
    }

    /**
     * 检查当前时间是否应该拦截
     */
    fun isActiveNow(): Boolean = isActiveAt(LocalDateTime.now())

    fun isActiveAt(now: LocalDateTime): Boolean {
        if (!enabled) return false

        val currentDayOfWeek = now.dayOfWeek.value  // 1=周一, 7=周日
        val currentTime = now.toLocalTime()
        val start = runCatching { parseTime(startTime) }.getOrNull() ?: return false
        val end = runCatching { parseTime(endTime) }.getOrNull() ?: return false
        if (!Regex("\\d{2}:\\d{2}").matches(startTime) ||
            !Regex("\\d{2}:\\d{2}").matches(endTime)
        ) return false
        val days = getDaysOfWeekList().filter { it in 1..7 }.toSet()
        val inWindow = when {
            start == end -> currentDayOfWeek in days
            end.isAfter(start) && currentDayOfWeek in days -> {
                if (end == LocalTime.of(23, 59)) {
                    !currentTime.isBefore(start)
                } else {
                    !currentTime.isBefore(start) && currentTime.isBefore(end)
                }
            }
            end.isAfter(start) -> false
            else -> {
                val previousDay = now.toLocalDate().minusDays(1).dayOfWeek.value
                (currentDayOfWeek in days && !currentTime.isBefore(start)) ||
                    (previousDay in days && currentTime.isBefore(end))
            }
        }
        return if (isAllowMode) !inWindow else inWindow
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
        // i18n-ignore: legacy fallback or non-display heuristic data
        if (days.isEmpty()) return "未设置"
        // i18n-ignore: legacy fallback or non-display heuristic data
        if (days.size == 7) return "每天"
        // i18n-ignore: legacy fallback or non-display heuristic data
        if (days == listOf(1, 2, 3, 4, 5)) return "工作日"
        // i18n-ignore: legacy fallback or non-display heuristic data
        if (days == listOf(6, 7)) return "周末"

        val dayNames = mapOf(
            // i18n-ignore: legacy fallback or non-display heuristic data
            1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
            // i18n-ignore: legacy fallback or non-display heuristic data
            5 to "周五", 6 to "周六", 7 to "周日"
        )
        return days.mapNotNull { dayNames[it] }.joinToString("、")
    }

    fun formatDaysOfWeek(context: Context): String {
        val days = getDaysOfWeekList()
        if (days.isEmpty()) return context.getString(R.string.days_not_set)
        if (days.size == 7) return context.getString(R.string.days_everyday)
        if (days == listOf(1, 2, 3, 4, 5)) return context.getString(R.string.days_weekdays)
        if (days == listOf(6, 7)) return context.getString(R.string.days_weekend)

        val dayNames = mapOf(
            1 to R.string.day_monday,
            2 to R.string.day_tuesday,
            3 to R.string.day_wednesday,
            4 to R.string.day_thursday,
            5 to R.string.day_friday,
            6 to R.string.day_saturday,
            7 to R.string.day_sunday,
        )
        return days.mapNotNull { dayNames[it]?.let(context::getString) }.joinToString("、")
    }

    /**
     * 格式化时间段显示
     */
    fun formatTimeRange(): String {
        return "$startTime-$endTime"
    }

    @Dao
    interface UrlTimeRuleDao {
        @Query("SELECT * FROM url_time_rule WHERE target_type = :type AND target_id = :id ORDER BY created_at DESC")
        fun queryByTarget(type: Int, id: Long): Flow<List<UrlTimeRule>>

        @Query("SELECT * FROM url_time_rule WHERE enabled = 1")
        fun queryEnabled(): Flow<List<UrlTimeRule>>

        @Query("SELECT * FROM url_time_rule ORDER BY created_at DESC")
        fun queryAll(): Flow<List<UrlTimeRule>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(rule: UrlTimeRule): Long

        @Update
        suspend fun update(rule: UrlTimeRule)

        @Delete
        suspend fun delete(rule: UrlTimeRule)

        @Query("SELECT * FROM url_time_rule WHERE id = :id")
        suspend fun getById(id: Long): UrlTimeRule?

        @Query("DELETE FROM url_time_rule WHERE id = :id")
        suspend fun deleteById(id: Long)

        @Query("DELETE FROM url_time_rule WHERE target_type = :type AND target_id = :id")
        suspend fun deleteByTarget(type: Int, id: Long)

        @Query("UPDATE url_time_rule SET enabled = 1 WHERE enabled = 0")
        suspend fun enableAllDisabled(): Int

        @Query("DELETE FROM url_time_rule")
        suspend fun deleteAll(): Int
    }
}
