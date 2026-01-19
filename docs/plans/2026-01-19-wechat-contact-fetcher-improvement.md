# 微信联系人抓取功能优化与可视化设计 (WeChat Contact Fetcher Improvement)

## 1. 背景与问题
当前的微信联系人抓取功能存在以下严重缺陷，导致无法正常完成数据采集：
1.  **节点识别错误**：误将“新的朋友”、“群聊”、“公众号”等头部功能入口识别为联系人。
2.  **滚动失效**：尝试滚动根节点 (`rootInActiveWindow`) 而非具体的列表容器，导致在大多数设备上无法翻页。
3.  **退出判定脆弱**：仅凭连续两次未找到节点就退出，容易在头部区域误判结束。
4.  **缺乏反馈**：全自动过程中用户处于“盲盒”状态，不知道进度，也无法便捷停止。

## 2. 改进目标
1.  **精准识别**：仅抓取真实的个人联系人，过滤所有头部入口和无关项。
2.  **稳健滚动**：精准定位列表容器进行滚动，并辅以手势滚动作为保底方案。
3.  **可视化反馈**：新增悬浮窗显示当前抓取进度、状态，并提供停止按钮。
4.  **智能防重**：优化去重逻辑和结束判定，确保不漏抓、不重复、不错退。

## 3. 技术设计

### 3.1 核心逻辑重构 (WechatContactFetcher)

#### A. 节点识别策略 (Blacklist + Index)
采用 **黑名单过滤 + 结构判断** 的组合策略。

1.  **黑名单 (Blacklist)**：忽略以下文本的节点：
    *   "新的朋友", "仅聊天的朋友", "群聊", "标签", "公众号", "服务号"
    *   "企业微信联系人", "企业微信通知", "我的企业及企业联系人"
    *   "微信团队", "文件传输助手"
2.  **结构判断**：
    *   排除 **字母索引** (如 "A", "B", "↑", "☆")。通常这些节点高度很小或 `className` 特殊。
    *   有效联系人通常在 `ListView` 或 `RecyclerView` 内。

#### B. 滚动策略 (Dual Strategy)
1.  **优先策略**：查找当前界面最大的 `ListView` 或 `RecyclerView`，对其执行 `ACTION_SCROLL_FORWARD`。
2.  **保底策略**：如果标准滚动动作返回 `false` 或屏幕内容哈希未变，使用 `dispatchGesture` 发送上滑手势 (Swipe Up)。
3.  **验证机制**：记录滚动前的屏幕内容指纹 (Content Hash)，滚动后对比。如果连续 3 次无变化，视为到底。

#### C. 抓取流程 (Process Flow)
1.  **初始化**：显示悬浮窗，通过 `A11yService` 获取上下文。
2.  **主循环**：
    *   **定位列表**：找到列表容器。
    *   **快照识别**：获取当前屏所有可见的、未处理过的有效联系人节点。
    *   **逐个处理**：
        *   高亮/提示当前目标。
        *   点击进入详情页。
        *   提取 `wechatId`, `nickname`, `remark`。
        *   返回列表页。
        *   标记该节点已处理 (使用 `text + bounds` 组合键)。
    *   **滚动加载**：执行滚动操作。
    *   **结束检查**：检查是否到底部 (内容无变化 或 发现底部的“XX位联系人”文本)。

### 3.2 可视化悬浮窗 (FetchOverlayController)

创建一个独立的单例控制器 `FetchOverlayController`，负责管理悬浮窗 UI。

*   **UI 布局**：
    *   背景：半透明黑色圆角矩形。
    *   内容：
        *   状态文本：(e.g., "正在抓取: 张三")
        *   进度统计：(e.g., "已获取: 15 人")
        *   操作栏：[ 停止 ] 按钮 (红色)
*   **交互**：
    *   点击 [停止] 按钮调用 `WechatContactFetcher.stopFetch()`。
    *   支持拖拽 (可选，P2优先级)。

### 3.3 数据流与状态管理

在 `WechatContactFetcher` 中增加状态流 `fetchStateFlow`：

```kotlin
data class FetchState(
    val isFetching: Boolean = false,
    val fetchedCount: Int = 0,
    val statusText: String = "准备中...",
    val currentTarget: String? = null // 当前正在处理的联系人昵称
)
```

`FetchOverlayController` 订阅此 Flow 并更新 UI。

## 4. 实现步骤

1.  **创建悬浮窗控制器 (FetchOverlayController)**
    *   实现 `show(context)`, `hide()`, `update(state)` 方法。
    *   定义简单的 Compose UI 或 View Layout。
2.  **重构 WechatContactFetcher**
    *   引入黑名单常量 `BLACK_LIST`。
    *   重写 `findContactNodes` 逻辑。
    *   重写 `performScroll` 逻辑，加入手势支持。
    *   集成 `FetchOverlayController` 的调用。
3.  **优化详情页提取**
    *   优化 `extractContactInfo`，提高查找微信号的效率和准确性。
4.  **集成与测试**
    *   在真机上测试滚动兼容性。
    *   验证黑名单过滤效果。
    *   验证悬浮窗交互。

## 5. 预期效果
用户启动抓取后，右下角出现悬浮窗显示进度。程序自动跳过头部功能项，从第一个真实联系人开始，逐个点击进入详情页抓取，抓取完毕后自动翻页，直到所有联系人采集完毕，悬浮窗提示“完成”并自动消失。
