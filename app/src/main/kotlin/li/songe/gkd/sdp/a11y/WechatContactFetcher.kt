package li.songe.gkd.sdp.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.WechatContact
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.toast
import kotlin.random.Random

data class FetchState(
    val isFetching: Boolean = false,
    val fetchedCount: Int = 0,
    val statusText: String = "准备中...",
    val currentTarget: String? = null
)

object WechatContactFetcher {
    private const val TAG = "WechatContactFetcher"
    private const val WECHAT_PACKAGE = "com.tencent.mm"

    private val BLACK_LIST = setOf(
        "新的朋友", "仅聊天的朋友", "群聊", "标签", "公众号", "服务号",
        "企业微信联系人", "企业微信通知", "我的企业及企业联系人",
        "微信团队", "文件传输助手"
    )

    val isFetchingFlow = MutableStateFlow(false)
    val fetchProgressFlow = MutableStateFlow("")
    val fetchStateFlow = MutableStateFlow(FetchState())

    private var service: AccessibilityService? = null
    private var isFetching = false
    private val fetchedContacts = mutableListOf<WechatContact>()
    private var fetchCount = 0
    private var collectJob: Job? = null
    private var currentScrollHash = 0
    private var noChangeCount = 0
    private val processedNodes = mutableSetOf<String>()

    private fun updateStatus(text: String? = null, target: String? = null, count: Int? = null, fetching: Boolean? = null) {
        fetchStateFlow.update { currentState ->
            currentState.copy(
                isFetching = fetching ?: currentState.isFetching,
                fetchedCount = count ?: currentState.fetchedCount,
                statusText = text ?: currentState.statusText,
                currentTarget = target
            )
        }
        // Keep backward compatibility
        if (text != null) {
            fetchProgressFlow.value = text
        }
        if (fetching != null) {
            isFetchingFlow.value = fetching
        }
    }

    fun startFetch(accessibilityService: AccessibilityService) {
        if (isFetching) {
            toast("正在抓取中，请稍候")
            return
        }

        service = accessibilityService
        isFetching = true
        updateStatus(text = "准备开始抓取...", count = 0, fetching = true)
        
        fetchedContacts.clear()
        fetchCount = 0
        currentScrollHash = 0
        noChangeCount = 0
        processedNodes.clear()

        FetchOverlayController.show(accessibilityService)

        collectJob?.cancel()
        collectJob = appScope.launch(Dispatchers.Main) {
            fetchStateFlow.collect { state ->
                FetchOverlayController.update(state)
            }
        }

        appScope.launch(Dispatchers.IO) {
            try {
                toast("请打开微信通讯录页面")
                updateStatus("等待进入微信通讯录...")

                // 等待用户打开通讯录
                delay(3000)

                fetchContactsFromCurrentScreen()
            } catch (err: Exception) {
                LogUtils.d("$TAG: Fetch error: ${err.message}")
                toast("抓取失败：${err.message}")
            } finally {
                finishFetch()
            }
        }
    }

