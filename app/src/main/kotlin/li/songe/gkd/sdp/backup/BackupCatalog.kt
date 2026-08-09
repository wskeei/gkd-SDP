package li.songe.gkd.sdp.backup

data class BackupTableDescriptor(
    val tableName: String,
    val primaryKeyColumns: List<String>,
) {
    val objectId: String = "table-$tableName"
}

data class BackupCategory(
    val id: String,
    val defaultEnabled: Boolean,
    val sensitive: Boolean = false,
    val tables: List<BackupTableDescriptor> = emptyList(),
)

object BackupCatalog {
    val categories = listOf(
        BackupCategory(
            id = "settings",
            defaultEnabled = true,
        ),
        BackupCategory(
            id = "subscriptions",
            defaultEnabled = true,
            tables = tables(
                "subs_item" to "id",
                "subs_config" to "id",
                "category_config" to "id",
                "app_config" to "id",
            ),
        ),
        BackupCategory(
            id = "self_control_config",
            defaultEnabled = true,
            tables = tables(
                "focus_lock" to "id",
                "intercept_config" to "id",
                "constraint_config" to "id",
                "focus_rule" to "id",
                "app_group" to "id",
                "block_time_rule" to "id",
                "app_blocker_lock" to "id",
                "url_block_rule" to "id",
                "browser_config" to "package_name",
                "url_rule_group" to "id",
                "url_time_rule" to "id",
                "url_blocker_lock" to "id",
                "usage_guard_app_profile" to "app_id",
                "usage_guard_tag" to "id",
                "monitored_app" to "package_name",
            ),
        ),
        BackupCategory(
            id = "self_control_history",
            defaultEnabled = true,
            tables = tables(
                "focus_session" to "id",
                "usage_guard_record" to "id",
                "self_control_attempt" to "event_key",
                "self_control_attempt_event" to "id",
                "app_install_log" to "id",
            ),
        ),
        BackupCategory(
            id = "upstream_history",
            defaultEnabled = true,
            tables = tables(
                "action_log" to "id",
                "activity_log_v2" to "id",
                "app_visit_log" to "id",
            ),
        ),
        BackupCategory(
            id = "sensitive_optional",
            defaultEnabled = false,
            sensitive = true,
            tables = tables(
                "snapshot" to "id",
                "a11y_event_log" to "id",
                "wechat_contact" to "wechat_id",
            ),
        ),
    )

    val defaultCategoryIds: Set<String> = categories
        .filter(BackupCategory::defaultEnabled)
        .mapTo(linkedSetOf(), BackupCategory::id)

    fun category(id: String): BackupCategory? = categories.firstOrNull { it.id == id }

    fun canonicalObjects(objects: Collection<BackupTableDescriptor>): List<BackupTableDescriptor> =
        objects.sortedBy(BackupTableDescriptor::objectId)

    fun isExcludedPath(rawPath: String): Boolean {
        val path = rawPath.replace('\\', '/').trim('/').lowercase()
        if (path.isBlank() || path.split('/').any { it == ".." }) return true
        val segments = path.split('/')
        return segments.any { it in EXCLUDED_SEGMENTS } ||
            segments.last() in EXCLUDED_FILENAMES ||
            path.contains("remote-session")
    }

    private fun tables(vararg values: Pair<String, String>): List<BackupTableDescriptor> =
        values.map { (table, primaryKey) ->
            BackupTableDescriptor(table, listOf(primaryKey))
        }

    private val EXCLUDED_SEGMENTS = setOf(
        "log",
        "crash",
        "cache",
        "shared",
        "private-store",
        "commands",
        "sh",
    )
    private val EXCLUDED_FILENAMES = setOf(
        "diagnostic-salt.bin",
        "github_cookie.txt",
        "accessibility_guard_session.json",
        "expose.sh",
    )
}
