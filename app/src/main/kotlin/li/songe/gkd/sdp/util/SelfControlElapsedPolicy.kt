package li.songe.gkd.sdp.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import li.songe.gkd.sdp.R

object SelfControlElapsedPolicy {
    enum class Context {
        USAGE_REQUEST,
        APP_OPEN_ATTEMPT,
        RULE_TRIGGER,
    }

    data class Copy(
        val titleRes: Int,
        val previousTimeLabelRes: Int,
        val firstTimeLabelRes: Int,
        val noHistoryTextRes: Int,
        val firstSupportingTextRes: Int,
        val supportingTextRes: Int,
    )

    sealed interface ElapsedState {
        data object Loading : ElapsedState

        data object NoHistory : ElapsedState

        data object Unavailable : ElapsedState

        /** A previous request exists, but no trustworthy real-use end is available. */
        data object MissingActualEnd : ElapsedState

        data class Running(
            val anchorAtEpochMs: Long,
            val firstOccurrence: Boolean,
        ) : ElapsedState
    }

    private val absoluteFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun formatElapsed(anchorAtEpochMs: Long, nowEpochMs: Long): String {
        val (days, clock) = elapsedParts(anchorAtEpochMs, nowEpochMs)
        // i18n-ignore: legacy fallback or non-display heuristic data
        return if (days > 0L) "${days}天 $clock" else clock
    }

    fun elapsedParts(anchorAtEpochMs: Long, nowEpochMs: Long): Pair<Long, String> {
        val totalSeconds = ((nowEpochMs - anchorAtEpochMs).coerceAtLeast(0L)) / 1_000L
        val days = totalSeconds / (24 * 60 * 60)
        val hours = (totalSeconds / (60 * 60)) % 24
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60
        val clock = "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        return days to clock
    }

    fun formatAbsolute(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return absoluteFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zoneId))
    }

    fun copyFor(context: Context): Copy {
        return when (context) {
            Context.USAGE_REQUEST -> Copy(
                titleRes = R.string.elapsed_title_since_last_use,
                previousTimeLabelRes = R.string.elapsed_previous_use,
                firstTimeLabelRes = R.string.elapsed_current_request,
                noHistoryTextRes = R.string.elapsed_no_history,
                firstSupportingTextRes = R.string.elapsed_first_support_use,
                supportingTextRes = R.string.elapsed_support_use,
            )

            Context.APP_OPEN_ATTEMPT -> Copy(
                titleRes = R.string.elapsed_title_since_last_attempt,
                previousTimeLabelRes = R.string.elapsed_previous_attempt,
                firstTimeLabelRes = R.string.elapsed_current_attempt,
                noHistoryTextRes = R.string.elapsed_no_history_attempt,
                firstSupportingTextRes = R.string.elapsed_first_support_attempt,
                supportingTextRes = R.string.elapsed_support_attempt,
            )

            Context.RULE_TRIGGER -> Copy(
                titleRes = R.string.elapsed_title_since_last_trigger,
                previousTimeLabelRes = R.string.elapsed_previous_trigger,
                firstTimeLabelRes = R.string.elapsed_current_trigger,
                noHistoryTextRes = R.string.elapsed_no_history_trigger,
                firstSupportingTextRes = R.string.elapsed_first_support_trigger,
                supportingTextRes = R.string.elapsed_support_trigger,
            )
        }
    }

    fun stateForAttempt(
        previousOccurredAt: Long?,
        currentOccurredAt: Long,
    ): ElapsedState {
        if (previousOccurredAt != null && previousOccurredAt > currentOccurredAt) {
            return ElapsedState.Unavailable
        }
        return ElapsedState.Running(
            anchorAtEpochMs = previousOccurredAt ?: currentOccurredAt,
            firstOccurrence = previousOccurredAt == null,
        )
    }

    fun stateForUsageRequest(previousRequestedAt: Long?): ElapsedState {
        return previousRequestedAt?.let {
            ElapsedState.Running(
                anchorAtEpochMs = it,
                firstOccurrence = false,
            )
        } ?: ElapsedState.NoHistory
    }

    fun appBlockerEventKey(packageName: String): String {
        return "app_blocker:$packageName"
    }

    fun selectorInterceptEventKey(
        subsId: Long,
        appId: String,
        groupType: Int,
        groupKey: Int,
        ruleIdentity: String,
    ): String {
        return "selector_intercept:v2:$subsId:$appId:$groupType:$groupKey:$ruleIdentity"
    }

    fun urlInterceptEventKey(ruleId: Long): String {
        return "url_intercept:$ruleId"
    }
}
