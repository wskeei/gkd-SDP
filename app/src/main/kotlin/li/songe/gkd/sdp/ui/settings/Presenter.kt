@file:JvmName("SettingsPresenter")

package li.songe.gkd.sdp.ui.home

/** Form policy shared by the backup route and its editor. */
internal fun settingsPasswordIsValid(password: String): Boolean =
    password.codePointCount(0, password.length) >= 12
