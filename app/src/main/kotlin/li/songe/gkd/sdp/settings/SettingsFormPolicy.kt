package li.songe.gkd.sdp.settings

import li.songe.gkd.sdp.store.SettingsStore

/**
 * Form-level save rules. The notification text form compares the title with
 * the title value and the text with the text value independently; both
 * current values are saved when either changed, and nothing is written when
 * neither changed.
 */
object SettingsFormPolicy {
    fun notificationTextChanged(
        store: SettingsStore,
        titleValue: String,
        textValue: String,
    ): Boolean =
        store.customNotifTitle != titleValue || store.customNotifText != textValue

    fun notificationTextUpdate(
        store: SettingsStore,
        titleValue: String,
        textValue: String,
    ): SettingsStore = store.copy(
        customNotifTitle = titleValue,
        customNotifText = textValue,
    )
}
