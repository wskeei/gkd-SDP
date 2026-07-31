package li.songe.gkd.sdp.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                title = "距离上次申请",
                previousTimeLabel = "上次申请",
                firstTimeLabel = "本次申请",
                noHistoryText = "此前没有提交过使用申请",
                firstSupportingText = "这次选择“取消”不会创建申请记录。",
                supportingText = "如果这次选择“取消”，这段未申请时间会继续延长。",
            )

            Context.APP_OPEN_ATTEMPT -> Copy(
                title = "距离上次尝试打开",
                previousTimeLabel = "上次尝试",
                firstTimeLabel = "本次尝试",
                noHistoryText = "首次记录到这个应用的拦截",
                firstSupportingText = "从本次尝试开始累计下一段间隔。",
                supportingText = "退出后，下一段间隔会从本次尝试继续累计。",
            )

            Context.RULE_TRIGGER -> Copy(
                title = "距离上次触发拦截",
                previousTimeLabel = "上次触发",
                firstTimeLabel = "本次触发",
                noHistoryText = "首次记录到这条拦截规则",
                firstSupportingText = "从本次触发开始累计下一段间隔。",
                supportingText = "退出后，下一段间隔会从本次触发继续累计。",
            )
        }
    }

    fun stateForAttempt(
        previousOccurredAt: Long?,
        currentOccurredAt: Long,
    ): ElapsedState {
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
}
