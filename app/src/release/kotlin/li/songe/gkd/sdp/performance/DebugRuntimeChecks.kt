package li.songe.gkd.sdp.performance

object DebugRuntimeChecks {
    fun enable(
        listener: DebugRuntimeViolationListener = DebugRuntimeViolationListener { },
        installPolicies: Boolean = true,
    ) = Unit
}
