package li.songe.gkd.sdp.data

import android.content.Context
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.diagnostics.DiagnosticErrorCategory
import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import li.songe.gkd.sdp.util.crashFolder
import li.songe.gkd.sdp.util.crashTempFolder
import li.songe.gkd.sdp.util.format
import li.songe.gkd.sdp.util.json

@Serializable
data class CrashData(
    val id: Long,
    val mtime: Long,
    val versionCode: Int,
    val versionName: String,
    val errorCode: String,
    val errorCategory: DiagnosticErrorCategory,
    val occurredAtMinute: Long,
    val appFrames: List<String>,
    val count: Int = 1,
) {
    val filename
        get() = "gkd_crash-${mtime.format("yyyyMMdd_HHmm")}-$errorCode.json"
    val summaryText: String
        get() = buildString {
            // i18n-ignore: legacy fallback or non-display heuristic data
            append("错误码：").append(errorCode)
            // i18n-ignore: legacy fallback or non-display heuristic data
            append("\n类别：").append(errorCategory.name)
            // i18n-ignore: legacy fallback or non-display heuristic data
            append("\n时间：").append(occurredAtMinute.format("yyyy-MM-dd HH:mm"))
            if (appFrames.isNotEmpty()) {
                // i18n-ignore: legacy fallback or non-display heuristic data
                append("\n应用内位置：\n")
                append(appFrames.joinToString("\n"))
            }
        }

    fun summaryText(context: Context): String = buildString {
        append(context.getString(R.string.crash_error_code, errorCode))
        append("\n").append(context.getString(R.string.crash_category, errorCategory.name))
        append("\n").append(
            context.getString(
                R.string.crash_time,
                occurredAtMinute.format("yyyy-MM-dd HH:mm"),
            ),
        )
        if (appFrames.isNotEmpty()) {
            append("\n").append(context.getString(R.string.crash_app_frames)).append("\n")
            append(appFrames.joinToString("\n"))
        }
    }

    fun save() {
        val text = json.encodeToString(this)
        crashFolder.resolve(filename).writeText(text)
        crashTempFolder.resolve(filename).writeText(text)
    }

    companion object {
        private const val MINUTE_MILLIS = 60_000L

        fun fromThrowable(
            occurredAtMillis: Long,
            versionCode: Int,
            versionName: String,
            error: Throwable,
        ): CrashData {
            val occurredAtMinute = occurredAtMillis - occurredAtMillis.mod(MINUTE_MILLIS)
            val errorCode = DiagnosticLogger.errorCode(error)
            return CrashData(
                id = errorCode.toLong(radix = 16),
                mtime = occurredAtMinute,
                versionCode = versionCode,
                versionName = versionName,
                errorCode = errorCode,
                errorCategory = DiagnosticLogger.errorCategory(error),
                occurredAtMinute = occurredAtMinute,
                appFrames = DiagnosticLogger.applicationFrames(error),
            )
        }
    }
}
