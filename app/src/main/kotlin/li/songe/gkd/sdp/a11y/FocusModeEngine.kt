package li.songe.gkd.sdp.a11y

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.runtime.appDependencies
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.FocusOverlayService
import li.songe.gkd.sdp.notif.focusEndNotif
import li.songe.gkd.sdp.a11y.topActivityFlow
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.json
import java.util.concurrent.ConcurrentHashMap

object FocusModeEngine {
    
    /**
     * 妫€鏌ュ綋鍓嶆槸鍚﹀浜庝笓娉ㄦā寮忥紙浼氳瘽鏈夋晥鎴栧畾鏃惰鍒欑敓鏁堬級
     */
    private fun isInFocusMode(): Boolean {
        // 妫€鏌ユ墜鍔ㄤ細璇?
        val session = cachedSession
        if (session?.isValidNow() == true) return true
        // 妫€鏌ュ畾鏃惰鍒?
        return cachedRules.any { it.isActiveNow() }
    }
    
    /**
     * 妫€鏌ュ簲鐢ㄦ槸鍚﹀湪鐧藉悕鍗曚腑
     * GKD-SDP 搴旂敤鏈韩榛樿鍦ㄧ櫧鍚嶅崟涓紝浠ヤ究鐢ㄦ埛鍙互闅忔椂璁块棶璁剧疆
     */
    private fun isWhitelisted(packageName: String): Boolean {
        return currentWhitelistFlow.value.contains(packageName)
    }
    
