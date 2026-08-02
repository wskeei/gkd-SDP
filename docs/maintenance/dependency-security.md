# 构建依赖安全维护

本文记录 GKD-SDP 的 Gradle/Android 工具链传递依赖安全下限。它是构建供应链的维护记录，不是运行时功能配置。

## 当前基线（2026-08-02）

Dependabot 报告的 45 条告警均来自 Maven/Gradle 传递依赖：1 条 Critical、19 条 High、23 条 Moderate、2 条 Low。告警逐条保留在 GitHub 依赖图中，本次不 dismiss 任何告警。对应的覆盖矩阵和原始调查记录见 [修复计划](../plans/2026-08-02-dependabot-vulnerability-repair.md)。

当前构建报告确认这些坐标由 Android Gradle Plugin/Android Tools、bundletool、jetifier、UTP 和测试平台等构建工具链引入。它们目前不在 release APK 的 DEX `class_defs` 中，但仍属于 CI、签名构建和开发机的供应链，不能因为不进入 APK 就忽略。

## 版本下限与引入链

下表的 floor 保存在 [`gradle/security-dependency-floors.properties`](../../gradle/security-dependency-floors.properties)，并由 settings 级解析策略同时覆盖 buildscript classpath 与普通 project configurations。加入日期为 2026-08-02；父依赖以 Actions 真实 `buildEnvironment`/dependency report 为准，不能根据本表臆造新的引入链。

| 族群 / floor | 父依赖（当前工具链） | 告警编号 | 备注 |
| --- | --- | --- | --- |
| `io.netty` 4.1.x ≥ `4.1.136.Final` | Android Tools/UTP/testing-platform 的 grpc-netty 链 | 2–7, 9–11, 13, 15–16, 19–34, 36–45 | 统一覆盖 Netty 4.1 模块；不影响 tcnative 2.x 或未来更高版本 |
| `org.apache.commons:commons-lang3` ≥ `3.18.0` | Android Tools / SDK common | 8 | CVE-2025-48924 |
| `org.apache.httpcomponents:httpclient`, `httpmime` ≥ `4.5.14` | Android Tools / bundletool 工具链 | 1 | 两个 4.5 模块保持同版本 |
| `org.bitbucket.b_c:jose4j` ≥ `0.9.6` | bundletool | 14 | CVE-2024-29371 |
| `org.bouncycastle:bcpkix-jdk18on`, `bcprov-jdk18on`, `bcutil-jdk18on` ≥ `1.84` | Android Tools / SDK common | 17–18, 35 | Critical 告警为 35，三个模块一起对齐 |
| `org.jdom:jdom2` ≥ `2.0.6.1` | jetifier-processor | 12 | CVE-2021-33813 |

如果未来报告显示父工具链发生变化，应以报告中的请求路径补充本表，而不是把历史父依赖当成永久事实。

## Dependabot 与 CI 责任

GitHub 对 Gradle 的 dependency submission 会记录传递依赖，但 Dependabot 的 Gradle security update 可能无法在仓库清单中定位未直接声明的传递坐标；这会表现为 `security_update_dependency_not_found`。因此 Dependabot 负责持续发现和提示，Gradle 安全下限与父工具升级由维护者通过 PR 修复。每周更新任务继续运行，不得关闭 Dependabot、依赖提交或完整依赖图。

每个 PR 的 `quality` job 必须：

1. 运行 Python 版本判断测试；
2. 生成包含 buildscript、app、selector 和插件报告的安全依赖报告；
3. 运行既有 unit tests/Lint；
4. 验证所有目标坐标达到 floor，并上传报告 artifact。

`dependency-review`、CodeQL、debug/release 构建和 main 分支的依赖图刷新也是合并门槛。任何告警 dismiss 必须逐条写明可审计理由；本批 45 条不允许 dismiss。

## 临时 override 的退出条件

`gradle/security-dependency-policy.settings.gradle.kts` 是在上游父工具尚未自然解析到安全版本期间的最小临时策略。只有同时满足以下条件，才可以删除某个族群的 override：

- 升级父工具后，在不应用该规则的干净报告中，该族群自然解析到 floor 或更高；
- `quality`、`build`、`dependency-review`、CodeQL 全部通过；
- main 的 Dependency Graph 已刷新，相关 Dependabot 告警已变为 fixed；
- release APK 的 DEX 审计仍确认该构建工具依赖没有意外进入应用包（若进入，必须另行评估运行时风险）。

删除前应先提交一个只移除该族群策略的 PR，保留前后报告和 Actions 链接。

## 上调 floor 的流程

1. 更新 `security-dependency-floors.properties`；
2. 更新 vulnerable/patched fixture 和测试；
3. 先运行测试确认旧实现按预期变红；
4. 更新策略或父依赖并再次运行测试；
5. 推送 PR，等待 Actions 的真实报告、构建和安全检查；
6. 合并后等待 Dependency Graph 刷新并逐条核对告警状态；
7. 在本维护文档中补充日期、原因、父依赖和验证链接。

任何无法由报告和 Actions 证实的“已修复”都不得写入 release 说明，也不得用删除依赖图、关闭扫描或批量 dismiss 来制造零告警结果。
