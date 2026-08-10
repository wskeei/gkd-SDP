package li.songe.gkd.sdp.ui.component

import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.style.DimensionTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonComponentContractTest {
    @Test
    fun contentStateHasExactlyFourStates() {
        val states: List<ContentState> = listOf(
            ContentState.Loading,
            ContentState.Empty(title = "空", description = "说明", actionText = "去添加", onAction = {}),
            ContentState.Content,
            ContentState.Error(errorCode = "E_1", description = "失败", onRetry = {}),
        )
        assertEquals(4, states.size)
        assertTrue(states[0] is ContentState.Loading)
        assertTrue(states[1] is ContentState.Empty)
        assertTrue(states[2] is ContentState.Content)
        assertTrue(states[3] is ContentState.Error)
    }

    @Test
    fun loadingDelayIsFixedToAvoidFlicker() {
        assertEquals(400, ContentStatePolicy.LoadingDelayMs)
    }

    @Test
    fun emptyAndErrorCarryTitleDescriptionAndActions() {
        val empty = ContentState.Empty(
            title = "暂无记录",
            description = "开始使用后会在这里显示",
            actionText = "创建",
            onAction = {},
        )
        assertEquals("暂无记录", empty.title)
        assertEquals("开始使用后会在这里显示", empty.description)
        assertEquals("创建", empty.actionText)

        val error = ContentState.Error(
            errorCode = "db_001",
            description = "读取失败",
            onRetry = {},
            recoveryText = "打开设置",
            onRecovery = {},
        )
        assertEquals("db_001", error.errorCode)
        assertEquals("读取失败", error.description)
        assertTrue(error.onRetry != null)
        assertEquals("打开设置", error.recoveryText)
        assertTrue(error.onRecovery != null)
    }

    @Test
    fun inlineMessageKindsCoverAllLevels() {
        assertEquals(4, InlineMessageKind.entries.size)
        assertTrue(InlineMessageKind.entries.containsAll(
            listOf(InlineMessageKind.Info, InlineMessageKind.Success, InlineMessageKind.Warning, InlineMessageKind.Error),
        ))
    }

    @Test
    fun formFieldCountsAndValidates() {
        assertEquals("3/50", AppFormFieldPolicy.charCountLabel(3, 50))
        assertEquals("50/50", AppFormFieldPolicy.charCountLabel(50, 50))
        assertNull(AppFormFieldPolicy.charCountLabel(3, 0))
        assertNull(AppFormFieldPolicy.charCountLabel(3, -1))

        assertNull(AppFormFieldPolicy.validationError("x", required = true, minLength = 1))
        assertEquals("field_required", AppFormFieldPolicy.validationError("  ", required = true))
        assertEquals("field_too_short", AppFormFieldPolicy.validationError("ab", required = true, minLength = 3))
        assertNull(AppFormFieldPolicy.validationError("", required = false))
    }

    @Test
    fun actionBarAnchorsToWindowWidth() {
        assertTrue(AppActionBarPolicy.isCompactWindow(360))
        assertTrue(AppActionBarPolicy.isCompactWindow(599))
        assertFalse(AppActionBarPolicy.isCompactWindow(600))
        assertFalse(AppActionBarPolicy.isCompactWindow(840))
    }

    @Test
    fun fullDeletionRequiresTheExactPhrase() {
        assertTrue(FullDeletionPolicy.isConfirmed("删除全部数据"))
        assertTrue(FullDeletionPolicy.isConfirmed("  删除全部数据  "))
        assertFalse(FullDeletionPolicy.isConfirmed("删除"))
        assertFalse(FullDeletionPolicy.isConfirmed("删除全部"))
        assertFalse(FullDeletionPolicy.isConfirmed("删除全部数据2"))
        assertEquals("删除全部数据", FullDeletionPolicy.FullDeletionPhrase)
    }

    @Test
    fun chartTableRowsAndDetailTextAreStable() {
        val buckets = listOf(
            ChartBucket(label = "08:00", value = 12.0, sampleCount = 3, hasValue = true),
            ChartBucket(label = "09:00", value = 0.0, sampleCount = 0, hasValue = false),
        )
        val rows = AppDataTablePolicy.rows(buckets, unit = "分", formatValue = { it.toInt().toString() })

        assertEquals(2, rows.size)
        assertEquals("08:00", rows[0].label)
        assertEquals("12", rows[0].valueText)
        assertEquals("分", rows[0].unit)
        assertEquals("样本 3", rows[0].status)
        assertEquals("09:00", rows[1].label)
        assertEquals("—", rows[1].valueText)
        assertEquals("", rows[1].unit)
        assertEquals("无数据", rows[1].status)

        assertEquals(
            "08:00：12分，样本 3",
            AppDataTablePolicy.pointDetailText(rows[0]),
        )
    }

    @Test
    fun iconButtonUsesThe48dpTouchShell() {
        assertEquals(48.dp, DimensionTokens.MinTouchTarget)
        assertEquals(24.dp, DimensionTokens.IconSizeDefault)
    }
}
