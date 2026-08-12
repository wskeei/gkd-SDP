package li.songe.gkd.sdp.usage
import androidx.compose.runtime.Immutable

/**
 * Immutable request-form state used by JVM-presenter contracts and by the
 * overlay rendering layer after repository data has been transformed.
 */
@Immutable
data class UsageRequestUiState(
    val appId: String,
    val appName: String,
    val selectedTags: Set<String>,
    val reason: String,
    val requestedDurationMinutes: Int,
    val lastUsageEndedAt: Long?,
    val nowEpochMs: Long,
    val status: Status,
) {
    enum class Status {
        FIRST,
        AVAILABLE,
        MISSING_ACTUAL_END,
        UNAVAILABLE,
    }
}
