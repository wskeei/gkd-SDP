package li.songe.gkd.sdp.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import li.songe.gkd.sdp.R

class UsageRequestValidationPolicyTest {
    @Test
    fun tagNamesAreTrimmedAndBoundedToTwentyCodePoints() {
        assertTrue(UsageRequestValidationPolicy.tagNameValid(" 查资料 "))
        assertEquals("查资料", UsageRequestValidationPolicy.normalizeTagName(" 查资料 "))
        assertFalse(UsageRequestValidationPolicy.tagNameValid(""))
        assertFalse(UsageRequestValidationPolicy.tagNameValid("a".repeat(21)))
        assertTrue(UsageRequestValidationPolicy.tagNameValid("a".repeat(20)))
    }

    @Test
    fun duplicateTagsAreRejectedCaseInsensitively() {
        assertTrue(UsageRequestValidationPolicy.hasDuplicateTag(listOf("工作"), "工作"))
        assertTrue(UsageRequestValidationPolicy.hasDuplicateTag(listOf("工作"), " 工作 "))
        assertTrue(UsageRequestValidationPolicy.hasDuplicateTag(listOf("Work"), "work"))
        assertFalse(UsageRequestValidationPolicy.hasDuplicateTag(listOf("工作"), "生活"))
    }

    @Test
    fun validationRequiresTagReasonAndPositiveDuration() {
        val invalid = UsageRequestValidationPolicy.validate(
            selectedTags = emptyList(),
            reason = "太短",
            minReasonLength = 6,
            requestedDurationMinutes = 0,
        )
        assertFalse(invalid.accepted)
        assertTrue(invalid.tagsErrorRes != null)
        assertTrue(invalid.reasonErrorRes == null)

        val valid = UsageRequestValidationPolicy.validate(
            selectedTags = listOf("查资料"),
            reason = "查资料准备今晚的演讲",
            minReasonLength = 6,
            requestedDurationMinutes = 15,
        )
        assertTrue(valid.accepted)
    }

    @Test
    fun duplicateSelectedTagsFailClosed() {
        val result = UsageRequestValidationPolicy.validate(
            selectedTags = listOf("工作", "工作"),
            reason = "足够长的理由文本",
            minReasonLength = 6,
            requestedDurationMinutes = 10,
        )
        assertFalse(result.accepted)
        assertEquals(R.string.usage_request_error_tag_duplicate, result.tagsErrorRes)
    }

    @Test
    fun eachValidationFailureReturnsItsOwnErrorField() {
        val tooLong = UsageRequestValidationPolicy.validate(
            selectedTags = listOf("a".repeat(21)),
            reason = "足够长的理由文本",
            minReasonLength = 6,
            requestedDurationMinutes = 10,
        )
        assertFalse(tooLong.accepted)
        assertTrue(tooLong.tagsErrorRes != null)

        val shortReason = UsageRequestValidationPolicy.validate(
            selectedTags = listOf("工作"),
            reason = "短",
            minReasonLength = 6,
            requestedDurationMinutes = 10,
        )
        assertFalse(shortReason.accepted)
        assertEquals(R.string.usage_request_error_reason_short, shortReason.reasonErrorRes)

        val badDuration = UsageRequestValidationPolicy.validate(
            selectedTags = listOf("工作"),
            reason = "足够长的理由文本",
            minReasonLength = 6,
            requestedDurationMinutes = 0,
        )
        assertFalse(badDuration.accepted)
        assertEquals(R.string.usage_request_error_duration, badDuration.durationErrorRes)
    }
}
