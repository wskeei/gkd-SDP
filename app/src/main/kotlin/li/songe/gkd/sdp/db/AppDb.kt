package li.songe.gkd.sdp.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.A11yEventLog
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.data.ActivityLog
import li.songe.gkd.sdp.data.AppBlockerLock
import li.songe.gkd.sdp.data.AppConfig
import li.songe.gkd.sdp.data.AppGroup
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
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.WechatContact
import li.songe.gkd.sdp.util.dbFolder
import li.songe.gkd.sdp.util.json

@Database(
    version = 24,
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
    ]
)
@TypeConverters(DbConverters::class)
abstract class AppDb : RoomDatabase() {
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
        ).fallbackToDestructiveMigration(false).build()
    }
    val subsItemDao get() = db.subsItemDao()
    val subsConfigDao get() = db.subsConfigDao()
    val snapshotDao get() = db.snapshotDao()
    val actionLogDao get() = db.actionLogDao()
    val categoryConfigDao get() = db.categoryConfigDao()
    val activityLogDao get() = db.activityLogDao()
    val appConfigDao get() = db.appConfigDao()
    val appVisitLogDao get() = db.appVisitLogDao()
    val a11yEventLogDao get() = db.a11yEventLogDao()
    val focusLockDao get() = db.focusLockDao()
    val interceptConfigDao get() = db.interceptConfigDao()
    val constraintConfigDao get() = db.constraintConfigDao()
    val urlBlockRuleDao get() = db.urlBlockRuleDao()
    val browserConfigDao get() = db.browserConfigDao()
    val focusRuleDao get() = db.focusRuleDao()
    val focusSessionDao get() = db.focusSessionDao()
    val appGroupDao get() = db.appGroupDao()
    val blockTimeRuleDao get() = db.blockTimeRuleDao()
    val appBlockerLockDao get() = db.appBlockerLockDao()
    val wechatContactDao get() = db.wechatContactDao()
}
