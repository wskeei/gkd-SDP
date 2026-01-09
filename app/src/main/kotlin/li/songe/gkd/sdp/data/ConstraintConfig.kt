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
@Entity(tableName = "constraint_config")
data class ConstraintConfig(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    
    // 0=Subscription, 1=App, 2=RuleGroup
    @ColumnInfo(name = "target_type") val targetType: Int,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "app_id") val appId: String? = null,
    @ColumnInfo(name = "group_key") val groupKey: Int? = null,
    
    @ColumnInfo(name = "lock_end_time") val lockEndTime: Long = 0,
    @ColumnInfo(name = "enable_intercept") val enableIntercept: Boolean = false
) {
    companion object {
        const val TYPE_SUBSCRIPTION = 0
        const val TYPE_APP = 1
        const val TYPE_RULE_GROUP = 2
    }

    val isLocked: Boolean
        get() = lockEndTime > System.currentTimeMillis()

    @Dao
    interface ConstraintConfigDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(config: ConstraintConfig): Long

        @Query("SELECT * FROM constraint_config")
        fun queryAll(): Flow<List<ConstraintConfig>>

        @Query("DELETE FROM constraint_config WHERE lock_end_time <= :currentTime AND enable_intercept = 0")
        suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis())
        
        @Query("DELETE FROM constraint_config")
        suspend fun deleteAll()
    }
}
