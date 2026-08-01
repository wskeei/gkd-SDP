# Contributing to GKD-SDP

感谢你愿意为 GKD-SDP 提交反馈或代码。GKD-SDP 是由个人维护的 GKD fork，目标是把无障碍自动化能力和数字自律保护结合起来。项目欢迎小而清晰的修复和文档改进，但不承诺每个提议都会合并。

## 先选择正确的入口

- 可复现的应用 bug：使用 [Bug report](https://github.com/wskeei/gkd-SDP/issues/new?template=bug_report.yml)。
- 已经明确、可执行的功能需求：使用 [Feature request](https://github.com/wskeei/gkd-SDP/issues/new?template=feature_request.yml)。
- 使用问答、规则讨论、设备兼容性或尚不确定的想法：使用 [Discussions](https://github.com/wskeei/gkd-SDP/discussions)。
- 未公开的安全漏洞：不要创建公开 Issue，按 [SECURITY.md](SECURITY.md) 的方式私密报告。
- 只影响上游 GKD、第三方订阅规则或选择器内容的问题，应先确认是否应该反馈给对应的维护者。

提交前请先搜索已有 Issue、PR 和 Discussion，避免重复报告。

## 开发环境与验证

主要技术栈是 Kotlin、Android/Jetpack Compose、Room、Ktor、Shizuku 和 Kotlin Multiplatform selector。项目的权威 Android 编译环境是 GitHub Actions（JDK 21）；本机没有完整 Android 环境时，不要把本地无法编译当作代码失败，提交后查看 PR 的 Actions 结果。

常用命令：

```bash
./gradlew :selector:jvmTest
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug
```

如果修改无障碍、悬浮窗、通知、后台服务、Shizuku、数据库迁移或数字自律规则，请在 PR 中写明真实设备验证情况；JVM 测试不能替代这些验证。

## 分支、提交和 PR

- 从最新 `main` 创建 `codex/` 或清晰描述用途的分支。
- 每个 PR 聚焦一个目的，避免把业务重构和基础设施改动混在一起。
- 保留有意义的提交记录；不要求贡献者为了形式强行重写全部历史。
- PR 描述必须说明目的、影响范围、测试命令和未验证项。
- 涉及 Room entity 时同时提交 migration、schema 和迁移测试。
- 涉及发布、版本号、GitHub Actions 或权限时，说明对签名密钥、token 权限、Release 历史的影响。
- 维护者会根据风险、方向、测试结果和后续维护成本决定是否合并。

PR 提交前至少运行 `git diff --check`，并确认没有把 `.jks`、密码、token、日志原文、申请理由、URL 或屏幕内容提交进去。

## 隐私与敏感数据

本应用会处理应用列表、无障碍节点、快照、URL 规则、使用申请理由和行为日志等可能敏感的数据。上传日志或截图前请删除：

- 申请理由、个人计划、账号、手机号、邮箱和聊天内容；
- 完整 URL、token、Cookie、Authorization header；
- 屏幕录制中不相关的第三方应用内容；
- keystore、`gradle.properties`、GitHub secret 和任何密码。

如果无法确认某份日志是否安全，先在 Discussion 中询问，不要直接公开上传。

## 许可证

代码按 [GPL-3.0](LICENSE) 发布。提交者应确认自己有权按该许可证提供代码；本项目当前不要求 CLA 或 DCO。贡献者仍需遵守第三方代码、字体、图标和订阅规则各自的许可证。
