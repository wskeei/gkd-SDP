# GKD-SDP

[![CI](https://github.com/wskeei/gkd-SDP/actions/workflows/ci.yml/badge.svg)](https://github.com/wskeei/gkd-SDP/actions/workflows/ci.yml)
[![Nightly](https://github.com/wskeei/gkd-SDP/actions/workflows/nightly.yml/badge.svg)](https://github.com/wskeei/gkd-SDP/actions/workflows/nightly.yml)
[![Latest release](https://img.shields.io/github/v/release/wskeei/gkd-SDP?display_name=tag)](https://github.com/wskeei/gkd-SDP/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

`GKD-SDP` 是一个基于 [GKD](https://github.com/gkd-kit/gkd) fork 的 Android 数字自律工具。

本项目保留了 GKD 的高级选择器、订阅规则、无障碍运行时和快照能力，并在此基础上加入一组面向“减少冲动使用手机”的二开功能。它不是上游 GKD 的官方版本，也不再只是一个自动点击工具。

## 项目定位

GKD 原本擅长通过选择器和订阅规则，在指定界面自动点击控件、跳过流程、执行快捷操作。`GKD-SDP` 在这个运行时之上增加了自律干预层：

- 在打开受控应用前，让用户先说明理由并申请有限使用时长。
- 在专注、应用拦截、URL 拦截等场景中主动阻断分心入口。
- 在规则被关闭后，通过自动重开和锁定机制降低“临时放纵”变成永久关闭的概率。
- 通过申请记录和复盘视图，把使用行为从流水账转成可分析的模式。

简单说，它的目标不是帮你更快地使用手机，而是让每一次“我要打开它”都变得更清醒。

## 主要功能

### 使用申请

对受控应用启用“申请后使用”的流程：

- 支持仅选中应用或全局生效。
- 支持严格模式和普通模式。
- 申请时需要选择标签、填写理由、选择时长。
- 标签按预设和创建时间稳定排序，“其他”始终固定在列表末尾；距离上次结束使用的秒级信息和历史节律图表位于申请表单顶部，间用比反馈紧邻申请时长。
- 记录每次申请的应用、标签、理由、时长、结束状态。
- 提供申请复盘页和桌面小组件，查看今日/近 7 天申请模式。
- 申请页显示“未使用间隔”：从上次实际结束使用到当前申请的本地统计；拦截页显示对应规则/目标的触发间隔。
- 申请页提供“间用比”反馈：`未使用间隔 ÷ 本次申请时长`，并同时显示近 24 小时、近 7 天、近 30 天平均值。
- 间隔图默认显示近 24 小时，可切换近 7 天或近 30 天；旧记录缺少可信结束时间时保持未知，不回填为 0。
- 公式会把间隔和申请时长自动换成同一秒、分钟或小时单位；有效样本不超过窗口上限时逐条显示，超过上限才聚合，并同时说明总记录、有效样本和图形点数量。

### 专注模式

通过专注会话和白名单限制临时访问：

- 支持手动或规则化的专注时段。
- 非白名单应用会被拦截覆盖层阻断。
- 支持锁定窗口，避免中途轻易关闭。

### 应用拦截

对指定应用或应用组建立时间规则：

- 按时间段拦截应用。
- 支持应用组管理。
- 支持锁定与自动恢复相关策略。

### URL 拦截

针对浏览器地址栏进行 URL 检测和阻断：

- 通过无障碍树读取支持浏览器的地址栏。
- 匹配 URL 规则后进行拦截或跳转。
- 支持浏览器配置和 URL 规则组。

### 拦截来源与触发记录

- 选择器拦截覆盖层会显示订阅、规则组和命中的 exact rule 名称/编号；应用和 URL 拦截显示对应的安全规则来源。
- 首页“触发记录”区分“动作已执行”和“命中后已拦截”。只有覆盖层真正挂载成功，选择器拦截才会写入后一种记录。
- 来源和统计只保存在本机，不新增保存 selector 文本、节点文本、实际 URL、URL pattern 或重复的申请理由。

### 数字自律复盘

- 复盘按“使用申请/拦截”主类型、今日/近 7 天/近 30 天范围组织。
- 使用申请默认查看间用比趋势，也可切换未使用间隔；拦截只查看拦截间隔。
- 页面依次展示数据概览、单指标趋势、应用/标签/结束状态或拦截目标分布、最近 10 条明细，并在每个图表中提供文字明细和覆盖率。
- 复盘趋势一次只聚焦一个指标：申请默认为间用比，可切换未使用间隔；每个范围显示本期平均、上一周期、差值、总记录、有效样本和图形点。
- 无效间隔、旧版未知结束时间和无效间用比保持为未知，不显示成 0；复盘文案不包含申请理由、URL、pattern、selector 或节点文本。

### 自动重开

用于恢复被临时关闭的自律保护：

- 可恢复使用申请总开关。
- 可恢复规则或拦截相关状态。
- 适合避免“只关一下”变成长期失效。

### GKD 原有能力

本 fork 仍保留上游 GKD 的核心能力：

- 高级选择器。
- 订阅规则。
- 快照捕获与审查。
- 自动点击和快捷操作。
- 触发记录、界面日志、调试服务等开发能力。

## 与上游 GKD 的关系

本项目是二次开发版本，基础能力来自上游 GKD：

- 上游项目：[gkd-kit/gkd](https://github.com/gkd-kit/gkd)
- 选择器文档：[gkd.li/guide/selector](https://gkd.li/guide/selector)
- 订阅规则文档：[gkd.li/guide/subscription](https://gkd.li/guide/subscription)

由于 fork 历史原因，仓库中仍可能存在 `gkd`、`subs`、`match`、上游链接、原始文案等命名和痕迹。开发时请根据上下文区分：

- 上游自动化/选择器系统。
- 本 fork 新增的数字自律系统。

## 技术栈

- Kotlin
- Android / Jetpack Compose
- Compose Destinations
- Room
- Kotlinx Serialization
- Ktor
- Shizuku / hidden API stubs
- Kotlin Multiplatform `selector` 模块

主要模块：

- `app`：Android 主应用。
- `selector`：选择器解析与匹配逻辑。
- `hidden_api`：隐藏 API 编译桩。

## 构建

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleGkdDebug
.\gradlew.bat :app:testGkdDebugUnitTest
.\gradlew.bat :selector:jvmTest
```

Unix-like shell：

```bash
./gradlew :app:assembleGkdDebug
./gradlew :app:testGkdDebugUnitTest
./gradlew :selector:jvmTest
```

常用信息：

- `applicationId`: `li.songe.gkd.sdp`
- `minSdk`: 26
- `compileSdk` / `targetSdk`: 37
- Debug 应用名：`GKD-SDP`

Android 编译和 PR 质量检查以 [GitHub Actions](https://github.com/wskeei/gkd-SDP/actions) 为准。正式版本和历史更新说明请查看 [Releases](https://github.com/wskeei/gkd-SDP/releases)；`main` 上的 [Nightly 构建](https://github.com/wskeei/gkd-SDP/actions/workflows/nightly.yml)仅保留 7 天，是短期测试构建，不等同于稳定版。

版本号、更新说明和可验证安装包遵循 [`docs/releasing.md`](docs/releasing.md)。`v2.x` 是 GKD-SDP 自有版本；上游 `v1.x` tag 只作为历史基线，不会覆盖本项目的 Release 历史。

## 开发说明

更详细的工程结构、运行时架构、Room/Store 数据模型和开发注意事项见：

[README_DEV.md](README_DEV.md)

如果你要改动无障碍运行时、自动重开、应用/URL 拦截或使用申请，请优先阅读 `README_DEV.md`，这些功能之间存在较多状态联动。

## 参与和反馈

- 使用问答、设备兼容性和想法： [Discussions](https://github.com/wskeei/gkd-SDP/discussions)
- 可复现问题： [Issues](https://github.com/wskeei/gkd-SDP/issues)
- 贡献代码： [CONTRIBUTING.md](CONTRIBUTING.md)
- 安全问题： [SECURITY.md](SECURITY.md)
- 隐私说明： [PRIVACY.md](PRIVACY.md)
- 上游同步边界： [UPSTREAM.md](UPSTREAM.md)
- 发布与恢复： [docs/releasing.md](docs/releasing.md) · [recovery runbook](docs/maintenance/recovery-runbook.md)

## 免责声明

本项目遵循 [GPL-3.0](LICENSE) 开源。

GPL-3.0 的使用、修改和再分发权利与义务以许可证正文为准。本项目不鼓励也不保证任何违反法律、绕过应用/系统安全策略或违反目标应用服务条款的用途。使用无障碍、悬浮窗、Shizuku 等能力时，请自行理解相关权限风险并遵守所在地区法律法规。

## 致谢

感谢 [GKD](https://github.com/gkd-kit/gkd) 原项目及其生态提供的选择器、订阅规则和无障碍自动化基础。本项目的二开能力建立在这些基础之上。
