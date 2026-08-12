package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.service.UsageGuardCountdownAction
import li.songe.gkd.sdp.service.UsageGuardCountdownUiState
import li.songe.gkd.sdp.service.UsageGuardRequestAction
import li.songe.gkd.sdp.service.UsageGuardRequestUiState
import li.songe.gkd.sdp.service.reduce
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.ui.home.SettingsAction
import li.songe.gkd.sdp.ui.home.SettingsUiState
import li.songe.gkd.sdp.ui.home.reduce
import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStatePresenterContractTest {
    @Test
    fun appBlockerUiStateReducesEditorVisibility() {
        var state = AppBlockerUiState()
        state = state.reduce(AppBlockerAction.OpenGroupEditor)
        assertTrue(state.showGroupEditor)
        state = state.reduce(AppBlockerAction.CloseGroupEditor)
        assertFalse(state.showGroupEditor)
        state = state.reduce(AppBlockerAction.OpenRuleEditor)
        assertTrue(state.showRuleEditor)
        state = state.reduce(AppBlockerAction.CloseRuleEditor)
        assertFalse(state.showRuleEditor)
        assertEquals(state, state.reduce(AppBlockerAction.LockGlobal))
    }

    @Test
    fun focusLockUiStateReducesExpansionWithoutSideEffects() {
        val state = FocusLockUiState().reduce(FocusLockAction.ToggleExpandSubs(7L))
        assertEquals(setOf(7L), state.expandedSubs)
        val collapsed = state.reduce(FocusLockAction.ToggleExpandSubs(7L))
        assertTrue(collapsed.expandedSubs.isEmpty())
        val appState = collapsed.reduce(FocusLockAction.ToggleExpandApp("1_app"))
        assertEquals(setOf("1_app"), appState.expandedApps)
    }

    @Test
    fun focusModeAndUrlBlockerUiStatesKeepEditorFlagsImmutable() {
        val focus = FocusModeUiState().reduce(FocusModeAction.OpenRuleEditor)
        assertTrue(focus.showRuleEditor)
        assertFalse(focus.reduce(FocusModeAction.CloseRuleEditor).showRuleEditor)
        assertEquals(focus, focus.reduce(FocusModeAction.StartManualSession))

        val url = UrlBlockerUiState().reduce(UrlBlockerAction.OpenGroupEditor)
        assertTrue(url.showGroupEditor)
        assertFalse(url.reduce(UrlBlockerAction.CloseGroupEditor).showGroupEditor)
        assertTrue(url.reduce(UrlBlockerAction.OpenUrlEditor).showUrlEditor)
        assertFalse(url.reduce(UrlBlockerAction.CloseUrlEditor).showUrlEditor)
        assertTrue(url.reduce(UrlBlockerAction.OpenTimeRuleEditor).showTimeRuleEditor)
        assertFalse(url.reduce(UrlBlockerAction.CloseTimeRuleEditor).showTimeRuleEditor)
        assertTrue(url.reduce(UrlBlockerAction.OpenBrowserEditor).showBrowserEditor)
        assertFalse(url.reduce(UrlBlockerAction.CloseBrowserEditor).showBrowserEditor)
        assertTrue(url.reduce(UrlBlockerAction.OpenBrowserList).showBrowserList)
        assertFalse(url.reduce(UrlBlockerAction.CloseBrowserList).showBrowserList)
        assertEquals(url, url.reduce(UrlBlockerAction.LockGlobal))
    }

    @Test
    fun advancedAndImagePreviewUiStatesReduceLocalRenderState() {
        val advanced = AdvancedUiState(
            store = SettingsStore(
                actionToast = "",
                customNotifTitle = "",
                updateChannel = 1,
            ),
        ).reduce(AdvancedAction.OpenShizukuState)
        assertTrue(advanced.showShizukuState)
        assertFalse(advanced.reduce(AdvancedAction.CloseShizukuState).showShizukuState)
        assertTrue(
            AdvancedUiState(
                store = SettingsStore(
                    actionToast = "",
                    customNotifTitle = "",
                    updateChannel = 1,
                ),
            ).reduce(AdvancedAction.OpenEditPortDialog).showEditPortDlg,
        )
        assertFalse(
            AdvancedUiState(
                showEditPortDlg = true,
                store = SettingsStore(
                    actionToast = "",
                    customNotifTitle = "",
                    updateChannel = 1,
                ),
            )
                .reduce(AdvancedAction.CloseEditPortDialog)
                .showEditPortDlg,
        )
        assertTrue(
            AdvancedUiState(
                store = SettingsStore(
                    actionToast = "",
                    customNotifTitle = "",
                    updateChannel = 1,
                ),
            ).reduce(AdvancedAction.OpenCaptureScreenshotDialog)
                .showCaptureScreenshotDlg,
        )
        assertFalse(
            AdvancedUiState(
                showCaptureScreenshotDlg = true,
                store = SettingsStore(
                    actionToast = "",
                    customNotifTitle = "",
                    updateChannel = 1,
                ),
            )
                .reduce(AdvancedAction.CloseCaptureScreenshotDialog)
                .showCaptureScreenshotDlg,
        )
        val withHttpSettings = advanced.reduce(AdvancedAction.ToggleHttpSetting)
        assertTrue(withHttpSettings.showHttpSettingDlg)
        assertFalse(withHttpSettings.reduce(AdvancedAction.ToggleHttpSetting).showHttpSettingDlg)

        val preview = ImagePreviewUiState(items = listOf(ImagePreviewItem("a"), ImagePreviewItem("b")))
        val page = preview.reduce(ImagePreviewAction.ShowPage(1))
        assertEquals(1, page.currentPage)
        assertFalse(page.reduce(ImagePreviewAction.ToggleBars).showBars)
    }

    @Test
    fun settingsUsageGuardAndOverlayUiStatesReduceActions() {
        val settings = SettingsUiState().reduce(
            SettingsAction.UpdateBackupCategory("self_control_config", true),
        )
        assertTrue(settings.backup.selectedCategoryIds.contains("self_control_config"))
        assertTrue(settings.reduce(SettingsAction.ClearBackupError).backup.errorText == null)
        assertTrue(
            settings.reduce(SettingsAction.ResetBackupWorkflow).backup.selectedCategoryIds
                .contains("settings"),
        )

        val usageGuard = UsageGuardUiState().reduce(
            UsageGuardAction.UpdateSelectedHistoryDate(12),
        )
        assertEquals(12L, usageGuard.selectedHistoryDateEpochDay)
        assertEquals(usageGuard, usageGuard.reduce(UsageGuardAction.UpdateEnabled(true)))

        val countdown = UsageGuardCountdownUiState(
            remainingMillis = 1_000L,
            reasonText = "reason",
            showTerminateConfirm = false,
        ).reduce(UsageGuardCountdownAction.OpenTerminateConfirm)
        assertTrue(countdown.showTerminateConfirm)
        assertFalse(
            countdown.reduce(UsageGuardCountdownAction.DismissTerminateConfirm)
                .showTerminateConfirm,
        )

        val request = UsageGuardRequestUiState().reduce(UsageGuardRequestAction.Cancel)
        assertTrue(request.dataset is li.songe.gkd.sdp.service.UsageRequestDatasetState.Loading)
        assertEquals(request, request.reduce(UsageGuardRequestAction.Submit))
    }

    @Test
    fun usageGuardReviewPageUiStateReducesAllSelections() {
        var state = UsageGuardReviewPageUiState()
        state = state.reduce(
            UsageGuardReviewAction.UpdateRange(
                DigitalSelfDisciplineReviewPolicy.Range.LAST_7_DAYS,
            ),
        )
        assertEquals(DigitalSelfDisciplineReviewPolicy.Range.LAST_7_DAYS, state.selectedRange)

        state = state.reduce(
            UsageGuardReviewAction.UpdateReviewType(
                DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt,
            ),
        )
        assertEquals(
            DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt,
            state.selectedType,
        )

        state = state.reduce(
            UsageGuardReviewAction.UpdateMetric(
                DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL,
            ),
        )
        assertEquals(
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL,
            state.selectedMetric,
        )

        state = state.reduce(
            UsageGuardReviewAction.UpdateInterceptFilter(
                DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.Url,
            ),
        )
        assertEquals(DigitalSelfDisciplineReviewPolicy.InterceptKindFilter.Url, state.selectedFilter)
    }
}
