package li.songe.gkd.sdp.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

object SelfControlElapsedPolicy {
    enum class Context {
        USAGE_REQUEST,
        APP_OPEN_ATTEMPT,
        RULE_TRIGGER,
    }

    data class Copy(
        val title: String,
        val previousTimeLabel: String,
        val firstTimeLabel: String,
        val noHistoryText: String,
        val firstSupportingText: String,
        val supportingText: String,
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
        .ofPattern("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA)

    fun formatElapsed(anchorAtEpochMs: Long, nowEpochMs: Long): String {
        val totalSeconds = ((nowEpochMs - anchorAtEpochMs).coerceAtLeast(0L)) / 1_000L
        val days = totalSeconds / (24 * 60 * 60)
        val hours = (totalSeconds / (60 * 60)) % 24
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60
        val clock = "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        return if (days > 0L) "${days}天 $clock" else clock
    }

    fun formatAbsolute(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return absoluteFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zoneId))
    }

    fun copyFor(context: Context): Copy {
        return when (context) {
            Context.USAGE_REQUEST -> Copy(
                title = app.getString(R.string.s_05dc33f232),
                previousTimeLabel = "上次结束使用",
                firstTimeLabel = "本次申请",
                noHistoryText = "此前没有成功的使用申请",
                firstSupportingText = "完成一次使用并离开后开始统计；取消申请不会重置这段时间。",
                supportingText = app.getString(R.string.s_2c2d2c052f),
            )

            Context.APP_OPEN_ATTEMPT -> Copy(
                title = app.getString(R.string.s_df643eccb1),
                previousTimeLabel = "上次尝试",
                firstTimeLabel = "本次尝试",
                noHistoryText = "首次记录到这个应用的拦截",
                firstSupportingText = "从本次尝试开始累计下一段间隔。",
                supportingText = app.getString(R.string.s_9cedae8b91),
            )

            Context.RULE_TRIGGER -> Copy(
                title = app.getString(R.string.s_7229e6e59a),
                previousTimeLabel = "上次触发",
                firstTimeLabel = "本次触发",
                noHistoryText = "首次记录到这条拦截规则",
                firstSupportingText = "从本次触发开始累计下一段间隔。",
                supportingText = app.getString(R.string.s_57aa860b2b),
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
