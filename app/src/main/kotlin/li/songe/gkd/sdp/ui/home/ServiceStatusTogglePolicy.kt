package li.songe.gkd.sdp.ui.home

internal fun canToggleServiceStatusFromUi(
    currentEnabled: Boolean,
    requestedEnabled: Boolean
): Boolean {
    return !(currentEnabled && !requestedEnabled)
}
