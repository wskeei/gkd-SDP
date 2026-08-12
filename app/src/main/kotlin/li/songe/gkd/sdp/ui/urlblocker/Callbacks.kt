@file:JvmName("UrlBlockerCallbacks")

package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.BrowserConfig
import li.songe.gkd.sdp.data.UrlBlockRule
import li.songe.gkd.sdp.data.UrlRuleGroup
import li.songe.gkd.sdp.data.UrlTimeRule

data class UrlBlockerCallbacks(
    val onBack: () -> Unit,
    val onAddGroup: () -> Unit,
    val onOpenBrowserList: () -> Unit,
    val onOpenGlobalLock: () -> Unit,
    val onDismissGlobalLock: () -> Unit,
    val onLockGlobal: (UrlLockDraft) -> Unit,
    val onToggleGroup: (UrlRuleGroup) -> Unit,
    val onEditGroup: (UrlRuleGroup) -> Unit,
    val onDeleteGroup: (UrlRuleGroup) -> Unit,
    val onLockGroup: (UrlRuleGroup) -> Unit,
    val onAddTimeRule: (Int, Long) -> Unit,
    val onEditTimeRule: (UrlTimeRule) -> Unit,
    val onDeleteTimeRule: (UrlTimeRule) -> Unit,
    val onLockTimeRule: (UrlTimeRule) -> Unit,
    val onAddUrlRule: (Long) -> Unit,
    val onEditUrlRule: (UrlBlockRule) -> Unit,
    val onDeleteUrlRule: (UrlBlockRule) -> Unit,
    val onToggleUrlRule: (UrlBlockRule) -> Unit,
    val onLockUrlRule: (UrlBlockRule) -> Unit,
    val onDismissGroupEditor: () -> Unit,
    val onSaveGroup: (String, String) -> Unit,
    val onDismissUrlEditor: () -> Unit,
    val onSaveUrlRule: (UrlRuleDraft) -> Unit,
    val onDismissTimeRuleEditor: () -> Unit,
    val onSaveTimeRule: (UrlTimeRuleDraft) -> Unit,
    val onDismissBrowserList: () -> Unit,
    val onAddBrowser: () -> Unit,
    val onEditBrowser: (BrowserConfig) -> Unit,
    val onDeleteBrowser: (BrowserConfig) -> Unit,
    val onToggleBrowser: (BrowserConfig) -> Unit,
    val onDismissBrowserEditor: () -> Unit,
    val onSaveBrowser: (BrowserDraft) -> Unit,
    val onDismissGroupLock: () -> Unit,
    val onLockGroupTarget: (UrlLockDraft) -> Unit,
    val onDismissTimeRuleLock: () -> Unit,
    val onLockTimeRuleTarget: (UrlLockDraft) -> Unit,
    val onDismissUrlRuleLock: () -> Unit,
    val onLockUrlRuleTarget: (UrlLockDraft) -> Unit,
)
