package li.songe.gkd.sdp.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.data.UsageGuardAppProfile
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.UsageGuardHistoryPolicy
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import li.songe.gkd.sdp.util.toast

class UsageGuardVm : BaseViewModel() {
    val appProfilesFlow = DbSet.usageGuardAppProfileDao.queryAll().stateInit(emptyList())
    val tagsFlow = DbSet.usageGuardTagDao.queryAll().stateInit(emptyList())
    val selectedAppSectionsFlow = combine(appProfilesFlow, storeFlow) { profiles, settings ->
        UsageGuardUiStatePolicy.groupSelectedApps(
            profiles = profiles,
            defaultGrantMode = settings.usageGuardDefaultGrantMode,
        )
    }.stateInit(
        UsageGuardUiStatePolicy.groupSelectedApps(
            profiles = emptyList(),
            defaultGrantMode = storeFlow.value.usageGuardDefaultGrantMode,
        )
    )
    val durationOptionsFlow = storeFlow.map { settings ->
        UsageGuardUiStatePolicy.normalizeDurationOptions(settings.usageGuardDurationOptionsMinutes)
    }.stateInit(UsageGuardUiStatePolicy.defaultDurationOptions)
    private val selectedHistoryDateFlow = MutableStateFlow(LocalDate.now())
    val historyFlow = selectedHistoryDateFlow.flatMapLatest { date ->
        val (startAt, endAt) = UsageGuardHistoryPolicy.dayRange(date)
        DbSet.usageGuardRecordDao.queryByRequestedAtRange(startAt, endAt)
    }.stateInit(emptyList())

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
        viewModelScope.launch(Dispatchers.IO) {
            DbSet.usageGuardAppProfileDao.deleteUnusedProfiles(grantMode)
        }
    }

    fun updateMinReasonLength(minLength: Int) {
        storeFlow.update { it.copy(usageGuardMinReasonLength = minLength.coerceAtLeast(1)) }
        toast("最少理由字数已保存")
    }

    fun updateDurationOptions(raw: List<Int>) {
        val normalized = UsageGuardUiStatePolicy.normalizeDurationOptions(raw)
        storeFlow.update { it.copy(usageGuardDurationOptionsMinutes = normalized) }
        toast("申请时长选项已保存")
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
        toast("受控应用已保存")
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
        toast("白名单已保存")
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
        DbSet.usageGuardAppProfileDao.deleteUnusedProfiles(storeFlow.value.usageGuardDefaultGrantMode)
        toast("应用模式已保存")
    }

    fun moveSelectedAppToGrantMode(appId: String, grantMode: Int) {
        saveAppGrantMode(appId, grantMode)
    }

    fun saveGrantModeOverrideApps(appIds: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        val targetSet = appIds.toSet()
        val profilesByAppId = appProfilesFlow.value.associateBy { it.appId }
        val defaultGrantMode = storeFlow.value.usageGuardDefaultGrantMode
        val overrideGrantMode = oppositeGrantMode(defaultGrantMode)

        targetSet.forEach { appId ->
            val current = profilesByAppId[appId]
            DbSet.usageGuardAppProfileDao.insert(
                if (current == null) {
                    UsageGuardAppProfile(
                        appId = appId,
                        grantMode = overrideGrantMode,
                    )
                } else if (current.grantMode == defaultGrantMode) {
                    current.copy(
                        grantMode = overrideGrantMode,
                        updatedAt = System.currentTimeMillis(),
                    )
                } else {
                    current
                }
            )
        }
    }

    fun clearAppGrantModeOverride(appId: String) = viewModelScope.launch(Dispatchers.IO) {
        val current = appProfilesFlow.value.associateBy { it.appId }[appId] ?: return@launch
        val defaultGrantMode = storeFlow.value.usageGuardDefaultGrantMode
        DbSet.usageGuardAppProfileDao.insert(
            current.copy(
                grantMode = defaultGrantMode,
                updatedAt = System.currentTimeMillis(),
            )
        )
        DbSet.usageGuardAppProfileDao.deleteUnusedProfiles(defaultGrantMode)
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

    fun updateSelectedHistoryDate(date: LocalDate) {
        selectedHistoryDateFlow.value = date
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
        DbSet.usageGuardAppProfileDao.deleteUnusedProfiles(storeFlow.value.usageGuardDefaultGrantMode)
    }

    companion object {
        val presetTags = listOf("联系工作", "回复消息", "查资料", "支付", "其他")

        fun shouldRetainProfile(profile: UsageGuardAppProfile, defaultGrantMode: Int): Boolean {
            return profile.selectedTarget ||
                profile.globalWhitelist ||
                profile.grantMode != defaultGrantMode
        }

        fun oppositeGrantMode(defaultGrantMode: Int): Int {
            return if (defaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT) {
                UsageGuardPolicy.GRANT_MODE_RESUMABLE
            } else {
                UsageGuardPolicy.GRANT_MODE_STRICT
            }
        }
    }
}
