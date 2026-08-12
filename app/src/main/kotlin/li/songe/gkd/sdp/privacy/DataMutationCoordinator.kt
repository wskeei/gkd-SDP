package li.songe.gkd.sdp.privacy

import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy

object DataMutationCoordinator {
    // i18n-ignore: legacy fallback or non-display heuristic data
    const val DELETE_ALL_PHRASE = "删除全部数据"

    fun configurationDeletionAllowed(hasActiveSession: Boolean): Boolean =
        !hasActiveSession

    fun deleteAllAllowed(hasActiveSession: Boolean): Boolean =
        !hasActiveSession

    fun validatesDeleteAllPhrase(input: String): Boolean =
        input.trim() == DELETE_ALL_PHRASE

    fun validatesDeleteAllPhrase(input: String, expectedPhrase: String): Boolean =
        input.trim() == expectedPhrase

    fun blockReason(hasActiveSession: Boolean): String? =
        if (hasActiveSession) {
            // i18n-ignore: legacy fallback or non-display heuristic data
            "存在活动使用申请、专注会话或锁定保护，配置与全部删除已禁用。"
        } else {
            null
        }

    fun resetSelfControlConfig(current: SettingsStore): SettingsStore =
        current.copy(
            autoReenableIntervalMinutes = 0,
            autoReenableIntervalChangedAt = 0L,
            autoReenableNextEnforceAt = 0L,
            autoReenableDailyDisableLimit = 1,
            autoReenableDailyDisableUsed = 0,
            autoReenableDailyDisableDayStartAt = 0L,
            usageGuardEnabled = false,
            usageGuardScopeMode = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
            usageGuardDefaultGrantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
            usageGuardMinReasonLength = 8,
            usageGuardDurationOptionsMinutes = UsageGuardUiStatePolicy.defaultDurationOptions,
            accessibilityGuardEnabled = false,
            accessibilityGuardAutoReenableArmed = false,
        )
}
