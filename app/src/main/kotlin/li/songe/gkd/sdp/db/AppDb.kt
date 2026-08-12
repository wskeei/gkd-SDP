package li.songe.gkd.sdp.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.withTransaction
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.backup.gateRoomDao
import li.songe.gkd.sdp.backup.withBackupDataMutationGate
import li.songe.gkd.sdp.data.A11yEventLog
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.data.ActivityLog
import li.songe.gkd.sdp.data.AppBlockerLock
import li.songe.gkd.sdp.data.AppConfig
import li.songe.gkd.sdp.data.AppGroup
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.data.AppVisitLog
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.CategoryConfig
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.data.FocusLock
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession
import li.songe.gkd.sdp.data.InterceptConfig
import li.songe.gkd.sdp.data.Snapshot
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.data.SubsItem
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.WechatContact
import li.songe.gkd.sdp.data.AppInstallLog
import li.songe.gkd.sdp.data.MonitoredApp
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule
import li.songe.gkd.sdp.data.UrlBlockerLock
import li.songe.gkd.sdp.util.dbFolder
import li.songe.gkd.sdp.util.json

@Database(
    version = 33,
    entities = [
        SubsItem::class,
        Snapshot::class,
        SubsConfig::class,
        CategoryConfig::class,
        ActionLog::class,
        ActivityLog::class,
        AppConfig::class,
        AppVisitLog::class,
        A11yEventLog::class,
        FocusLock::class,
        InterceptConfig::class,
        ConstraintConfig::class,
        UrlBlockRule::class,
        BrowserConfig::class,
        FocusRule::class,
        FocusSession::class,
        AppGroup::class,
        BlockTimeRule::class,
        AppBlockerLock::class,
        WechatContact::class,
        AppInstallLog::class,
        MonitoredApp::class,
        UrlRuleGroup::class,
        UrlTimeRule::class,
        UrlBlockerLock::class,
        UsageGuardAppProfile::class,
        UsageGuardTag::class,
        UsageGuardRecord::class,
        SelfControlAttempt::class,
        SelfControlAttemptEvent::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = ActivityLog.ActivityLogV2Spec::class),
        AutoMigration(from = 8, to = 9, spec = ActionLog.ActionLogSpec::class),
        AutoMigration(from = 9, to = 10, spec = Migration9To10Spec::class),
        AutoMigration(from = 10, to = 11, spec = Migration10To11Spec::class),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 23, to = 24),  // 添加 is_allow_mode 字段
        AutoMigration(from = 24, to = 25),  // 添加 shortcut_id 字段
        AutoMigration(from = 25, to = 26),  // 添加 app_install_log 和 monitored_app 表
        AutoMigration(from = 26, to = 27),  // 添加 url_rule_group, url_time_rule 表和 group_id 字段
        AutoMigration(from = 27, to = 28),  // 添加 url_blocker_lock 表
        AutoMigration(from = 28, to = 29),  // UrlBlockRule 添加 is_locked, lock_end_time 字段
        AutoMigration(from = 29, to = 30),  // 添加 usage_guard_* 表
        AutoMigration(from = 30, to = 31),  // 添加 self_control_attempt 表
        AutoMigration(from = 31, to = 32),  // 添加 self_control_attempt_event 表和申请时间索引
    ]
)
@TypeConverters(DbConverters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun digitalSelfDisciplineLockDao(): DigitalSelfDisciplineLockDao
    abstract fun subsItemDao(): SubsItem.SubsItemDao
    abstract fun snapshotDao(): Snapshot.SnapshotDao
    abstract fun subsConfigDao(): SubsConfig.SubsConfigDao
    abstract fun appConfigDao(): AppConfig.AppConfigDao
    abstract fun categoryConfigDao(): CategoryConfig.CategoryConfigDao
    abstract fun actionLogDao(): ActionLog.ActionLogDao
    abstract fun activityLogDao(): ActivityLog.ActivityLogDao
    abstract fun appVisitLogDao(): AppVisitLog.AppLogDao
    abstract fun a11yEventLogDao(): A11yEventLog.A11yEventLogDao
    abstract fun focusLockDao(): FocusLock.FocusLockDao
    abstract fun interceptConfigDao(): InterceptConfig.InterceptConfigDao
    abstract fun constraintConfigDao(): ConstraintConfig.ConstraintConfigDao
    abstract fun urlBlockRuleDao(): UrlBlockRule.UrlBlockRuleDao
    abstract fun browserConfigDao(): BrowserConfig.BrowserConfigDao
    abstract fun focusRuleDao(): FocusRule.FocusRuleDao
    abstract fun focusSessionDao(): FocusSession.FocusSessionDao
    abstract fun appGroupDao(): AppGroup.AppGroupDao
    abstract fun blockTimeRuleDao(): BlockTimeRule.BlockTimeRuleDao
    abstract fun appBlockerLockDao(): AppBlockerLock.AppBlockerLockDao
    abstract fun wechatContactDao(): WechatContact.WechatContactDao
    abstract fun appInstallLogDao(): AppInstallLog.AppInstallLogDao
    abstract fun monitoredAppDao(): MonitoredApp.MonitoredAppDao
    abstract fun urlRuleGroupDao(): UrlRuleGroup.UrlRuleGroupDao
    abstract fun urlTimeRuleDao(): UrlTimeRule.UrlTimeRuleDao
    abstract fun urlBlockerLockDao(): UrlBlockerLock.UrlBlockerLockDao
    abstract fun usageGuardAppProfileDao(): UsageGuardAppProfile.UsageGuardAppProfileDao
    abstract fun usageGuardTagDao(): UsageGuardTag.UsageGuardTagDao
    abstract fun usageGuardRecordDao(): UsageGuardRecord.UsageGuardRecordDao
    abstract fun selfControlAttemptDao(): SelfControlAttempt.SelfControlAttemptDao
}

