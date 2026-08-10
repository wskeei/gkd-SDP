# GKD-SDP 无障碍矩阵

本文档记录一级页面与核心覆盖层（Overlay）的可访问性呈现契约。
`AccessibilityPresentationContractTest` 守护共享组件规则；
`AccessibilitySmokeTest` 与托管设备上的 UI 测试守护页面级语义。

## 共享规则（组件层）

- 可点击/可切换/可拖动/可展开元素必须有 role、stateDescription、
  onClickLabel 或 progressBarRangeInfo。
- 触控目标最小 48×48dp（`DimensionTokens.MinTouchTarget`）；
  `AccessibleIconButton` 强制 48dp 触控壳并清除内层重复描述。
- 可点击图标禁止 `contentDescription = null`；装饰图标由父节点合并语义。
- `SettingItem` 整行 `toggleable` 合并 Switch 语义，避免双重焦点。
- 规则启用、服务状态、错误、图表系列与正负趋势不只靠颜色表达；
  `InlineMessage` 同时提供图标与文字。
- 表单 label 永久可见；placeholder 不承担 label 职责。
- 秒级倒计时只更新文本，不设置 live region；用户聚焦时才读取剩余时长。
- 图表父节点提供摘要；数据表逐行提供时间、数值、单位与状态。

## 页面清单

| 页面 | 可访问标题 | 焦点顺序 | 主动作 | 状态表达 | 大字体结果 | 图表文字替代 | 对应测试 |
|---|---|---|---|---|---|---|---|
| 首页（概览） | 页面标题 | 导航 → 状态卡 → 列表 | 运行能力中心 | 文字+icon 状态卡 | 不截断滚动 | 摘要卡文字 | AccessibilitySmokeTest |
| 权限/能力中心 | 页面标题 | 状态 → 唯一下一步 | 下一步卡片 | 五种状态文字 | 不覆盖输入 | — | CapabilityCenterTest（Task 28） |
| 设置 | 页面标题 | 分组 → 项 → 开关 | 搜索/导航 | 摘要行 | 完整滚动 | — | SettingsSearchTest（Task 28） |
| 使用申请 Overlay | 弹窗标题 | 标签 → 理由 → 时长 → 操作栏 | 开始使用 | 错误 inline 文字 | IME 不遮挡 | 节奏卡摘要+数据表 | UsageRequestFlowTest（Task 28） |
| 倒计时悬浮条 | 无独立焦点 | pill 文本 | 点按查看/终止 | 剩余时长文本 | 单行截断 | — | AccessibilityPresentationContractTest |
| 拦截 Overlay | 弹窗标题 | 来源 → 说明 → 操作 | 继续/回桌面 | 来源缺失文字 | 完整滚动 | 节奏卡摘要 | InterceptionSourceCard |
| 数字自律复盘 | 页面标题 | 选择器 → 摘要卡 → 图表 → 数据表 | 窗口切换 | 空态文字 | 图表可滚 | 摘要+点选+数据表 | ReviewDashboardTest（Task 28） |
| 专注/应用拦截/URL 拦截 | 页面标题 | 列表 → 行 → 开关 | 新增 | 锁定文字 | 完整滚动 | — | Task 28 GMD |
| 订阅/应用列表 | 页面标题 | 搜索 → 列表 | 搜索 | 结果数 | 长标题两行 | — | Task 28 GMD |
| 快照/日志 | 页面标题 | 列表 → 详情 | 预览 | 空态 | 完整滚动 | — | Task 28 GMD |

## 验证分层

- JVM：`AccessibilityPresentationContractTest`（源码契约）。
- 设备/托管模拟器：`AccessibilitySmokeTest` 与 Task 28 GMD 套件。
- 字体缩放 1.0/1.3/2.0、横竖屏与 TalkBack 导航：真机验证，不由 CI 承担。