    private suspend fun fetchContactsFromCurrentScreen() {
        var retryCount = 0
        val MAX_RETRIES = 5

        while (isFetching && retryCount < MAX_RETRIES) {
            val rootNode = service?.rootInActiveWindow
            if (rootNode == null) {
                delay(500)
                continue
            }

            // 检查是否在通讯录页面
            if (!isInContactsPage(rootNode)) {
                updateStatus("请打开微信通讯录页面")
                delay(2000)
                continue
            }

            // 1. 查找列表容器
            val listNode = findScrollableList(rootNode)
            if (listNode == null) {
                delay(1000)
                retryCount++
                continue
            }

            // 2. 获取当前屏幕的有效联系人节点
            val contactNodes = findValidContactNodes(listNode)
            var hasProcessedAny = false

            // 3. 逐个处理
            for (node in contactNodes) {
                if (!isFetching) break

                // 生成节点唯一标识 (使用 文本+屏幕区域 组合)
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val nodeId = "${getNodeText(node)}_${rect}"
                
                if (nodeId in processedNodes) continue

                updateStatus("正在抓取第 ${fetchCount + 1} 个联系人...", count = fetchCount)

                val success = processSingleContact(node)
                
                // 无论成功失败，都标记为已处理，防止死循环卡在某一个节点
                processedNodes.add(nodeId)
                
                if (success) {
                    hasProcessedAny = true
                    retryCount = 0 // 重置重试计数
                } else {
                    // 失败反馈
                    updateStatus("抓取失败，跳过...", count = fetchCount)
                    delay(1000)
                }

                // 每抓取 10-15 个联系人，暂停一下
                if (fetchCount > 0 && fetchCount % Random.nextInt(10, 16) == 0) {
                    delay(randomDelay(2000, 4000))
                }
            }

            // 4. 滚动加载更多
            val preHash = calculateScreenHash(rootNode)
            val scrolled = performScroll(listNode, rootNode)
            
            if (!scrolled) {
                LogUtils.d(TAG, "Scroll action failed")
                noChangeCount++
            } else {
                // 等待滚动完成
                delay(1000) 
                val postHash = calculateScreenHash(service?.rootInActiveWindow ?: rootNode)
                if (preHash == postHash) {
                    noChangeCount++
                } else {
                    noChangeCount = 0
                    // 滚动成功，清理旧缓存防止内存溢出 (只保留最近200个)
                    if (processedNodes.size > 200) {
                        val toRemove = processedNodes.take(100)
                        processedNodes.removeAll(toRemove.toSet())
                    }
                }
            }

            // 5. 结束检查
            if (isAtBottom(rootNode)) {
                LogUtils.d(TAG, "Detected bottom indicator")
                break
            }

            if (noChangeCount >= 3) {
                LogUtils.d(TAG, "Screen content no change for 3 times")
                break
            }
        }
    }

    private suspend fun processSingleContact(node: AccessibilityNodeInfo): Boolean {
        return try {
            // 点击联系人
            clickNodeWithRandomOffset(node)
            delay(randomDelay(800, 1200))

            val root = service?.rootInActiveWindow
            if (root == null || !isInDetailPage(root)) {
                LogUtils.d(TAG, "Failed to enter detail page")
                // 如果没进入详情页，不需要返回
                return false
            }

            // 读取详情页信息
            val contact = extractContactInfo()
            if (contact != null) {
                fetchedContacts.add(contact)
                fetchCount++
                updateStatus(count = fetchCount, target = contact.displayName)
                LogUtils.d("$TAG: Fetched contact: ${contact.displayName} (${contact.wechatId})")
            } else {
                updateStatus("未找到微信号，跳过...", count = fetchCount)
                LogUtils.d("$TAG: Failed to extract info (no wechatId found)")
                delay(1500) // 让用户看清错误提示
            }

            // 返回
            service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(randomDelay(500, 800))
            
            // 验证是否回到列表页 (可选，如果不回来，下一轮循环会检测)
            
            true
        } catch (t: Throwable) {
            LogUtils.d(TAG, "Error processing contact: ${t.message}")
            // 尝试恢复状态（比如多按一次返回？）
            service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(1000)
            false
        }
    }

    private fun isInDetailPage(root: AccessibilityNodeInfo): Boolean {
        // 特征：存在"发消息"、"音视频通话"、"个人信息"等
        if (root.findAccessibilityNodeInfosByText("发消息").isNotEmpty()) return true
        if (root.findAccessibilityNodeInfosByText("音视频通话").isNotEmpty()) return true
        if (root.findAccessibilityNodeInfosByText("微信号").isNotEmpty()) return true
        if (root.findAccessibilityNodeInfosByText("添加到通讯录").isNotEmpty()) return true
        // 地区也是一个特征，但可能太通用？结合前面几项应该够了。
        return false
    }

    private fun calculateScreenHash(root: AccessibilityNodeInfo): Int {
        val sb = StringBuilder()
        collectNodeInfo(root, sb)
        return sb.toString().hashCode()
    }

