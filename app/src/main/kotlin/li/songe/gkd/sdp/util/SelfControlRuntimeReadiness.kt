package li.songe.gkd.sdp.util

object SelfControlRuntimeReadiness {
    enum class Issue {
        None,
        Switching,
        RuntimeUnavailable,
        OverlayPermissionMissing,
    }

    data class Status(
        val mode: AutomatorModeOption?,
        val modeLabelRes: Int?,
        val connected: Boolean,
        val overlayPermission: Boolean,
        val ready: Boolean,
        val issue: Issue,
    )

    fun evaluate(
        mode: AutomatorModeOption?,
        connected: Boolean,
        switching: Boolean,
        overlayPermission: Boolean,
    ): Status {
        val issue = when {
            switching -> Issue.Switching
            !connected || mode == null -> Issue.RuntimeUnavailable
            !overlayPermission -> Issue.OverlayPermissionMissing
            else -> Issue.None
        }
        return Status(
            mode = mode,
            modeLabelRes = mode?.labelRes,
            connected = connected,
            overlayPermission = overlayPermission,
            ready = issue == Issue.None,
            issue = issue,
        )
    }
}