/**
 * Explicitly adds the nullable observability/rhythm columns for the 32 -> 33 upgrade.
 *
 * Room 2.8.4's KSP auto-migration processor cannot resolve this combination of newly added
 * nullable columns and the existing entity graph, so keep the same non-destructive schema change
 * explicit and executable at runtime.
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE action_log ADD COLUMN outcome INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE action_log ADD COLUMN matched_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE action_log ADD COLUMN subs_name_snapshot TEXT")
        db.execSQL("ALTER TABLE action_log ADD COLUMN group_name_snapshot TEXT")
        db.execSQL("ALTER TABLE action_log ADD COLUMN rule_name_snapshot TEXT")
        db.execSQL("ALTER TABLE usage_guard_record ADD COLUMN last_usage_ended_at INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE usage_guard_record ADD COLUMN request_gap_ms INTEGER DEFAULT NULL")
    }
}

@RenameColumn(
    tableName = "subs_config",
    fromColumnName = "subs_item_id",
    toColumnName = "subs_id"
)
@RenameColumn(
    tableName = "category_config",
    fromColumnName = "subs_item_id",
    toColumnName = "subs_id"
)
class Migration9To10Spec : AutoMigrationSpec

@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_name"
)
@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_version_code"
)
@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_version_name"
)
class Migration10To11Spec : AutoMigrationSpec

@Suppress("unused")
class DbConverters {
    @TypeConverter
    fun fromListStringToString(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

object DbSet {
    private val db by lazy {
        Room.databaseBuilder(
            app,
            AppDb::class.java,
            dbFolder.resolve("gkd.db").absolutePath
        ).addMigrations(MIGRATION_32_33)
            .fallbackToDestructiveMigration(false)
            .build()
    }
    val subsItemDao get() = gateRoomDao(SubsItem.SubsItemDao::class.java, db.subsItemDao())
    val digitalSelfDisciplineLockDao get() = gateRoomDao(DigitalSelfDisciplineLockDao::class.java, db.digitalSelfDisciplineLockDao())
    val subsConfigDao get() = gateRoomDao(SubsConfig.SubsConfigDao::class.java, db.subsConfigDao())
    val snapshotDao get() = gateRoomDao(Snapshot.SnapshotDao::class.java, db.snapshotDao())
    val actionLogDao get() = gateRoomDao(ActionLog.ActionLogDao::class.java, db.actionLogDao())
    val categoryConfigDao get() = gateRoomDao(CategoryConfig.CategoryConfigDao::class.java, db.categoryConfigDao())
    val activityLogDao get() = gateRoomDao(ActivityLog.ActivityLogDao::class.java, db.activityLogDao())
    val appConfigDao get() = gateRoomDao(AppConfig.AppConfigDao::class.java, db.appConfigDao())
    val appVisitLogDao get() = gateRoomDao(AppVisitLog.AppLogDao::class.java, db.appVisitLogDao())
    val a11yEventLogDao get() = gateRoomDao(A11yEventLog.A11yEventLogDao::class.java, db.a11yEventLogDao())
    val focusLockDao get() = gateRoomDao(FocusLock.FocusLockDao::class.java, db.focusLockDao())
    val interceptConfigDao get() = gateRoomDao(InterceptConfig.InterceptConfigDao::class.java, db.interceptConfigDao())
    val constraintConfigDao get() = gateRoomDao(ConstraintConfig.ConstraintConfigDao::class.java, db.constraintConfigDao())
    val urlBlockRuleDao get() = gateRoomDao(UrlBlockRule.UrlBlockRuleDao::class.java, db.urlBlockRuleDao())
    val browserConfigDao get() = gateRoomDao(BrowserConfig.BrowserConfigDao::class.java, db.browserConfigDao())
    val focusRuleDao get() = gateRoomDao(FocusRule.FocusRuleDao::class.java, db.focusRuleDao())
    val focusSessionDao get() = gateRoomDao(FocusSession.FocusSessionDao::class.java, db.focusSessionDao())
    val appGroupDao get() = gateRoomDao(AppGroup.AppGroupDao::class.java, db.appGroupDao())
    val blockTimeRuleDao get() = gateRoomDao(BlockTimeRule.BlockTimeRuleDao::class.java, db.blockTimeRuleDao())
    val appBlockerLockDao get() = gateRoomDao(AppBlockerLock.AppBlockerLockDao::class.java, db.appBlockerLockDao())
    val wechatContactDao get() = gateRoomDao(WechatContact.WechatContactDao::class.java, db.wechatContactDao())
    val appInstallLogDao get() = gateRoomDao(AppInstallLog.AppInstallLogDao::class.java, db.appInstallLogDao())
    val monitoredAppDao get() = gateRoomDao(MonitoredApp.MonitoredAppDao::class.java, db.monitoredAppDao())
    val urlRuleGroupDao get() = gateRoomDao(UrlRuleGroup.UrlRuleGroupDao::class.java, db.urlRuleGroupDao())
    val urlTimeRuleDao get() = gateRoomDao(UrlTimeRule.UrlTimeRuleDao::class.java, db.urlTimeRuleDao())
    val urlBlockerLockDao get() = gateRoomDao(UrlBlockerLock.UrlBlockerLockDao::class.java, db.urlBlockerLockDao())
    val usageGuardAppProfileDao get() = gateRoomDao(UsageGuardAppProfile.UsageGuardAppProfileDao::class.java, db.usageGuardAppProfileDao())
    val usageGuardTagDao get() = gateRoomDao(UsageGuardTag.UsageGuardTagDao::class.java, db.usageGuardTagDao())
    val usageGuardRecordDao get() = gateRoomDao(UsageGuardRecord.UsageGuardRecordDao::class.java, db.usageGuardRecordDao())
    val selfControlAttemptDao get() = gateRoomDao(SelfControlAttempt.SelfControlAttemptDao::class.java, db.selfControlAttemptDao())

    suspend fun <T> withTransaction(block: suspend () -> T): T =
        withBackupDataMutationGate { db.withTransaction(block) }

    suspend fun <T> withRawTransaction(
        block: suspend (SupportSQLiteDatabase) -> T,
    ): T = withBackupDataMutationGate {
        db.withTransaction {
            block(db.openHelper.writableDatabase)
        }
    }
}
