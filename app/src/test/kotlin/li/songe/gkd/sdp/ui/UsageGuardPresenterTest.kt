package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.UsageGuardRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UsageGuardPresenterTest {
    private fun record(endReason: Int) = UsageGuardRecord(
        appId = "com.example",
        appName = "Example",
        tagNames = emptyList(),
        reasonText = "test",
        requestedDurationMinutes = 15,
        requestedAt = 1_000L,
        grantedAt = 1_100L,
        expiresAt = 2_000L,
        endedAt = 1_500L,
        endReason = endReason,
    )

    @Test
    fun endStateTextCoversEveryKnownReason() {
        assertEquals(R.string.usage_guard_end_active, record(UsageGuardRecord.END_REASON_ACTIVE).endStateTextRes())
        assertEquals(R.string.usage_guard_end_expired, record(UsageGuardRecord.END_REASON_EXPIRED).endStateTextRes())
        assertEquals(R.string.usage_guard_end_left_app, record(UsageGuardRecord.END_REASON_LEFT_APP).endStateTextRes())
        assertEquals(R.string.usage_guard_end_replaced, record(UsageGuardRecord.END_REASON_REPLACED).endStateTextRes())
        assertEquals(R.string.usage_guard_end_home_button, record(UsageGuardRecord.END_REASON_HOME_BUTTON).endStateTextRes())
        assertEquals(R.string.usage_guard_end_user_terminated, record(UsageGuardRecord.END_REASON_USER_TERMINATED).endStateTextRes())
        assertEquals(R.string.usage_guard_end_unknown, record(99).endStateTextRes())
    }

    @Test
    fun formattersUseStableLocalPatterns() {
        assertEquals(
            "2026-08-09",
            usageGuardDateFormatter.format(LocalDate.of(2026, 8, 9)),
        )
    }

    @Test
    fun dispatchRoutesEveryUsageGuardAction() {
        val calls = mutableListOf<String>()
        fun dispatch(action: UsageGuardAction) {
            dispatchUsageGuardAction(
                updateEnabled = { calls += "enabled:$it" },
                updateScopeMode = { calls += "scope:$it" },
                updateDefaultGrantMode = { calls += "grant:$it" },
                updateMinReasonLength = { calls += "min:$it" },
                updateDurationOptions = { calls += "duration:${it.joinToString()}" },
                moveSelectedAppToGrantMode = { appId, grantMode -> calls += "move:$appId:$grantMode" },
                addCustomTag = { calls += "tag:$it" },
                updateSelectedHistoryDate = { calls += "date:$it" },
                action = action,
            )
        }
        dispatch(UsageGuardAction.UpdateEnabled(true))
        dispatch(UsageGuardAction.UpdateScopeMode(1))
        dispatch(UsageGuardAction.UpdateDefaultGrantMode(2))
        dispatch(UsageGuardAction.UpdateMinReasonLength(8))
        dispatch(UsageGuardAction.UpdateDurationOptions(listOf(10, 15)))
        dispatch(UsageGuardAction.MoveSelectedAppToGrantMode("com.example", 1))
        dispatch(UsageGuardAction.AddCustomTag("学习"))
        dispatch(
            UsageGuardAction.UpdateSelectedHistoryDate(
                LocalDate.of(2026, 8, 9).toEpochDay(),
            ),
        )

        assertEquals("enabled:true", calls[0])
        assertTrue(calls.any { it == "move:com.example:1" })
        assertTrue(calls.any { it == "tag:学习" })
        assertTrue(calls.any { it == "date:2026-08-09" })
    }
}
