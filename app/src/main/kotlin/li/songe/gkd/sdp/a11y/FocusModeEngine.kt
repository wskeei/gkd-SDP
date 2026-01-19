package li.songe.gkd.sdp.a11y

import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.data.FocusSession
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.FocusOverlayService
import li.songe.gkd.sdp.notif.focusEndNotif
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.json
import java.util.concurrent.ConcurrentHashMap

object FocusModeEngine {
    private const val TAG = "FocusModeEngine"

    // 缓存的规则和会话
    private var cachedRules: List<FocusRule> = emptyList()
    private var cachedSession: FocusSession? = null

    // 冷却时间缓存，防止重复触发
    private val cooldownMap = ConcurrentHashMap<String, Long>()
    private const val COOLDOWN_MS = 2000L  // 2秒冷却时间

    // 微信跳转状态机
    enum class JumpState {
        IDLE,               // 空闲
        WAIT_FOR_MAIN,      // 已启动微信，等待主界面
        WAIT_FOR_SEARCH,    // 已点击搜索，等待搜索页
        WAIT_FOR_RESULT,    // 已输入内容，等待搜索结果
        COMPLETED           // 检测到聊天页，完成
    }

    private val jumpState = MutableStateFlow(JumpState.IDLE)
    private var jumpJob: kotlinx.coroutines.Job? = null
    private var targetWechatId: String? = null

    val isJumpInProgress: Boolean
        get() = jumpState.value != JumpState.IDLE

    // 是否启用专注模式引擎
    val enabledFlow = MutableStateFlow(true)

    // 当前活跃的专注模式状态
    val activeSessionFlow = DbSet.focusSessionDao.getSession()
        .stateIn(appScope, SharingStarted.Eagerly, null)

    // 所有规则
    val allRulesFlow = DbSet.focusRuleDao.queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 启用的规则
    val enabledRulesFlow = DbSet.focusRuleDao.queryEnabled()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 是否有活跃的专注模式（会话有效或规则在时间段内）
    val isActiveFlow = combine(activeSessionFlow, enabledRulesFlow) { session, rules ->
        // 检查手动会话
        if (session?.isValidNow() == true) return@combine true
        // 检查定时规则
        rules.any { it.isActiveNow() }
    }.stateIn(appScope, SharingStarted.Eagerly, false)

