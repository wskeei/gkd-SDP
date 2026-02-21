package li.songe.gkd.sdp.util

import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.store.storeFlow

data class DisableAttemptResult(
    val allowed: Boolean,
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val dayStartAt: Long
)

data class DisableQuotaState(
    val used: Int,
    val limit: Int,
    val dayStartAt: Long
)

object AutoReenableDisableGuard {
    fun evaluateDisableAttempt(
        limit: Int,
        used: Int,
        dayStartAt: Long,
        now: Long,
        consume: Boolean,
    ): Pair<DisableAttemptResult, DisableQuotaState> {
        val normalizedLimit = AutoReenablePolicy.normalizeDailyDisableLimit(limit)
        val currentDayStartAt = AutoReenablePolicy.localDayStartEpochMs(now)
        val normalizedUsed = if (AutoReenablePolicy.shouldResetDailyCounter(dayStartAt, now)) {
            0
        } else {
            used.coerceIn(0, normalizedLimit)
        }

        val canConsume = normalizedUsed < normalizedLimit
        val nextUsed = if (consume && canConsume) normalizedUsed + 1 else normalizedUsed
        val remaining = (normalizedLimit - nextUsed).coerceAtLeast(0)

        return DisableAttemptResult(
            allowed = !consume || canConsume,
            used = nextUsed,
            limit = normalizedLimit,
            remaining = remaining,
            dayStartAt = currentDayStartAt
        ) to DisableQuotaState(
            used = nextUsed,
            limit = normalizedLimit,
            dayStartAt = currentDayStartAt
        )
    }

    fun inspect(now: Long = System.currentTimeMillis()): DisableAttemptResult {
        val settings = storeFlow.value
        val (result, normalizedState) = evaluateDisableAttempt(
            limit = settings.autoReenableDailyDisableLimit,
            used = settings.autoReenableDailyDisableUsed,
            dayStartAt = settings.autoReenableDailyDisableDayStartAt,
            now = now,
            consume = false
        )
        persistNormalizedState(settings, normalizedState)
        return result
    }

    fun tryConsumeForDisable(now: Long = System.currentTimeMillis()): DisableAttemptResult {
        var attempt = DisableAttemptResult(
            allowed = false,
            used = 0,
            limit = AutoReenablePolicy.MIN_DAILY_DISABLE_LIMIT,
            remaining = 0,
            dayStartAt = 0L
        )
        storeFlow.update { settings ->
            val (result, nextState) = evaluateDisableAttempt(
                limit = settings.autoReenableDailyDisableLimit,
                used = settings.autoReenableDailyDisableUsed,
                dayStartAt = settings.autoReenableDailyDisableDayStartAt,
                now = now,
                consume = true
            )
            attempt = result
            settings.copy(
                autoReenableDailyDisableLimit = nextState.limit,
                autoReenableDailyDisableUsed = nextState.used,
                autoReenableDailyDisableDayStartAt = nextState.dayStartAt
            )
        }
        return attempt
    }

    private fun persistNormalizedState(
        settings: SettingsStore,
        state: DisableQuotaState
    ) {
        if (
            settings.autoReenableDailyDisableLimit == state.limit &&
            settings.autoReenableDailyDisableUsed == state.used &&
            settings.autoReenableDailyDisableDayStartAt == state.dayStartAt
        ) {
            return
        }
        storeFlow.update {
            it.copy(
                autoReenableDailyDisableLimit = state.limit,
                autoReenableDailyDisableUsed = state.used,
                autoReenableDailyDisableDayStartAt = state.dayStartAt
            )
        }
    }
}
