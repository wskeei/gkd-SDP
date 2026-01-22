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
import li.songe.gkd.sdp.a11y.topActivityFlow
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.json
import java.util.concurrent.ConcurrentHashMap

object FocusModeEngine {
    
    /**
     * 检查当前是否处于专注模式（会话有效或定时规则生效）
     */
    private fun isInFocusMode(): Boolean {
        // 检查手动会话
        val session = cachedSession
        if (session?.isValidNow() == true) return true
        // 检查定时规则
        return cachedRules.any { it.isActiveNow() }
    }
    
    /**
     * 检查应用是否在白名单中
     * GKD-SDP 应用本身默认在白名单中，以便用户可以随时访问设置
     */
    private fun isWhitelisted(packageName: String): Boolean {
        // GKD-SDP 应用始终在白名单中（但用户可以在 UI 中手动移除）
        if (packageName == META.appId) {
            return true
        }
        return currentWhitelistFlow.value.contains(packageName)
    }
    
    /**
     * 递归查找特定类名的节点
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
     * 优先使用快捷方式 Intent 直接跳转，如无快捷方式则显示提示悬浮窗引导手动跳转
     */
    fun startWechatJump(wechatId: String, contactName: String = "", shortcutId: String = "") {
        appScope.launch(Dispatchers.Main) {
            try {
                // 保存目标信息
                targetWechatId = wechatId
                pendingContactName = contactName
                
                LogUtils.d(TAG, "Starting jump for $contactName ($wechatId), shortcut=${shortcutId.take(20)}...")
                
                // 如果有快捷方式 ID，直接使用 Intent 跳转
                if (shortcutId.isNotBlank()) {
                    val success = openWechatByShortcut(shortcutId)
                    if (success) {
                        LogUtils.d(TAG, "Shortcut jump successful")
                        return@launch
                    }
                    LogUtils.d(TAG, "Shortcut jump failed, falling back to manual")
                }
                
                // 无快捷方式或跳转失败，使用手动跳转
                startManualWechatJump(wechatId, contactName)
                
            } catch (e: Exception) {
                LogUtils.d(TAG, "Failed to start jump: ${e.message}")
                android.widget.Toast.makeText(app, "跳转失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 使用微信桌面快捷方式 Intent 直接打开联系人聊天
     */
    private fun openWechatByShortcut(shortcutId: String): Boolean {
        return try {
            val intent = Intent("com.tencent.mm.action.BIZSHORTCUT").apply {
                component = android.content.ComponentName(
                    "com.tencent.mm",
                    "com.tencent.mm.ui.LauncherUI"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_FORWARD_RESULT
                putExtra("LauncherUI.Shortcut.Username", shortcutId)
                putExtra("LauncherUI.From.Biz.Shortcut", true)
                putExtra("app_shortcut_custom_id", shortcutId)
            }
            app.startActivity(intent)
            true
        } catch (e: Exception) {
            LogUtils.d(TAG, "openWechatByShortcut failed: ${e.message}")
            false
        }
    }
    
    /**
     * 手动跳转流程（无快捷方式时使用）
     */
    private suspend fun startManualWechatJump(wechatId: String, contactName: String) {
        // 1. 启动微信
        val intent = app.packageManager.getLaunchIntentForPackage("com.tencent.mm")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            app.startActivity(intent)
        } else {
            LogUtils.d(TAG, "WeChat not installed")
            android.widget.Toast.makeText(app, "微信未安装", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // 2. 延迟一点后启动提示悬浮窗（确保微信已启动）
        delay(500)
        
        val hintIntent = Intent(app, li.songe.gkd.sdp.service.WechatJumpHintService::class.java).apply {
            putExtra(li.songe.gkd.sdp.service.WechatJumpHintService.EXTRA_CONTACT_NAME, contactName.ifEmpty { wechatId })
            putExtra(li.songe.gkd.sdp.service.WechatJumpHintService.EXTRA_WECHAT_ID, wechatId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startService(hintIntent)
    }
    
    // 保存待查找的联系人名称（用于验证）
    private var pendingContactName: String? = null
    
    /**
     * 检查当前是否在目标联系人的聊天页面
     * 使用多种方式检测：
     * 1. Activity 名称包含 Chatting 相关关键词
     * 2. UI 特征：有输入框、发送按钮等
     * 3. 标题栏匹配目标联系人
     */
    fun checkIfOnTargetChat(wechatId: String, contactName: String): Boolean {
        val service = A11yService.instance ?: return false
        val rootNode = service.rootInActiveWindow ?: return false
        
        // 获取当前 Activity
        val currentActivity = topActivityFlow.value.activityId ?: ""
        LogUtils.d("$TAG: checkIfOnTargetChat - activity: $currentActivity")
        
        // 方法1：检查 Activity 名称
        val isChatActivity = currentActivity.contains("Chatting", ignoreCase = true) ||
                currentActivity.contains("Chat", ignoreCase = true) ||
                currentActivity.contains("BaseChatUI", ignoreCase = true)
        
        // 方法2：检查 UI 特征（微信聊天页特征）
        // 聊天页通常有：输入框（EditText）、语音按钮、更多功能按钮等
        val hasInputBox = rootNode.findAccessibilityNodeInfosByText("按住 说话").isNotEmpty() ||
                rootNode.findAccessibilityNodeInfosByText("发送").isNotEmpty() ||
                hasClassNode(rootNode, "android.widget.EditText", 8)
        
        // 方法3：通过页面结构判断 - 聊天页底部有输入区域
        val hasBottomInput = checkForBottomInputArea(rootNode)
        
        val looksLikeChat = isChatActivity || hasInputBox || hasBottomInput
        LogUtils.d("$TAG: Chat detection - activity=$isChatActivity, inputBox=$hasInputBox, bottomInput=$hasBottomInput, final=$looksLikeChat")
        
        if (!looksLikeChat) {
            LogUtils.d("$TAG: Not in chat UI ($currentActivity)")
            return false
        }
        
        // 提取当前聊天对象名称
        val chatTitle = extractChatTitle(rootNode)
        LogUtils.d("$TAG: Checking target chat, expected: $contactName (id: $wechatId), actual: $chatTitle")
        
        if (chatTitle == null) {
            return false
        }
        
        // 检查名称是否匹配（忽略特殊字符）
        val normalizedExpected = contactName.trim()
        val normalizedActual = chatTitle.trim()
        
        if (normalizedActual == normalizedExpected) {
            return true
        }
        
        // 尝试通过微信号匹配（查询数据库）
        val actualWechatId = try {
            kotlinx.coroutines.runBlocking {
                DbSet.wechatContactDao.findIdByName(normalizedActual)
            }
        } catch (e: Exception) {
            null
        }
        
        return actualWechatId == wechatId
    }
    
    /**
     * 触发微信拦截（用于跳转失败时）
     */
    fun triggerWechatIntercept() {
        val service = A11yService.instance ?: return
        LogUtils.d("$TAG: Triggering wechat intercept")
        
        // 显示专注模式拦截界面
        showFocusOverlay(service, "com.tencent.mm")
    }

    private fun resetJumpState() {
        jumpState.value = JumpState.IDLE
        targetWechatId = null
        jumpJob?.cancel()
        jumpJob = null
    }

    /**
     * 处理应用切换事件
     */
    fun onAppChanged(packageName: String, service: A11yService) {
        if (!enabledFlow.value) return
        if (!isInFocusMode()) return

        // 如果正在自动跳转中，且是微信
        if (isJumpInProgress && packageName == "com.tencent.mm") {
            // 安全检查：防止跳转过程中用户去往其他页面
            // 允许的页面：LauncherUI(主界面), FTSMainUI(搜索页), ChattingUI(聊天页)
            // 拒绝的页面：SnsTimeLineUI(朋友圈), Finder(视频号) 等
            val currentClass = topActivityFlow.value.activityId
            if (currentClass != null) {
                if (currentClass.contains("SnsTimeLineUI") || 
                    currentClass.contains("Finder") || 
                    currentClass.contains("GameCenterUI")) {
                    LogUtils.d(TAG, "User strayed to $currentClass during jump, aborting")
                    resetJumpState()
                    // 继续执行下面的拦截逻辑
                } else {
                    LogUtils.d(TAG, "Jump in progress ($currentClass), allowing WeChat access")
                    return
                }
            } else {
                return // 无法获取 Activity，暂时放行
            }
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
     * 处理无障碍事件，驱动状态机
     */
    fun onA11yEvent(event: android.view.accessibility.AccessibilityEvent) {
        if (!isJumpInProgress) return
        if (event.packageName != "com.tencent.mm") return

        // 使用 rootInActiveWindow 而不是 event.source，确保能获取完整界面
        val root = A11yService.instance?.rootInActiveWindow ?: return
        val displayMetrics = app.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        when (jumpState.value) {
            JumpState.WAIT_FOR_MAIN -> {
                LogUtils.d(TAG, "Checking for Search button in WAIT_FOR_MAIN...")
                // 目标：找到"搜索"按钮并点击
                // 1. 查找所有描述或文本为"搜索"的节点
                val searchNodes = root.findAccessibilityNodeInfosByText("搜索")
                
                for (node in searchNodes) {
                    // 过滤策略：
                    // 1. 必须在屏幕顶部区域 (Top < 300px)
                    // 2. 必须在屏幕右侧区域 (Right > width * 0.6) - 微信主界面的搜索通常在右上角
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    
                    if (bounds.top > 300) {
                        LogUtils.d(TAG, "Node '${node.text}' ignored: too low (top=${bounds.top})")
                        continue
                    }
                    if (bounds.right < screenWidth * 0.6) {
                         LogUtils.d(TAG, "Node '${node.text}' ignored: too left (right=${bounds.right})")
                         continue
                    }

                    // 2. 向上寻找可点击的父节点
                    var target: AccessibilityNodeInfo? = node
                    while (target != null) {
                        if (target.isClickable) {
                            LogUtils.d(TAG, "Found valid Search button: ${target.className}, clicking...")
                            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            jumpState.value = JumpState.WAIT_FOR_SEARCH
                            return
                        }
                        target = target.parent
                    }
                }
                LogUtils.d(TAG, "Search button not found in current event window")
            }
            JumpState.WAIT_FOR_SEARCH -> {
                LogUtils.d(TAG, "Checking for EditText in WAIT_FOR_SEARCH...")
                // 目标：找到搜索输入框 (EditText)，并粘贴内容
                val editNodes = mutableListOf<AccessibilityNodeInfo>()
                findNodesByClass(root, "android.widget.EditText", editNodes)
                
                val editText = editNodes.firstOrNull()
                if (editText != null) {
                    LogUtils.d(TAG, "Found search input, pasting targetId: $targetWechatId")
                    
                    // 必须先聚焦
                    editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    
                    // 优先使用粘贴
                    val pasteResult = editText.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    if (!pasteResult) {
                        LogUtils.d(TAG, "Paste failed, trying SET_TEXT")
                        // 如果粘贴失败，尝试设置文本
                         val args = android.os.Bundle()
                         args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetWechatId)
                         editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }
                    jumpState.value = JumpState.WAIT_FOR_RESULT
                }
            }
            JumpState.WAIT_FOR_RESULT -> {
                val targetId = targetWechatId ?: return
                LogUtils.d(TAG, "Checking for result '$targetId' in WAIT_FOR_RESULT...")
                
                // 目标：找到包含 "微信号: targetId" 的结果项
                val resultNodes = root.findAccessibilityNodeInfosByText(targetId)
                
                for (n in resultNodes) {
                    LogUtils.d(TAG, "Found potential result node: ${n.text}, checking clickable parent...")
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
                // 这里的处理在点击后已经做了延迟重置
            }
            else -> {}
        }
    }

    /**
     * 微信专项检查 - 严格模式
     * 只允许：
     * 1. 白名单联系人的聊天页面
     * 2. 主界面的"微信"和"通讯录"标签页（用于查找联系人）
     * 3. 搜索页面（用于搜索联系人）
     * 
     * 拦截：
     * 1. 朋友圈、视频号、发现页、游戏、购物等
     * 2. 非白名单联系人的聊天
     * 3. 小程序
     */
    private fun checkWechatAccess(service: A11yService): Boolean {
        val whitelist = currentWechatWhitelistFlow.value
        if (whitelist.isEmpty()) {
            LogUtils.d("$TAG: WeChat whitelist is empty, blocking all")
            return false
        }

        val rootNode = service.rootInActiveWindow ?: return false
        
        // 获取当前 Activity
        val currentActivity = topActivityFlow.value.activityId ?: ""
        LogUtils.d("$TAG: Checking WeChat access, activity: $currentActivity")
        
        // 1. 检查是否在小程序中 - 直接拦截
        if (currentActivity.contains("AppBrandUI") || 
            currentActivity.contains("WeAppUI") ||
            currentActivity.contains("miniprogram", ignoreCase = true)) {
            LogUtils.d("$TAG: In mini-program, blocking")
            return false
        }
        
        // 2. 检查是否在朋友圈/视频号/发现页 - 直接拦截
        if (currentActivity.contains("SnsTimeLineUI") ||  // 朋友圈
            currentActivity.contains("SnsUploadUI") ||    // 发朋友圈
            currentActivity.contains("FinderHomeUI") ||   // 视频号
            currentActivity.contains("FinderLiveUI") ||   // 视频号直播
            currentActivity.contains("FinderFeedUI") ||   // 视频号Feed
            currentActivity.contains("WxaLauncherUI") ||  // 小程序启动器
            currentActivity.contains("GameCenterUI") ||   // 游戏中心
            currentActivity.contains("ShoppingUI") ||     // 购物
            currentActivity.contains("ChannelUI")) {      // 频道
            LogUtils.d("$TAG: In restricted page ($currentActivity), blocking")
            return false
        }
        
        // 3. 检查是否在聊天界面 (ChattingUI)
        if (currentActivity.contains("ChattingUI")) {
            // 在聊天界面，提取聊天对象并检查白名单
            val chatTitle = extractChatTitle(rootNode)
            LogUtils.d("$TAG: In chat UI, title: $chatTitle")
            
            if (chatTitle == null) {
                return false
            }
            
            // 排除系统服务和订阅号
            if (chatTitle == "订阅号消息" || chatTitle == "服务通知" || 
                chatTitle == "文件传输助手" || chatTitle == "微信团队") {
                LogUtils.d("$TAG: System chat, blocking")
                return false
            }
            
            // 查询该名称对应的微信号
            val wechatId = try {
                kotlinx.coroutines.runBlocking {
                    DbSet.wechatContactDao.findIdByName(chatTitle)
                }
            } catch (e: Exception) {
                LogUtils.d("$TAG: Failed to query wechat contact: ${e.message}")
                null
            }

            if (wechatId == null) {
                // 未找到对应联系人，尝试用名称直接匹配白名单
                // 因为白名单可能包含备注名或昵称
                val isInWhitelistByName = try {
                    kotlinx.coroutines.runBlocking {
                        val contact = DbSet.wechatContactDao.findByDisplayName(chatTitle)
                        contact != null && whitelist.contains(contact.wechatId)
                    }
                } catch (e: Exception) {
                    false
                }
                
                if (!isInWhitelistByName) {
                    LogUtils.d("$TAG: Contact '$chatTitle' not in whitelist, blocking")
                    return false
                }
                return true
            }

            // 检查是否在白名单
            val allowed = whitelist.contains(wechatId)
            LogUtils.d("$TAG: Contact '$chatTitle' (id: $wechatId) whitelist check: $allowed")
            return allowed
        }
        
        // 4. 检查主界面 - 只允许在"微信"聊天列表和"通讯录"Tab
        if (currentActivity.contains("LauncherUI")) {
            // 检查当前选中的Tab
            // 如果能找到"发现"或"我"Tab处于选中状态，则拦截
            
            // 检测是否在聊天列表或通讯录Tab
            // 通过检测页面特征：聊天列表有搜索框，通讯录有字母索引
            val hasSearch = rootNode.findAccessibilityNodeInfosByText("搜索").isNotEmpty()
            val hasContacts = rootNode.findAccessibilityNodeInfosByText("通讯录").isNotEmpty()
            val hasNewFriend = rootNode.findAccessibilityNodeInfosByText("新的朋友").isNotEmpty()
            
            // 检测是否在"发现"Tab - 有"朋友圈"、"视频号"等入口
            val hasMoments = rootNode.findAccessibilityNodeInfosByText("朋友圈").isNotEmpty()
            val hasChannels = rootNode.findAccessibilityNodeInfosByText("视频号").isNotEmpty()
            
            // 如果能看到朋友圈和视频号入口，说明在发现页
            if (hasMoments && hasChannels) {
                LogUtils.d("$TAG: In Discover tab (朋友圈+视频号 visible), blocking")
                return false
            }
            
            // 检测是否在"我"Tab - 有"设置"、"收藏"等入口
            val hasSettings = rootNode.findAccessibilityNodeInfosByText("设置").isNotEmpty()
            val hasFavorites = rootNode.findAccessibilityNodeInfosByText("收藏").isNotEmpty()
            if (hasSettings && hasFavorites) {
                LogUtils.d("$TAG: In 'Me' tab, blocking")
                return false
            }
            
            // 允许聊天列表和通讯录
            LogUtils.d("$TAG: In launcher (chat/contacts), allowing")
            return true
        }
        
        // 5. 允许搜索页面 (用于搜索联系人)
        if (currentActivity.contains("FTSMainUI") || 
            currentActivity.contains("ContactSearchUI") ||
            currentActivity.contains("SearchUI")) {
            LogUtils.d("$TAG: In search UI, allowing")
            return true
        }
        
        // 6. 允许联系人详情页 (用于发起聊天)
        if (currentActivity.contains("ContactInfoUI") ||
            currentActivity.contains("FriendProfileUI")) {
            LogUtils.d("$TAG: In contact info UI, allowing")
            return true
        }
        
        // 7. 其他未知页面 - 默认拦截
        LogUtils.d("$TAG: Unknown activity ($currentActivity), blocking by default")
        return false
    }

    /**
     * 提取聊天标题栏的联系人名称
     * 微信聊天界面的标题通常在导航栏中，特征：
     * 1. 在屏幕顶部区域（Y < 300dp）
     * 2. 是一个 TextView
     * 3. 不是返回按钮、设置按钮等
     */
    private fun extractChatTitle(rootNode: AccessibilityNodeInfo): String? {
        val displayMetrics = app.resources.displayMetrics
        val density = displayMetrics.density
        val maxY = (200 * density).toInt()  // 200dp 以内
        
        val candidates = mutableListOf<Pair<String, android.graphics.Rect>>()
        
        // 收集顶部区域的所有文本节点
        collectTopTextNodes(rootNode, candidates, maxY)
        
        // 过滤掉不可能是标题的内容
        val filtered = candidates.filter { (text, _) ->
            text.isNotEmpty() &&
            text != "返回" &&
            text != "搜索" &&
            text != "..." &&
            text != "⋮" &&
            !text.startsWith("微信") &&  // 排除底部Tab文字
            !text.contains("通讯录") &&
            !text.contains("发现") &&
            !text.matches(Regex("^\\d+$")) &&  // 纯数字
            !text.matches(Regex("^\\d{1,2}:\\d{2}$")) &&  // 时间格式
            text.length in 1..30  // 合理的名称长度
        }
        
        // 按 X 坐标排序，取居中的（标题通常在中间）
        val sorted = filtered.sortedBy { it.second.left }
        
        // 如果有多个候选，优先选择靠中间的
        val screenWidth = displayMetrics.widthPixels
        val centerX = screenWidth / 2
        val best = sorted.minByOrNull { 
            kotlin.math.abs(it.second.centerX() - centerX)
        }
        
        val result = best?.first
        LogUtils.d("$TAG: extractChatTitle candidates: ${filtered.map { it.first }}, selected: $result")
        return result
    }
    
    /**
     * 收集顶部区域的文本节点
     */
    private fun collectTopTextNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<Pair<String, android.graphics.Rect>>,
        maxY: Int,
        depth: Int = 0
    ) {
        if (depth > 10) return  // 限制递归深度
        
        // 检查节点位置
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        
        // 只处理顶部区域的节点
        if (bounds.top > maxY) return
        
        // 如果是文本节点，添加到结果
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && bounds.top < maxY) {
            result.add(text to bounds)
        }
        
        // 递归处理子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTopTextNodes(child, result, maxY, depth + 1)
        }
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
     * 检查是否存在指定类名的节点
     */
    private fun hasClassNode(node: AccessibilityNodeInfo, className: String, maxDepth: Int, depth: Int = 0): Boolean {
        if (depth > maxDepth) return false
        
        if (node.className?.toString() == className) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasClassNode(child, className, maxDepth, depth + 1)) {
                return true
            }
        }
        return false
    }
    
    /**
     * 检查是否有底部输入区域（聊天页特征）
     */
    private fun checkForBottomInputArea(rootNode: AccessibilityNodeInfo): Boolean {
        val displayMetrics = app.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomThreshold = (screenHeight * 0.7).toInt()  // 底部 30% 区域
        
        // 查找底部区域的 EditText 或输入相关节点
        return hasBottomEditText(rootNode, bottomThreshold)
    }
    
    /**
     * 检查底部是否有 EditText
     */
    private fun hasBottomEditText(node: AccessibilityNodeInfo, bottomThreshold: Int, depth: Int = 0): Boolean {
        if (depth > 15) return false
        
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        
        // 检查是否在底部区域且是输入相关控件
        if (bounds.top > bottomThreshold) {
            val className = node.className?.toString() ?: ""
            if (className.contains("EditText") || className.contains("Input")) {
                return true
            }
            // 检查是否有微信特有的输入区域文本
            val text = node.text?.toString() ?: ""
            if (text == "按住 说话" || text.contains("输入") || node.isEditable) {
                return true
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasBottomEditText(child, bottomThreshold, depth + 1)) {
                return true
            }
        }
        return false
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
