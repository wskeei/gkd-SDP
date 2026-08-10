package li.songe.gkd.sdp.settings

/** The fixed settings groups in display order. */
enum class SettingsGroup {
    RUNTIME_CAPABILITIES,
    SELF_CONTROL,
    RULES_SUBSCRIPTIONS_TRIGGERS,
    DISPLAY_THEME_ACCESSIBILITY,
    PRIVACY_DATA,
    DIAGNOSTICS_DEVELOPER,
    ABOUT_UPDATES,
}

data class SettingsEntry(
    val group: SettingsGroup,
    val title: String,
    val titleEn: String = title,
    val keywords: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
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
        return normalize(entry.title).contains(q) ||
            normalize(entry.titleEn).contains(q) ||
            entry.keywords.any { normalize(it).contains(q) } ||
            entry.aliases.any { normalize(it).contains(q) }
    }

    fun search(entries: List<SettingsEntry>, query: String): List<SettingsEntry> =
        entries.filter { matches(it, query) }

    /** Recent items shown for an empty query; capped by design. */
    fun recent(entries: List<SettingsEntry>, limit: Int = 5): List<SettingsEntry> =
        entries.take(limit.coerceAtLeast(0))
}
