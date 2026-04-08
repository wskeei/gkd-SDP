package li.songe.gkd.sdp.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.UsageGuardPolicy

class UsageGuardVm : BaseViewModel() {
    val appProfilesFlow = DbSet.usageGuardAppProfileDao.queryAll().stateInit(emptyList())
    val tagsFlow = DbSet.usageGuardTagDao.queryAll().stateInit(emptyList())
    val historyFlow = DbSet.usageGuardRecordDao.queryLatest(50).stateInit(emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (DbSet.usageGuardTagDao.count() > 0) return@launch
            presetTags.forEach { name ->
                DbSet.usageGuardTagDao.insert(
                    UsageGuardTag(name = name, isPreset = true),
                )
            }
        }
    }

    fun updateEnabled(enabled: Boolean) {
        storeFlow.update { it.copy(usageGuardEnabled = enabled) }
    }

    fun updateScopeMode(scopeMode: Int) {
        storeFlow.update { it.copy(usageGuardScopeMode = scopeMode) }
    }

    fun updateDefaultGrantMode(grantMode: Int) {
        storeFlow.update { it.copy(usageGuardDefaultGrantMode = grantMode) }
    }

    fun updateMinReasonLength(minLength: Int) {
        storeFlow.update { it.copy(usageGuardMinReasonLength = minLength.coerceAtLeast(1)) }
    }

    fun saveSelectedTargets(appIds: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        saveProfileFlags(
            appIds = appIds,
            updateProfile = { profile, selected ->
                profile.copy(
                    selectedTarget = selected,
                    updatedAt = System.currentTimeMillis(),
                )
            },
            createProfile = { appId ->
                UsageGuardAppProfile(
                    appId = appId,
                    selectedTarget = true,
                    grantMode = storeFlow.value.usageGuardDefaultGrantMode,
                )
            },
        )
    }

    fun saveWhitelist(appIds: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        saveProfileFlags(
            appIds = appIds,
            updateProfile = { profile, selected ->
                profile.copy(
                    globalWhitelist = selected,
                    updatedAt = System.currentTimeMillis(),
                )
            },
            createProfile = { appId ->
                UsageGuardAppProfile(
                    appId = appId,
                    globalWhitelist = true,
                    grantMode = storeFlow.value.usageGuardDefaultGrantMode,
                )
            },
        )
    }

    fun saveAppGrantMode(appId: String, grantMode: Int) = viewModelScope.launch(Dispatchers.IO) {
        val current = appProfilesFlow.value.associateBy { it.appId }[appId]
        DbSet.usageGuardAppProfileDao.insert(
            current?.copy(
                grantMode = grantMode,
                updatedAt = System.currentTimeMillis(),
            ) ?: UsageGuardAppProfile(
                appId = appId,
                grantMode = grantMode,
            )
        )
    }

    fun addCustomTag(name: String) = viewModelScope.launch(Dispatchers.IO) {
        val normalized = name.trim()
        if (normalized.isBlank()) return@launch
        val duplicated = tagsFlow.value.any { it.name.equals(normalized, ignoreCase = true) }
        if (duplicated) return@launch
        DbSet.usageGuardTagDao.insert(
            UsageGuardTag(name = normalized, isPreset = false),
        )
    }

    fun deleteCustomTag(tag: UsageGuardTag) = viewModelScope.launch(Dispatchers.IO) {
        if (tag.isPreset) return@launch
        DbSet.usageGuardTagDao.deleteCustomTag(tag.id)
    }

    private suspend fun saveProfileFlags(
        appIds: List<String>,
        updateProfile: (UsageGuardAppProfile, Boolean) -> UsageGuardAppProfile,
        createProfile: (String) -> UsageGuardAppProfile,
    ) {
        val targetSet = appIds.toSet()
        val profilesByAppId = appProfilesFlow.value.associateBy { it.appId }

        profilesByAppId.values.forEach { profile ->
            DbSet.usageGuardAppProfileDao.insert(
                updateProfile(profile, targetSet.contains(profile.appId))
            )
        }
        targetSet.filterNot(profilesByAppId::containsKey).forEach { appId ->
            DbSet.usageGuardAppProfileDao.insert(createProfile(appId))
        }
        DbSet.usageGuardAppProfileDao.deleteUnusedProfiles()
    }

    companion object {
        val presetTags = listOf("联系工作", "回复消息", "查资料", "支付", "其他")
    }
}
