package li.songe.gkd.sdp.ui

object AppBlockerEditorPolicy {
    fun resolveGroupApps(
        existingApps: List<String>,
        pickedApps: List<String>,
        appendOnly: Boolean,
    ): List<String> {
        val normalizedPickedApps = pickedApps.distinct()
        return if (appendOnly) {
            (existingApps + normalizedPickedApps).distinct()
        } else {
            normalizedPickedApps
        }
    }

    fun canDragSheet(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ): Boolean {
        return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
    }
}
