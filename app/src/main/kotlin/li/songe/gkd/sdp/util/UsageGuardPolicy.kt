package li.songe.gkd.sdp.util

object UsageGuardPolicy {
    const val SCOPE_SELECTED_ONLY = 0
    const val SCOPE_GLOBAL_EXCEPT_WHITELIST = 1

    const val GRANT_MODE_STRICT = 0
    const val GRANT_MODE_RESUMABLE = 1

    data class AppProfileSnapshot(
        val appId: String,
        val selectedTarget: Boolean,
        val globalWhitelist: Boolean,
        val grantMode: Int,
    )

    data class RequestValidationResult(
        val accepted: Boolean,
        val reasonError: String? = null,
        val durationError: String? = null,
        val tagsError: String? = null,
    )

    fun shouldProtectApp(
        enabled: Boolean,
        scopeMode: Int,
        appProfile: AppProfileSnapshot?,
    ): Boolean {
        if (!enabled) return false
        return when (scopeMode) {
            SCOPE_SELECTED_ONLY -> appProfile?.selectedTarget == true
            SCOPE_GLOBAL_EXCEPT_WHITELIST -> appProfile?.globalWhitelist != true
            else -> false
        }
    }

    fun validateRequest(
        selectedTags: List<String>,
        reason: String,
        minReasonLength: Int,
        requestedDurationMinutes: Int,
    ): RequestValidationResult {
        if (selectedTags.isEmpty()) {
            return RequestValidationResult(
                accepted = false,
                tagsError = "至少选择一个标签",
            )
        }
        if (reason.trim().length < minReasonLength) {
            return RequestValidationResult(
                accepted = false,
                reasonError = "理由长度不足",
            )
        }
        if (requestedDurationMinutes <= 0) {
            return RequestValidationResult(
                accepted = false,
                durationError = "时长必须大于 0",
            )
        }
        return RequestValidationResult(accepted = true)
    }
}