    // 当前生效的白名单
    val currentWhitelistFlow = combine(activeSessionFlow, enabledRulesFlow) { session, rules ->
        getEffectiveWhitelist(session, rules)
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 当前生效的微信白名单
    val currentWechatWhitelistFlow = combine(activeSessionFlow, enabledRulesFlow) { session, rules ->
        getEffectiveWechatWhitelist(session, rules)
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    // 当前生效的拦截消息
    val currentMessageFlow = combine(activeSessionFlow, enabledRulesFlow) { session, rules ->
        getEffectiveMessage(session, rules)
    }.stateIn(appScope, SharingStarted.Eagerly, "专注当下")

    init {
        // 监听规则和会话变化
        appScope.launch(Dispatchers.IO) {
            combine(
                DbSet.focusRuleDao.queryEnabled(),
                DbSet.focusSessionDao.getSession()
            ) { rules, session ->
                rules to session
            }.collect { (rules, session) ->
                cachedRules = rules
                cachedSession = session
                if (META.debuggable) {
                    Log.d(TAG, "Rules updated: ${rules.size}, Session: $session")
                }
            }
        }

        // 监听会话过期并自动结束
        appScope.launch(Dispatchers.IO) {
            while (true) {
                delay(30_000L)  // 每 30 秒检查一次

                val session = cachedSession
                if (session != null && session.isActive && !session.isValidNow()) {
                    // 会话已过期，停用
                    DbSet.focusSessionDao.deactivate()
                    LogUtils.d("Focus session expired, deactivated")

                    // 关闭拦截界面
                    closeFocusOverlay()

                    // 发送结束通知
                    focusEndNotif.notifySelf()
                }
            }
        }
    }

    /**
     * 关闭专注模式拦截界面
     */
    private fun closeFocusOverlay() {
        try {
            val intent = Intent(app, FocusOverlayService::class.java)
            app.stopService(intent)
            LogUtils.d("Focus overlay service stopped")
        } catch (e: Exception) {
            LogUtils.d("Failed to stop focus overlay: ${e.message}")
        }
    }

    /**
     * 获取当前有效的白名单
     */
    private fun getEffectiveWhitelist(session: FocusSession?, rules: List<FocusRule>): List<String> {
        // 优先使用手动会话的白名单
        if (session?.isValidNow() == true) {
            return session.getWhitelistPackages()
        }
        // 使用当前生效规则的白名单
        val activeRule = rules.firstOrNull { it.isActiveNow() }
        return activeRule?.getWhitelistPackages() ?: emptyList()
    }

    /**
     * 获取当前有效的拦截消息
     */
    private fun getEffectiveMessage(session: FocusSession?, rules: List<FocusRule>): String {
        if (session?.isValidNow() == true) {
            return session.interceptMessage
        }
        val activeRule = rules.firstOrNull { it.isActiveNow() }
        return activeRule?.interceptMessage ?: "专注当下"
    }

    /**
     * 获取当前有效的微信白名单
     */
    private fun getEffectiveWechatWhitelist(session: FocusSession?, rules: List<FocusRule>): List<String> {
        // 优先使用手动会话的白名单
        if (session?.isValidNow() == true) {
            return session.getWechatWhitelist()
        }
        // 使用当前生效规则的白名单
        val activeRule = rules.firstOrNull { it.isActiveNow() }
        return activeRule?.getWechatWhitelist() ?: emptyList()
    }

    /**
     * 开始微信跳转流程
     */
    fun startWechatJump(wechatId: String) {
        if (isJumpInProgress) {
            LogUtils.d(TAG, "Jump already in progress")
            return
        }

        appScope.launch(Dispatchers.Main) {
            try {
                // 1. 复制微信号到剪贴板
                val clipboard = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("wechat_id", wechatId)
                clipboard.setPrimaryClip(clip)

                // 2. 初始化状态
                targetWechatId = wechatId
                jumpState.value = JumpState.WAIT_FOR_MAIN
                LogUtils.d(TAG, "Starting jump for $wechatId, state: WAIT_FOR_MAIN")

                // 3. 启动微信
                val intent = app.packageManager.getLaunchIntentForPackage("com.tencent.mm")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    app.startActivity(intent)
                } else {
                    LogUtils.d(TAG, "WeChat not installed")
                    resetJumpState()
                    return@launch
                }

                // 4. 设置超时任务 (15秒)
                jumpJob?.cancel()
                jumpJob = launch {
                    delay(15_000L)
                    if (isJumpInProgress) {
                        LogUtils.d(TAG, "Jump timeout, resetting state")
                        // 如果超时，且当前不在聊天界面，可能需要恢复拦截
                        // 但由于我们在 onAppChanged 中有 checkWechatAccess，如果用户还在微信，会根据逻辑判断是否拦截
                        resetJumpState()
                        // 显示提示
                        launch(Dispatchers.Main) {
                            android.widget.Toast.makeText(app, "自动跳转超时，请手动查找", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtils.d(TAG, "Failed to start jump: ${e.message}")
                resetJumpState()
            }
        }
    }

    private fun resetJumpState() {
        jumpState.value = JumpState.IDLE
        targetWechatId = null
        jumpJob?.cancel()
        jumpJob = null
    }

    /**
     * 处理无障碍事件，驱动状态机
     */
    fun onA11yEvent(event: android.view.accessibility.AccessibilityEvent) {
        if (!isJumpInProgress) return
        if (event.packageName != "com.tencent.mm") return

        val node = event.source ?: return
        // 注意: node 可能会被回收，需要小心使用
        // 这里的逻辑尽量简单快速

        when (jumpState.value) {
            JumpState.WAIT_FOR_MAIN -> {
                // 目标：找到"搜索"按钮并点击
                // 微信主界面通常有 "搜索" 按钮 (contentDescription="搜索" 或 text="搜索")
                val searchNodes = node.findAccessibilityNodeInfosByText("搜索")
                // 过滤出真正的搜索按钮（通常是 ImageButton 或 TextView，且可点击）
                // 微信主界面的搜索通常在右上角
                val searchBtn = searchNodes.firstOrNull { 
                    it.isClickable && (it.className.contains("ImageView") || it.className.contains("TextView")) 
                }
                
                if (searchBtn != null) {
                    LogUtils.d(TAG, "Found search button, clicking...")
                    searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    jumpState.value = JumpState.WAIT_FOR_SEARCH
                }
            }
            JumpState.WAIT_FOR_SEARCH -> {
                // 目标：找到搜索输入框 (EditText)，并粘贴内容
                val editNodes = mutableListOf<AccessibilityNodeInfo>()
                findNodesByClass(node, "android.widget.EditText", editNodes)
                
                val editText = editNodes.firstOrNull()
                if (editText != null) {
                    LogUtils.d(TAG, "Found search input, pasting...")
                    // 优先使用粘贴
                    val pasteResult = editText.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    if (!pasteResult) {
                        // 如果粘贴失败，尝试设置文本
                         val args = android.os.Bundle()
                         args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetWechatId)
                         editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }
                    jumpState.value = JumpState.WAIT_FOR_RESULT
                }
            }
            JumpState.WAIT_FOR_RESULT -> {
                // 目标：找到包含 "微信号: targetId" 的结果项
                // 微信搜索结果中，精确匹配的微信号通常会显示 "微信号: xxx"
                val targetId = targetWechatId ?: return
                // 搜索文本可能分散，我们查找包含微信号的节点
                val resultNodes = node.findAccessibilityNodeInfosByText(targetId)
                
                for (n in resultNodes) {
                    // 检查父节点是否可点击，或者自己可点击
                    // 通常是一个列表项
                    var clickTarget: AccessibilityNodeInfo? = n
                    while (clickTarget != null && !clickTarget.isClickable) {
                        clickTarget = clickTarget.parent
                    }
                    
                    if (clickTarget != null) {
                        LogUtils.d(TAG, "Found result, clicking...")
                        clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        jumpState.value = JumpState.COMPLETED
                        
                        // 稍微延迟后重置状态，确保跳转完成
                        appScope.launch {
                            delay(2000)
                            resetJumpState()
                        }
                        return
                    }
                }
            }
            JumpState.COMPLETED -> {
                // 这里的处理在点击后已经做了延迟重置，也可以在这里检测 ChattingUI
                // 暂时留空
            }
            else -> {}
        }
    }

    private fun findNodesByClass(root: AccessibilityNodeInfo, className: String, result: MutableList<AccessibilityNodeInfo>) {
        if (root.className?.toString() == className) {
            result.add(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodesByClass(child, className, result)
        }
    }

    /**
     * 检查当前是否处于专注模式
     */
    fun isInFocusMode(): Boolean {
        if (!enabledFlow.value) return false

        // 检查手动会话
        val session = cachedSession
        if (session?.isValidNow() == true) return true

        // 检查定时规则
        return cachedRules.any { it.isActiveNow() }
    }

    /**
     * 检查包名是否在白名单中
     */
    fun isWhitelisted(packageName: String): Boolean {
        // 始终允许系统 UI
        if (packageName == "com.android.systemui") return true
        // 始终允许本应用
        if (packageName == META.appId) return true

        // 检查手动会话的白名单
        val session = cachedSession
        if (session?.isValidNow() == true) {
            return session.isInWhitelist(packageName)
        }

        // 检查当前生效规则的白名单
        val activeRule = cachedRules.firstOrNull { it.isActiveNow() }
        return activeRule?.getWhitelistPackages()?.contains(packageName) == true
    }

    /**
     * 处理应用切换事件
     */
    fun onAppChanged(packageName: String, service: A11yService) {
        if (!enabledFlow.value) return
        if (!isInFocusMode()) return

        // 如果正在自动跳转中，且是微信，暂时放行
        if (isJumpInProgress && packageName == "com.tencent.mm") {
            LogUtils.d(TAG, "Jump in progress, allowing WeChat access")
            return
        }

        // 检查冷却时间
        val now = System.currentTimeMillis()
        val lastTriggerTime = cooldownMap[packageName] ?: 0L
        if (now - lastTriggerTime < COOLDOWN_MS) {
            return
        }

        // 微信专项检查
        if (packageName == "com.tencent.mm") {
            if (checkWechatAccess(service)) {
                return  // 允许访问
            }
        } else {
            // 其他应用：检查是否在白名单
            if (isWhitelisted(packageName)) {
                if (META.debuggable) {
                    Log.d(TAG, "App $packageName is whitelisted, allowing")
                }
                return
            }
        }

        // 触发拦截
        cooldownMap[packageName] = now
        LogUtils.d("Focus mode blocking: $packageName")
        showFocusOverlay(service, packageName)
    }

    /**
     * 微信专项检查
     */
    private fun checkWechatAccess(service: A11yService): Boolean {
        val whitelist = currentWechatWhitelistFlow.value
        if (whitelist.isEmpty()) return false

        val rootNode = service.rootInActiveWindow ?: return false

        // 1. 允许微信主界面 (LauncherUI)
        // 特征：底部包含 "微信", "通讯录", "发现", "我"
        val hasMainTabs = rootNode.findAccessibilityNodeInfosByText("通讯录").isNotEmpty() &&
                rootNode.findAccessibilityNodeInfosByText("发现").isNotEmpty()
        if (hasMainTabs) {
            return true
        }

        // 2. 读取聊天标题栏的联系人名称
        val chatTitle = extractChatTitle(rootNode) ?: return false
        
        // 排除常见非聊天页面
        if (chatTitle == "朋友圈" || chatTitle == "视频号" || chatTitle == "订阅号消息" || chatTitle == "服务号") {
            return false
        }

        // 3. 查询该名称对应的微信号（同步查询）
        val wechatId = try {
            kotlinx.coroutines.runBlocking {
                DbSet.wechatContactDao.findIdByName(chatTitle)
            }
        } catch (e: Exception) {
            LogUtils.d("$TAG: Failed to query wechat contact: ${e.message}")
            null
        }

        if (wechatId == null) {
            // 未找到对应联系人，拦截
            return false
        }

        // 4. 检查是否在白名单
        return whitelist.contains(wechatId)
    }

    /**
     * 提取聊天标题栏的联系人名称
     */
    private fun extractChatTitle(rootNode: AccessibilityNodeInfo): String? {
        // 查找标题栏（通常在顶部）
        val textNodes = mutableListOf<AccessibilityNodeInfo>()
        findTextNodes(rootNode, textNodes, maxDepth = 5)

        // 返回第一个非空文本（通常是联系人名称）
        return textNodes.firstOrNull()?.text?.toString()?.trim()
    }

    private fun findTextNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth) return

        if (!node.text.isNullOrEmpty()) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findTextNodes(child, result, maxDepth, currentDepth + 1)
        }
    }

    /**
     * 显示专注模式全屏拦截界面
     * @param overrideWhitelist 直接指定白名单（用于会话刚创建时 Flow 还未更新的情况）
     * @param overrideMessage 直接指定拦截消息
     * @param overrideEndTime 直接指定结束时间
     * @param overrideIsLocked 直接指定锁定状态
     * @param overrideWechatWhitelist 直接指定微信白名单
     */
    private fun showFocusOverlay(
        service: A11yService,
        packageName: String,
        overrideWhitelist: List<String>? = null,
        overrideMessage: String? = null,
        overrideEndTime: Long? = null,
        overrideIsLocked: Boolean? = null,
        overrideWechatWhitelist: List<String>? = null
    ) {
        try {
            val message = overrideMessage ?: currentMessageFlow.value
            val whitelist = overrideWhitelist ?: currentWhitelistFlow.value
            val wechatWhitelist = overrideWechatWhitelist ?: currentWechatWhitelistFlow.value
            val session = cachedSession
            val activeRule = cachedRules.firstOrNull { it.isActiveNow() }
            val isLocked = overrideIsLocked ?: (session?.isCurrentlyLocked == true || activeRule?.isCurrentlyLocked == true)
            val endTime = overrideEndTime ?: session?.endTime ?: 0L

            val intent = Intent(service, FocusOverlayService::class.java).apply {
                putExtra("message", message)
                putExtra("whitelist", json.encodeToString(whitelist))
                putExtra("wechatWhitelist", json.encodeToString(wechatWhitelist))
                putExtra("blockedApp", packageName)
                putExtra("isLocked", isLocked)
                putExtra("endTime", endTime)
            }
            service.startService(intent)
        } catch (e: Exception) {
            LogUtils.d("Failed to show focus overlay: ${e.message}")
        }
    }

    /**
     * 手动开启专注模式
     */
    suspend fun startManualSession(
        durationMinutes: Int,
        whitelistApps: List<String>,
        wechatWhitelist: List<String> = emptyList(), // Added parameter
        interceptMessage: String = "专注当下",
        isLocked: Boolean = false,
        lockDurationMinutes: Int = 0
    ) {
        val now = System.currentTimeMillis()
        val endTime = now + durationMinutes * 60 * 1000L
        val lockEndTime = if (isLocked) now + lockDurationMinutes * 60 * 1000L else 0L

        val session = FocusSession(
            id = 1,
            isActive = true,
            ruleId = null,
            startTime = now,
            endTime = endTime,
            whitelistApps = json.encodeToString(whitelistApps),
            wechatWhitelist = json.encodeToString(wechatWhitelist), // Save it
            interceptMessage = interceptMessage,
            isManual = true,
            isLocked = isLocked,
            lockEndTime = lockEndTime
        )

        DbSet.focusSessionDao.insert(session)
        LogUtils.d("Manual focus session started: ${durationMinutes}min, whitelist: ${whitelistApps.size} apps, wechat: ${wechatWhitelist.size}")

        // 立即触发拦截界面，直接传递参数（因为 Flow 可能还未更新）
        A11yService.instance?.let { service ->
            showFocusOverlay(
                service = service,
                packageName = "manual_start",
                overrideWhitelist = whitelistApps,
                overrideWechatWhitelist = wechatWhitelist, // Pass it
                overrideMessage = interceptMessage,
                overrideEndTime = endTime,
                overrideIsLocked = isLocked
            )
        }
    }

    /**
     * 停止手动会话
     */
    suspend fun stopManualSession() {
        val session = cachedSession
        if (session?.isManual == true && !session.isCurrentlyLocked) {
            DbSet.focusSessionDao.deactivate()
            LogUtils.d("Manual focus session stopped")
        }
    }

    /**
     * 从会话白名单中移除应用
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
     * 向会话白名单添加应用（仅在未锁定时允许）
     */
    suspend fun addToWhitelist(packageName: String): Boolean {
        val session = cachedSession ?: return false
        if (!session.isActive) return false

        // 锁定时不允许添加
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
     * 清除冷却时间缓存
     */
    fun clearCooldown() {
        cooldownMap.clear()
    }
}
