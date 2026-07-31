package li.songe.gkd.sdp.util

/**
 * Decides what the home-page service switch gesture means.
 *
 * The switch is a status/control affordance rather than a second way to
 * revoke the system accessibility permission. Turning it off therefore only
 * explains how to use Android settings; turning it on either opens the
 * authorization flow or issues an explicit start/repair command.
 */
object HomeA11yServiceTogglePolicy {
    enum class Action {
        OPEN_AUTHORIZATION,
        START_OR_REPAIR,
        EXPLAIN_SYSTEM_SETTINGS,
    }

    fun action(
        requestedEnabled: Boolean,
        writeSecureSettings: Boolean,
    ): Action = when {
        !requestedEnabled -> Action.EXPLAIN_SYSTEM_SETTINGS
        !writeSecureSettings -> Action.OPEN_AUTHORIZATION
        else -> Action.START_OR_REPAIR
    }
}
