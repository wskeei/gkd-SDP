package li.songe.gkd.sdp.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.service.ExposeService
import li.songe.gkd.sdp.util.AppListString
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.R

val storeFlow by lazy {
    createAnyFlow(
        key = "store",
        default = { SettingsStore() }
    )
}

val actionCountFlow by lazy {
    createTextFlow(
        key = "action_count",
        decode = { it?.toLongOrNull() ?: 0L },
        encode = { it.toString() },
    )
}

val blockMatchAppListFlow by lazy {
    createTextFlow(
        key = "block_match_app_list",
        decode = { it?.let(AppListString::decode) ?: AppListString.getDefaultBlockList() },
        encode = AppListString::encode,
    )
}

val blockA11yAppListFlow by lazy {
    createTextFlow(
        key = "block_a11y_app_list",
        decode = { it?.let(AppListString::decode) ?: emptySet() },
        encode = AppListString::encode,
    )
}

val actualBlockA11yAppList: Set<String>
    get() = if (storeFlow.value.blockA11yAppListFollowMatch) {
        blockMatchAppListFlow.value
    } else {
        blockA11yAppListFlow.value
    }

val a11yScopeAppListFlow by lazy {
    createTextFlow(
        key = "a11y_scope_app_list",
        decode = { it?.let(AppListString::decode) ?: setOf("com.tencent.mm") },
        encode = AppListString::encode,
    )
}

val actualA11yScopeAppList: Set<String>
    get() = if (storeFlow.value.useAutomation) {
        a11yScopeAppListFlow.value
    } else {
        emptySet()
    }

fun checkAppBlockMatch(appId: String): Boolean {
    if (blockMatchAppListFlow.value.contains(appId)) {
        return true
    }
    if (storeFlow.value.enableBlockA11yAppList) {
        return actualBlockA11yAppList.contains(appId)
    }
    return false
}

fun initStore() = appScope.launchTry(Dispatchers.IO) {
    // preload
    storeFlow.value
    initDisplayPreferenceBackup()
    actionCountFlow.value
    blockMatchAppListFlow.value
    blockA11yAppListFlow.value
    a11yScopeAppListFlow.value
    accessibilityGuardSessionFlow.value
    ExposeService.clearCommandFiles()
}

fun switchStoreEnableMatch() {
    if (storeFlow.value.enableMatch) {
        toast(li.songe.gkd.sdp.app.getString(R.string.s_2bd91e39a7))
    } else {
        toast(li.songe.gkd.sdp.app.getString(R.string.s_bb9c248fa1))
    }
    storeFlow.update { it.copy(enableMatch = !it.enableMatch) }
}

fun updateEnableAutomator(value: Boolean) {
    if (value == storeFlow.value.enableAutomator) return
    storeFlow.update { it.copy(enableAutomator = value) }
}
