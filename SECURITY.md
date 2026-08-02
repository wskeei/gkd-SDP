# Security Policy

## Supported versions

| Version channel | Support |
| --- | --- |
| Latest stable release | Security fixes prioritized |
| Latest beta/rc release | Best effort，可能先要求升级到 stable |
| Older releases and Nightly artifacts | 不承诺安全修复 |

由于项目由个人维护，不能承诺固定响应 SLA；高影响漏洞会尽量优先处理。

## Report a vulnerability privately

请通过仓库 **Security → Report a vulnerability** 使用 GitHub Private Vulnerability Reporting。不要在公开 Issue、Discussion、PR、README 或聊天中发布未修复漏洞、exploit、敏感日志或复现代码。

报告尽量包含：

- GKD-SDP 版本、安装来源、Android 版本和设备/ROM；
- 影响范围和安全影响；
- 最小可复现步骤或 PoC（避免包含真实账号/数据）；
- 日志、截图或堆栈（先脱敏）；
- 你认为的修复方向（如果有）。

维护流程为：私密确认 → 影响评估 → 修复 PR → Actions/CodeQL/设备验证 → 新的不可变版本 → 在适当范围内披露。请不要在修复前公开 tag、commit 或下载链接。

如果 Private Vulnerability Reporting 暂时不可用，请通过维护者 GitHub profile 中的私密联系方式联系，并在标题中标明 `GKD-SDP security report`。

## Scope and privacy

GKD-SDP 使用无障碍、悬浮窗、通知和可选 Shizuku 能力，漏洞报告中可能包含屏幕内容、URL 或使用申请数据。只提交复现所需的最小信息；维护者不会要求你上传完整的个人日志。
