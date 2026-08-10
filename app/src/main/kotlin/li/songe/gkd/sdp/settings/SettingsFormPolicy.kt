package li.songe.gkd.sdp.settings

/**
 * Form-level save rules. The notification text form compares the title with
 * the title value and the text with the text value independently; both
 * current values are saved when either changed, and nothing is written when
 * neither changed.
 */
object SettingsFormPolicy {
    fun notificationTextChanged(
        currentTitle: String,
        currentText: String,
        titleValue: String,
        textValue: String,
    ): Boolean = currentTitle != titleValue || currentText != textValue
}
