@file:JvmName("UrlBlockerPresenter0")

package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlBlockerLock
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule

fun UrlBlockerUiState.reduce(action: UrlBlockerAction): UrlBlockerUiState = when (action) {
    UrlBlockerAction.OpenGroupEditor -> copy(showGroupEditor = true)
    UrlBlockerAction.CloseGroupEditor -> copy(showGroupEditor = false, editingGroup = null)
    is UrlBlockerAction.EditGroup -> copy(editingGroup = action.group, showGroupEditor = true)
    UrlBlockerAction.OpenUrlEditor -> copy(showUrlEditor = true)
    UrlBlockerAction.CloseUrlEditor -> copy(
        showUrlEditor = false,
        editingUrlRule = null,
        editingTimeRule = null,
    )
    is UrlBlockerAction.EditUrlRule -> copy(editingUrlRule = action.rule, showUrlEditor = true)
    UrlBlockerAction.OpenTimeRuleEditor -> copy(showTimeRuleEditor = true)
    UrlBlockerAction.CloseTimeRuleEditor -> copy(showTimeRuleEditor = false, editingTimeRule = null)
    is UrlBlockerAction.EditTimeRule -> copy(editingTimeRule = action.rule, showTimeRuleEditor = true)
    UrlBlockerAction.OpenBrowserEditor -> copy(showBrowserEditor = true)
    UrlBlockerAction.CloseBrowserEditor -> copy(showBrowserEditor = false, editingBrowser = null)
    is UrlBlockerAction.EditBrowser -> copy(editingBrowser = action.browser, showBrowserEditor = true)
    UrlBlockerAction.OpenBrowserList -> copy(showBrowserList = true)
    UrlBlockerAction.CloseBrowserList -> copy(showBrowserList = false)
    else -> this
}

fun UrlBlockVm.present(
    allGroups: List<UrlRuleGroup>,
    allTimeRules: List<UrlTimeRule>,
    allUrlRules: List<UrlBlockRule>,
    globalLock: UrlBlockerLock?,
    browsers: List<BrowserConfig>,
): UrlBlockerUiState = UrlBlockerUiState(
    allGroups = allGroups,
    allTimeRules = allTimeRules,
    allUrlRules = allUrlRules,
    globalLock = globalLock,
    browsers = browsers,
)
