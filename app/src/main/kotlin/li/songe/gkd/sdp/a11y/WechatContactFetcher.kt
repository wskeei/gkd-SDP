package li.songe.gkd.sdp.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.WechatContact
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.toast
import kotlin.random.Random

object WechatContactFetcher {
    private const val TAG = "WechatContactFetcher"
    private const val WECHAT_PACKAGE = "com.tencent.mm"

    val isFetchingFlow = MutableStateFlow(false)
    val fetchProgressFlow = MutableStateFlow("")

    private var service: AccessibilityService? = null
    private var isFetching = false
    private val fetchedContacts = mutableListOf<WechatContact>()
    private var fetchCount = 0

    fun startFetch(accessibilityService: AccessibilityService) {
        if (isFetching) {
            toast("正在抓取中，请稍候")
            return
        }

        service = accessibilityService
        isFetching = true
        isFetchingFlow.value = true
        fetchedContacts.clear()
        fetchCount = 0

        appScope.launch(Dispatchers.IO) {
            try {
                toast("请打开微信通讯录页面")
                fetchProgressFlow.value = "等待进入微信通讯录..."

                // 等待用户打开通讯录
                delay(3000)

                fetchContactsFromCurrentScreen()
            } catch (e: Exception) {
                LogUtils.d("$TAG: Fetch error: ${e.message}")
                toast("抓取失败：${e.message}")
            } finally {
                finishFetch()
            }
        }
    }

    private suspend fun fetchContactsFromCurrentScreen() {
        var consecutiveEmptyScreens = 0

        while (isFetching && consecutiveEmptyScreens < 2) {
            val rootNode = service?.rootInActiveWindow
            if (rootNode == null) {
                delay(1000)
                continue
            }

            // 检查是否在通讯录页面
            if (!isInContactsPage(rootNode)) {
                fetchProgressFlow.value = "请打开微信通讯录页面"
                delay(2000)
                continue
            }

            // 获取当前屏幕的联系人列表项
            val contactNodes = findContactNodes(rootNode)

            if (contactNodes.isEmpty()) {
                consecutiveEmptyScreens++
                if (consecutiveEmptyScreens >= 2) {
                    break
                }
                // 尝试滚动
                scrollDown(rootNode)
                delay(randomDelay(800, 1500))
                continue
            }

            consecutiveEmptyScreens = 0

            // 逐个点击联系人
            for ((index, node) in contactNodes.withIndex()) {
                if (!isFetching) break

                fetchProgressFlow.value = "正在抓取第 ${fetchCount + 1} 个联系人..."

                // 点击联系人
                clickNodeWithRandomOffset(node)
                delay(randomDelay(500, 1000))

                // 读取详情页信息
                val contact = extractContactInfo()
                if (contact != null) {
                    fetchedContacts.add(contact)
                    fetchCount++
                    LogUtils.d("$TAG: Fetched contact: ${contact.displayName} (${contact.wechatId})")
                }

                // 返回
                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                delay(randomDelay(400, 800))

                // 每抓取 10-15 个联系人，暂停一下
                if (fetchCount % Random.nextInt(10, 16) == 0) {
                    delay(randomDelay(2000, 4000))
                }
            }

            // 滚动到下一屏
            scrollDown(rootNode)
            delay(randomDelay(800, 1500))
        }
    }

    private fun isInContactsPage(rootNode: AccessibilityNodeInfo): Boolean {
        // 检查是否在通讯录页面（通过查找特征控件）
        val nodes = rootNode.findAccessibilityNodeInfosByText("通讯录")
        return nodes.isNotEmpty()
    }

    private fun findContactNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val contactNodes = mutableListOf<AccessibilityNodeInfo>()

        // 查找 ListView 或 RecyclerView
        val listNodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByClassName(rootNode, "android.widget.ListView", listNodes)
        findNodesByClassName(rootNode, "androidx.recyclerview.widget.RecyclerView", listNodes)

        for (listNode in listNodes) {
            for (i in 0 until listNode.childCount) {
                val child = listNode.getChild(i) ?: continue
                // 简单判断：包含文本且可点击的节点
                if (child.isClickable && hasText(child)) {
                    contactNodes.add(child)
                }
            }
        }

        return contactNodes
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

    private fun hasText(node: AccessibilityNodeInfo): Boolean {
        if (!node.text.isNullOrEmpty()) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasText(child)) return true
        }
        return false
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
        // 查找包含"微信号"的节点
        val nodes = rootNode.findAccessibilityNodeInfosByText("微信号")
        for (node in nodes) {
            // 查找同级或父级的文本节点
            val parent = node.parent ?: continue
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                val text = sibling.text?.toString() ?: continue
                if (text != "微信号" && text.isNotBlank()) {
                    return text.trim()
                }
            }
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

    private fun scrollDown(rootNode: AccessibilityNodeInfo) {
        rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun randomDelay(minMs: Long, maxMs: Long): Long {
        return Random.nextLong(minMs, maxMs + 1)
    }

    private suspend fun finishFetch() {
        isFetching = false
        isFetchingFlow.value = false

        if (fetchedContacts.isNotEmpty()) {
            // 保存到数据库
            DbSet.wechatContactDao.insertAll(fetchedContacts)
            toast("抓取完成，已更新 ${fetchedContacts.size} 个联系人")
            fetchProgressFlow.value = "抓取完成：${fetchedContacts.size} 个联系人"
        } else {
            toast("未抓取到联系人")
            fetchProgressFlow.value = "未抓取到联系人"
        }

        LogUtils.d("$TAG: Fetch finished, total: ${fetchedContacts.size}")
    }

    fun stopFetch() {
        isFetching = false
        isFetchingFlow.value = false
        fetchProgressFlow.value = "已停止抓取"
        toast("已停止抓取")
    }
}
