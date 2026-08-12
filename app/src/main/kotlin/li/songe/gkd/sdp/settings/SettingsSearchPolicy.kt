package li.songe.gkd.sdp.settings

import androidx.compose.runtime.Immutable
import li.songe.gkd.sdp.R

/** The fixed settings groups in display order. */
enum class SettingsGroup {
    RUNTIME_CAPABILITIES(R.string.settings_group_capabilities),
    SELF_CONTROL(R.string.settings_group_self_control),
    RULES_SUBSCRIPTIONS_TRIGGERS(R.string.settings_group_rules),
    DISPLAY_THEME_ACCESSIBILITY(R.string.settings_group_display),
    PRIVACY_DATA(R.string.settings_group_privacy),
    DIAGNOSTICS_DEVELOPER(R.string.settings_group_diagnostics),
    ABOUT_UPDATES(R.string.settings_group_about),
    ;

    val labelRes: Int

    constructor(labelRes: Int) {
        this.labelRes = labelRes
    }
}

sealed interface SettingsTarget {
    data class Route(val routeKey: String) : SettingsTarget
    data class InPage(val anchorId: String) : SettingsTarget
}

@Immutable
data class SettingsEntry(
    val group: SettingsGroup,
    val titleRes: Int,
    val searchTitle: String,
    val titleEn: String = "",
    val keywords: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val id: String = group.name.lowercase(),
    val target: SettingsTarget = SettingsTarget.InPage(id),
    val status: String? = null,
    val lockedUntil: Long? = null,
)

/**
 * Settings search: matches Chinese title, English title, keywords and
 * semantic aliases; results show the group and a status summary; locked
 * entries stay searchable.
 */
object SettingsSearchPolicy {
    fun normalize(value: String): String = value.trim().lowercase()

    fun matches(entry: SettingsEntry, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return normalize(entry.searchTitle).contains(q) ||
            normalize(entry.titleEn).contains(q) ||
            entry.keywords.any { normalize(it).contains(q) } ||
            entry.aliases.any { normalize(it).contains(q) }
    }

    fun search(entries: List<SettingsEntry>, query: String): List<SettingsEntry> =
        entries.filter { matches(it, query) }

    /** Recent items shown for an empty query; capped by design. */
    fun recent(
        entries: List<SettingsEntry>,
        recentIds: List<String>,
        limit: Int = 5,
    ): List<SettingsEntry> =
        recentIds
            .mapNotNull { id -> entries.firstOrNull { it.id == id } }
            .distinctBy { it.id }
            .take(limit.coerceAtLeast(0))

    fun rememberRecent(
        recentIds: List<String>,
        selectedId: String,
        limit: Int = 5,
    ): List<String> =
        (listOf(selectedId) + recentIds.filterNot { it == selectedId })
            .distinct()
            .take(limit.coerceAtLeast(0))
}