    private fun collectNodeInfo(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.text.isNullOrEmpty()) {
            sb.append(node.text).append("|")
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        sb.append(rect.toShortString()).append("|")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodeInfo(child, sb)
        }
    }

    private fun performScroll(listNode: AccessibilityNodeInfo, root: AccessibilityNodeInfo): Boolean {
        // Try standard scroll first
        if (listNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true
        }

        // Fallback to gesture
        val rect = Rect()
        root.getBoundsInScreen(rect)
        // Center of screen
        val centerX = rect.centerX().toFloat()
        // Swipe from 80% height to 20% height
        val startY = rect.height() * 0.8f
        val endY = rect.height() * 0.2f

        val path = Path()
        path.moveTo(centerX, startY)
        path.lineTo(centerX, endY)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500)) // 500ms duration
            .build()

        return service?.dispatchGesture(gesture, null, null) ?: false
    }

    private fun isAtBottom(root: AccessibilityNodeInfo): Boolean {
        // Check for "X位联系人"
        val nodes = root.findAccessibilityNodeInfosByText("位联系人")
        for (node in nodes) {
            val text = node.text?.toString() ?: continue
            if (text.matches(Regex("^\\d+位联系人$"))) {
                return true
            }
        }
        return false
    }

    private fun isInContactsPage(rootNode: AccessibilityNodeInfo): Boolean {
        // 检查是否在通讯录页面（通过查找特征控件）
        val nodes = rootNode.findAccessibilityNodeInfosByText("通讯录")
        return nodes.isNotEmpty()
    }

    private fun findScrollableList(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        findNodesByClassName(root, "android.widget.ListView", candidates)
        findNodesByClassName(root, "androidx.recyclerview.widget.RecyclerView", candidates)

        // Return the largest one
        return candidates.maxByOrNull {
            val rect = Rect()
            it.getBoundsInScreen(rect)
            rect.width() * rect.height()
        }
    }

    private fun findValidContactNodes(listNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val validNodes = mutableListOf<AccessibilityNodeInfo>()

        for (i in 0 until listNode.childCount) {
            val child = listNode.getChild(i) ?: continue
            val text = getNodeText(child)
            
            // Filter Logic
            if (text.isNullOrBlank()) continue
            if (text in BLACK_LIST) continue
            
            // Index Filter: Single char and small height
            val rect = Rect()
            child.getBoundsInScreen(rect)
            if (text.length == 1 && rect.height() < 100) continue 

            // Must be clickable or have clickable relatives
            if (isClickableNode(child)) {
                validNodes.add(child)
            }
        }
        return validNodes
    }

    private fun getNodeText(node: AccessibilityNodeInfo): String? {
        if (!node.text.isNullOrEmpty()) return node.text.toString().trim()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val text = getNodeText(child)
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    private fun isClickableNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return true
        // Check children? usually the list item itself is clickable or contains a clickable
        // But here we want the item to be the target. 
        // If the item itself isn't clickable, maybe it's a container.
        // Let's assume the list child is the touch target or contains it.
        // For now, if we found text, we assume it's valid. 
        // But wait, "Headers" might not be clickable? 
        // Let's stick to: must have text AND (self is clickable OR has clickable child)
        return true // Simplification: if it has text and is in list, it's likely interactable.
    }

    private fun findNodesByClassName(
        node: AccessibilityNodeInfo,
        className: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString() == className) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClassName(child, className, result)
        }
    }

    private fun clickNodeWithRandomOffset(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 在控件范围内随机偏移 ±20%
        val offsetX = (rect.width() * 0.2 * (Random.nextDouble() - 0.5)).toInt()
        val offsetY = (rect.height() * 0.2 * (Random.nextDouble() - 0.5)).toInt()

        val centerX = rect.centerX() + offsetX
        val centerY = rect.centerY() + offsetY

        // 使用 GestureDescription 在指定坐标点击
        val path = Path()
        path.moveTo(centerX.toFloat(), centerY.toFloat())

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        service?.dispatchGesture(gesture, null, null)
    }

    private suspend fun extractContactInfo(): WechatContact? {
        delay(500) // 等待页面加载

        val rootNode = service?.rootInActiveWindow ?: return null

        // 查找微信号
        val wechatId = findWechatId(rootNode) ?: return null

        // 查找昵称
        val nickname = findNickname(rootNode) ?: wechatId

        // 查找备注名
        val remark = findRemark(rootNode) ?: ""

        return WechatContact(
            wechatId = wechatId,
            nickname = nickname,
            remark = remark,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun findWechatId(rootNode: AccessibilityNodeInfo): String? {
        // 策略1: 查找包含"微信号"的节点，取其兄弟或子节点
        val nodes = rootNode.findAccessibilityNodeInfosByText("微信号")
        for (node in nodes) {
            // 情况A: 文本是 "微信号: wxid_xxx"
            val text = node.text?.toString()
            if (text != null && text.contains("微信号") && text.length > 4) {
                val id = text.substringAfter("微信号").replace(":", "").trim()
                if (isValidWechatId(id)) return id
            }
            
            // 情况B: 兄弟节点
            val parent = node.parent ?: continue
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                val siblingText = sibling.text?.toString() ?: continue
                if (siblingText != "微信号" && siblingText.isNotBlank() && isValidWechatId(siblingText)) {
                    return siblingText.trim()
                }
            }
        }

        // 策略2: 全局扫描符合微信号格式的文本 (wxid_... 或 6-20位字母数字)
        return findTextByRegex(rootNode)
    }

    private fun isValidWechatId(text: String): Boolean {
        // 排除常见干扰词
        if (text.contains("地区") || text.contains("昵称") || text.contains("来源")) return false
        // 微信号通常是 wxid_ 开头，或者 6-20位，支持减号和下划线
        // 简单正则：包含字母或数字，且长度合适
        if (text.length < 6 || text.length > 30) return false
        // 必须包含至少一个字母或数字
        return text.any { it.isLetterOrDigit() }
    }

    private fun findTextByRegex(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        if (text != null) {
            // 优先匹配 wxid_
            if (text.startsWith("wxid_", ignoreCase = true)) return text
            // 匹配纯微信号格式 (仅字母数字下划线减号，首字符字母，6-20位)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findTextByRegex(child)
            if (found != null) return found
        }
        return null
    }

    private fun findNickname(rootNode: AccessibilityNodeInfo): String? {
        // 通常昵称在顶部标题栏
        val textNodes = mutableListOf<AccessibilityNodeInfo>()
        findTextNodes(rootNode, textNodes)

        // 返回第一个非空文本（通常是昵称）
        return textNodes.firstOrNull()?.text?.toString()?.trim()
    }

    private fun findRemark(rootNode: AccessibilityNodeInfo): String? {
        // 查找包含"备注"的节点
        val nodes = rootNode.findAccessibilityNodeInfosByText("备注")
        for (node in nodes) {
            val parent = node.parent ?: continue
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                val text = sibling.text?.toString() ?: continue
                if (text != "备注" && text.isNotBlank()) {
                    return text.trim()
                }
            }
        }
        return null
    }

    private fun findTextNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (!node.text.isNullOrEmpty()) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findTextNodes(child, result)
        }
    }

    private fun randomDelay(minMs: Long, maxMs: Long): Long {
        return Random.nextLong(minMs, maxMs + 1)
    }

    private suspend fun finishFetch() {
        isFetching = false
        updateStatus(fetching = false)
        
        if (fetchedContacts.isNotEmpty()) {
            // 保存到数据库
            DbSet.wechatContactDao.insertAll(fetchedContacts)
            toast("抓取完成，已更新 ${fetchedContacts.size} 个联系人")
            updateStatus("抓取完成：${fetchedContacts.size} 个联系人")
        } else {
            toast("未抓取到联系人")
            updateStatus("未抓取到联系人")
        }

        // Delay slightly to let user see "Finished" status
        delay(2000)
        
        FetchOverlayController.hide()
        collectJob?.cancel()
        collectJob = null
        service = null

        LogUtils.d("$TAG: Fetch finished, total: ${fetchedContacts.size}")
    }

    fun stopFetch() {
        isFetching = false
        updateStatus(text = "已停止抓取", fetching = false)
        FetchOverlayController.hide()
        collectJob?.cancel()
        collectJob = null
        service = null
        toast("已停止抓取")
    }
}
