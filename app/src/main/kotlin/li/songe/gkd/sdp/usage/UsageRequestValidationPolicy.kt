package li.songe.gkd.sdp.usage

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
        val tagsError: String? = null,
        val reasonError: String? = null,
        val durationError: String? = null,
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
                tagsError = "至少选择一个标签",
            )
        }
        if (selectedTags.any { !tagNameValid(it) }) {
            return Result(
                accepted = false,
                tagsError = "标签名称不能为空且不能超过 20 个字符",
            )
        }
        if (selectedTags.map(::normalizeTagName).distinctBy(String::lowercase).size != selectedTags.size) {
            return Result(
                accepted = false,
                tagsError = "标签名称不能重复",
            )
        }
        if (reason.trim().length < minReasonLength) {
            return Result(
                accepted = false,
                reasonError = "理由长度不足",
            )
        }
        if (requestedDurationMinutes <= 0) {
            return Result(
                accepted = false,
                durationError = "时长必须大于 0",
            )
        }
        return Result(accepted = true)
    }
}
