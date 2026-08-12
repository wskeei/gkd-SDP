package li.songe.gkd.sdp.usage

import li.songe.gkd.sdp.R

/**
 * Pure validation for the usage-request form.
 *
 * Tag names are trimmed, 1-20 Unicode code points, and duplicate names are
 * rejected before the UI writes a record. Reason and duration rules remain
 * shared with [li.songe.gkd.sdp.util.UsageGuardPolicy].
 */
object UsageRequestValidationPolicy {
    const val MAX_TAG_NAME_CODE_POINTS = 20

    data class Result(
        val accepted: Boolean,
        val tagsErrorRes: Int? = null,
        val reasonErrorRes: Int? = null,
        val durationErrorRes: Int? = null,
    )

    fun normalizeTagName(value: String): String = value.trim()

    fun tagNameValid(value: String): Boolean {
        val normalized = normalizeTagName(value)
        if (normalized.isEmpty()) return false
        return normalized.codePointCount(0, normalized.length) <= MAX_TAG_NAME_CODE_POINTS
    }

    fun hasDuplicateTag(
        existing: List<String>,
        candidate: String,
    ): Boolean {
        val normalized = normalizeTagName(candidate)
        if (normalized.isEmpty()) return false
        return existing.any { it.equals(normalized, ignoreCase = true) }
    }

    fun validate(
        selectedTags: List<String>,
        reason: String,
        minReasonLength: Int,
        requestedDurationMinutes: Int,
    ): Result {
        if (selectedTags.isEmpty()) {
            return Result(
                accepted = false,
                tagsErrorRes = R.string.usage_request_error_no_tags,
            )
        }
        if (selectedTags.any { !tagNameValid(it) }) {
            return Result(
                accepted = false,
                tagsErrorRes = R.string.usage_request_error_tag_invalid,
            )
        }
        if (selectedTags.map(::normalizeTagName).distinctBy(String::lowercase).size != selectedTags.size) {
            return Result(
                accepted = false,
                tagsErrorRes = R.string.usage_request_error_tag_duplicate,
            )
        }
        if (reason.trim().length < minReasonLength) {
            return Result(
                accepted = false,
                reasonErrorRes = R.string.usage_request_error_reason_short,
            )
        }
        if (requestedDurationMinutes <= 0) {
            return Result(
                accepted = false,
                durationErrorRes = R.string.usage_request_error_duration,
            )
        }
        return Result(accepted = true)
    }
}
