package li.songe.gkd.sdp.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import li.songe.gkd.sdp.data.ActionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLogStateContractTest {
    @Test
    fun actionsAreTypedAndImmutable() {
        val selectTab: ActionLogAction = ActionLogAction.SelectTab(1)
        val openDetail: ActionLogAction = ActionLogAction.OpenDetail(7)
        val dismiss: ActionLogAction = ActionLogAction.DismissDetail

        assertTrue(selectTab is ActionLogAction.SelectTab)
        assertEquals(1, (selectTab as ActionLogAction.SelectTab).index)
        assertTrue(openDetail is ActionLogAction.OpenDetail)
        assertEquals(7, (openDetail as ActionLogAction.OpenDetail).actionLogId)
        assertTrue(dismiss is ActionLogAction.DismissDetail)
    }

    @Test
    fun reducerMovesSelectionAndDetail() {
        val selected = applyActionLogAction(
            ActionLogUiState(),
            ActionLogAction.SelectTab(1),
        )
        assertEquals(1, selected.selectedTabIndex)

        val unchanged = applyActionLogAction(
            selected,
            ActionLogAction.OpenDetail(7),
        )
        assertEquals(1, unchanged.selectedTabIndex)

        val dismissed = applyActionLogAction(
            unchanged,
            ActionLogAction.DismissDetail,
        )
        assertEquals(1, dismissed.selectedTabIndex)
    }

    @Test
    fun reducerDismissClearsDetail() {
        val log = ActionLog(
            ctime = 1L,
            appId = "com.example",
            subsId = 1L,
            subsVersion = 1,
            groupKey = 1,
            groupType = 2,
            ruleIndex = 0,
        )
        val state = ActionLogUiState(selectedTabIndex = 1, detail = log)

        val dismissed = applyActionLogAction(state, ActionLogAction.DismissDetail)

        assertNull(dismissed.detail)
        assertEquals(1, dismissed.selectedTabIndex)
    }

    @Test
    fun dispatchAppliesSelectionDetailAndDismissSideEffects() = runBlocking {
        val selectedTab = MutableStateFlow(0)
        val detail = MutableStateFlow<ActionLog?>(null)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val log = ActionLog(
            ctime = 1L,
            appId = "com.example",
            subsId = 1L,
            subsVersion = 1,
            groupKey = 1,
            groupType = 2,
            ruleIndex = 0,
        )

        dispatchActionLog(
            selectedTabIndex = selectedTab,
            detail = detail,
            scope = scope,
            loadDetail = { id -> if (id == 7) log else null },
            action = ActionLogAction.SelectTab(1),
        )
        assertEquals(1, selectedTab.value)

        dispatchActionLog(
            selectedTabIndex = selectedTab,
            detail = detail,
            scope = scope,
            loadDetail = { id -> if (id == 7) log else null },
            action = ActionLogAction.OpenDetail(7),
        )
        assertEquals(log, detail.value)

        dispatchActionLog(
            selectedTabIndex = selectedTab,
            detail = detail,
            scope = scope,
            loadDetail = { null },
            action = ActionLogAction.DismissDetail,
        )
        assertNull(detail.value)
    }
}