    /**
     * 閫掑綊鏌ユ壘鐗瑰畾绫诲悕鐨勮妭鐐?
     */
    private fun findNodesByClass(
        node: AccessibilityNodeInfo,
        className: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString() == className) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClass(child, className, result)
        }
    }
    private const val TAG = "FocusModeEngine"

    // 缂撳瓨鐨勮鍒欏拰浼氳瘽
    private var cachedRules: List<FocusRule> = emptyList()
    private var cachedSession: FocusSession? = null

    // 鍐峰嵈鏃堕棿缂撳瓨锛岄槻姝㈤噸澶嶈Е鍙?
    private val cooldownMap = ConcurrentHashMap<String, Long>()
    private const val COOLDOWN_MS = 2000L  // 2绉掑喎鍗存椂闂?

    // 鏄惁鍚敤涓撴敞妯″紡寮曟搸
    val enabledFlow = MutableStateFlow(true)

    // 褰撳墠娲昏穬鐨勪笓娉ㄦā寮忕姸鎬?
    val activeSessionFlow = DbSet.focusSessionDao.getSession()
        .stateIn(appScope, SharingStarted.Eagerly, null)

    // 鎵€鏈夎鍒?
    val allRulesFlow = DbSet.focusRuleDao.queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 鍚敤鐨勮鍒?
    val enabledRulesFlow = DbSet.focusRuleDao.queryEnabled()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 鏄惁鏈夋椿璺冪殑涓撴敞妯″紡锛堜細璇濇湁鏁堟垨瑙勫垯鍦ㄦ椂闂存鍐咃級
    val isActiveFlow = combine(activeSessionFlow, enabledRulesFlow) { session, rules ->
        // 妫€鏌ユ墜鍔ㄤ細璇?
        if (session?.isValidNow() == true) return@combine true
        // 妫€鏌ュ畾鏃惰鍒?
        rules.any { it.isActiveNow() }
    }.stateIn(appScope, SharingStarted.Eagerly, false)

    // 褰撳墠鐢熸晥鐨勭櫧鍚嶅崟
    val currentWhitelistFlow = combine(activeSessionFlow, enabledRulesFlow) { session, rules ->
        getEffectiveWhitelist(session, rules)
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 褰撳墠鐢熸晥鐨勬嫤鎴秷鎭?
    val currentMessageFlow = combine(activeSessionFlow, enabledRulesFlow) { session, rules ->
        getEffectiveMessage(session, rules)
    }.stateIn(appScope, SharingStarted.Eagerly, "涓撴敞褰撲笅")

    init {
        // 鐩戝惉瑙勫垯鍜屼細璇濆彉鍖?
        appScope.launch(appDependencies.dispatchers.io) {
            combine(
                DbSet.focusRuleDao.queryEnabled(),
                DbSet.focusSessionDao.getSession()
            ) { rules, session ->
                rules to session
            }.collect { (rules, session) ->
                cachedRules = rules
                cachedSession = session
                LogUtils.d("focus configuration updated", rules.size, session != null)
            }
        }

        // 鐩戝惉浼氳瘽杩囨湡骞惰嚜鍔ㄧ粨鏉?
        appScope.launch(appDependencies.dispatchers.io) {
            while (true) {
                delay(30_000L)  // 姣?30 绉掓鏌ヤ竴娆?

                val session = cachedSession
                if (session != null && session.isActive && !session.isValidNow()) {
                    // 浼氳瘽宸茶繃鏈燂紝鍋滅敤
                    DbSet.focusSessionDao.deactivate()
                    LogUtils.d("Focus session expired, deactivated")

                    // 鍏抽棴鎷︽埅鐣岄潰
                    closeFocusOverlay()

                    // 鍙戦€佺粨鏉熼€氱煡
                    focusEndNotif.notifySelf()
                }
            }
        }
    }

    /**
     * 鍏抽棴涓撴敞妯″紡鎷︽埅鐣岄潰
     */
    private fun closeFocusOverlay() {
        try {
            val intent = Intent(app, FocusOverlayService::class.java)
            selfControlOverlayLauncher.stop(intent)
            LogUtils.d("Focus overlay service stopped")
        } catch (e: Exception) {
            LogUtils.d("Failed to stop focus overlay", e::class.java.simpleName)
        }
    }

    /**
     * 鑾峰彇褰撳墠鏈夋晥鐨勭櫧鍚嶅崟
     */
    private fun getEffectiveWhitelist(session: FocusSession?, rules: List<FocusRule>): List<String> {
        // 浼樺厛浣跨敤鎵嬪姩浼氳瘽鐨勭櫧鍚嶅崟
        if (session?.isValidNow() == true) {
            return session.getWhitelistPackages()
        }
        // 浣跨敤褰撳墠鐢熸晥瑙勫垯鐨勭櫧鍚嶅崟
        val activeRule = rules.firstOrNull { it.isActiveNow() }
        return activeRule?.getWhitelistPackages() ?: emptyList()
    }

    /**
     * 鑾峰彇褰撳墠鏈夋晥鐨勬嫤鎴秷鎭?
     */
    private fun getEffectiveMessage(session: FocusSession?, rules: List<FocusRule>): String {
        if (session?.isValidNow() == true) {
            return session.interceptMessage
        }
        val activeRule = rules.firstOrNull { it.isActiveNow() }
        return activeRule?.interceptMessage ?: "涓撴敞褰撲笅"
    }

    /**
     * 澶勭悊搴旂敤鍒囨崲浜嬩欢
     */
    fun onAppChanged(
        packageName: String,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
    ) {
        if (!enabledFlow.value) return
        if (owner != null && !sdpRuntimeFeatureCoordinator.isCurrent(owner)) return
        if (!isInFocusMode()) {
            sdpRuntimeFeatureCoordinator.recordDecision(owner, "focus", packageName, "outside_schedule")
            if (owner == null || sdpRuntimeFeatureCoordinator.isCurrent(owner)) {
                closeFocusOverlay()
            }
            return
        }

        val now = appDependencies.clock.nowEpochMillis()
        val lastTriggerTime = cooldownMap[packageName] ?: 0L
        if (now - lastTriggerTime < COOLDOWN_MS) {
            return
        }

        if (isWhitelisted(packageName)) {
            sdpRuntimeFeatureCoordinator.recordDecision(owner, "focus", packageName, "whitelisted")
            LogUtils.d("focus decision allowed")
            return
        }

        LogUtils.d("Focus mode blocking: $packageName")
        if (owner != null && !sdpRuntimeFeatureCoordinator.isCurrent(owner)) return
        val result = showFocusOverlay(packageName, owner = owner)
        sdpRuntimeFeatureCoordinator.recordDecision(
            owner,
            "focus",
            packageName,
            "overlay_${result::class.simpleName ?: "unknown"}",
        )
        if (result == OverlayLaunchResult.Accepted &&
            (owner == null || sdpRuntimeFeatureCoordinator.isCurrent(owner))
        ) {
            cooldownMap[packageName] = now
        }
    }

    fun onA11yEvent(event: android.view.accessibility.AccessibilityEvent) = Unit

    private fun showFocusOverlay(
        packageName: String,
        overrideWhitelist: List<String>? = null,
        overrideMessage: String? = null,
        overrideEndTime: Long? = null,
        overrideIsLocked: Boolean? = null,
        owner: SdpRuntimeFeatureCoordinator.RuntimeOwner? = null,
    ): OverlayLaunchResult {
        return try {
            val message = overrideMessage ?: currentMessageFlow.value
            val whitelist = overrideWhitelist ?: currentWhitelistFlow.value
            val session = cachedSession
            val activeRule = cachedRules.firstOrNull { it.isActiveNow() }
            val isLocked = overrideIsLocked ?: (session?.isCurrentlyLocked == true || activeRule?.isCurrentlyLocked == true)
            val endTime = overrideEndTime ?: session?.endTime ?: 0L

            val intent = Intent(app, FocusOverlayService::class.java).apply {
                putExtra("message", message)
                putExtra("whitelist", json.encodeToString(whitelist))
                putExtra("blockedApp", packageName)
                putExtra("isLocked", isLocked)
                putExtra("endTime", endTime)
            }
            if (owner == null || sdpRuntimeFeatureCoordinator.isCurrent(owner)) {
                val result = selfControlOverlayLauncher.launch(intent)
                if (result == OverlayLaunchResult.Accepted &&
                    owner != null && !sdpRuntimeFeatureCoordinator.isCurrent(owner)
                ) {
                    OverlayLaunchResult.RuntimeUnavailable
                } else {
                    result
                }
            } else {
                OverlayLaunchResult.RuntimeUnavailable
            }
        } catch (e: Exception) {
            LogUtils.d("focus overlay start rejected", e::class.java.simpleName)
            OverlayLaunchResult.Rejected(OverlayFailureCategory.UNKNOWN)
        }
    }

    /**
     * 鎵嬪姩寮€鍚笓娉ㄦā寮?
     */
    suspend fun startManualSession(
        durationMinutes: Int,
        whitelistApps: List<String>,
        interceptMessage: String = "涓撴敞褰撲笅",
        isLocked: Boolean = false,
        lockDurationMinutes: Int = 0
    ) {
        val now = appDependencies.clock.nowEpochMillis()
        val endTime = now + durationMinutes * 60 * 1000L
        val lockEndTime = if (isLocked) now + lockDurationMinutes * 60 * 1000L else 0L

        val session = FocusSession(
            id = 1,
            isActive = true,
            ruleId = null,
            startTime = now,
            endTime = endTime,
            whitelistApps = json.encodeToString(whitelistApps),
            interceptMessage = interceptMessage,
            isManual = true,
            isLocked = isLocked,
            lockEndTime = lockEndTime
        )

        DbSet.focusSessionDao.insert(session)
        LogUtils.d("Manual focus session started: ${durationMinutes}min, whitelist: ${whitelistApps.size} apps")

        // 绔嬪嵆瑙﹀彂鎷︽埅鐣岄潰锛岀洿鎺ヤ紶閫掑弬鏁帮紙鍥犱负 Flow 鍙兘杩樻湭鏇存柊锛?
        showFocusOverlay(
            packageName = "manual_start",
            overrideWhitelist = whitelistApps,
            overrideMessage = interceptMessage,
            overrideEndTime = endTime,
            overrideIsLocked = isLocked
        )
    }

    /**
     * 鍋滄鎵嬪姩浼氳瘽
     */
    suspend fun stopManualSession() {
        val session = cachedSession
        if (session?.isManual == true && !session.isCurrentlyLocked) {
            DbSet.focusSessionDao.deactivate()
            LogUtils.d("Manual focus session stopped")
        }
    }

    /**
     * 浠庝細璇濈櫧鍚嶅崟涓Щ闄ゅ簲鐢?
     */
    suspend fun removeFromWhitelist(packageName: String) {
        val session = cachedSession ?: return
        if (!session.isActive) return

        val currentWhitelist = session.getWhitelistPackages().toMutableList()
        if (currentWhitelist.remove(packageName)) {
            val newWhitelistJson = json.encodeToString(currentWhitelist)
            DbSet.focusSessionDao.updateWhitelist(newWhitelistJson)
            LogUtils.d("Removed $packageName from focus whitelist")
        }
    }

    /**
     * 鍚戜細璇濈櫧鍚嶅崟娣诲姞搴旂敤锛堜粎鍦ㄦ湭閿佸畾鏃跺厑璁革級
     */
    suspend fun addToWhitelist(packageName: String): Boolean {
        val session = cachedSession ?: return false
        if (!session.isActive) return false

        // 閿佸畾鏃朵笉鍏佽娣诲姞
        if (session.isCurrentlyLocked) {
            return false
        }

        val currentWhitelist = session.getWhitelistPackages().toMutableList()
        if (!currentWhitelist.contains(packageName)) {
            currentWhitelist.add(packageName)
            val newWhitelistJson = json.encodeToString(currentWhitelist)
            DbSet.focusSessionDao.updateWhitelist(newWhitelistJson)
            LogUtils.d("Added $packageName to focus whitelist")
        }
        return true
    }

    /**
     * 娓呴櫎鍐峰嵈鏃堕棿缂撳瓨
     */
    fun clearCooldown() {
        cooldownMap.clear()
        sdpRuntimeFeatureCoordinator.invalidateCurrentApp("focus-overlay-mount-failed")
    }
}
