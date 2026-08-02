# 发布密钥与仓库恢复手册

本手册只记录恢复步骤，不记录 keystore、密码、base64、token 或 GitHub recovery code。发布签名密钥一旦丢失，无法通过重新生成的证书兼容已经安装的 APK；应把它当作最高优先级的恢复事件。

## 离线 keystore

1. 在两个不同位置保存至少两份加密的 keystore 副本，并定期确认介质可读取。
2. 在本地密码管理器保存 keystore 密码、key alias 和 key password；不要放进仓库、Issue、Actions 日志或 Artifact。
3. 记录 alias、创建日期、证书 SHA-256 指纹和最近一次恢复演练日期。指纹应与 GitHub `release` Environment 的 `RELEASE_CERT_SHA256` 一致。
4. 每季度在离线临时目录读取 keystore，使用 `keytool`/`apksigner` 检查指纹；演练完成后安全删除临时文件。

## GitHub 恢复

1. 确认账户 2FA、recovery codes 和登录邮箱仍可用。
2. 在仓库 `release` Environment 恢复四个 secrets：`GKD_STORE_FILE_BASE64`、`GKD_STORE_PASSWORD`、`GKD_KEY_ALIAS`、`GKD_KEY_PASSWORD`；不要在聊天或日志中回显值。
3. 恢复 `RELEASE_CERT_SHA256` variable，并与离线 keystore 指纹逐字符核对。
4. 先手动运行 `Release` workflow，保持 `publish=false`。只有 signed dry-run 的 `apksigner` 指纹检查通过后，才允许推送正式 tag。
5. 若 GitHub 账户、Environment 或 Ruleset 被误改，先读取本手册和 `docs/maintenance/github-settings.md` 的审计命令，再恢复最小权限；不要为排障关闭 secret scanning、Ruleset 或 Actions SHA pinning。

## 发布失败处置

- 构建或签名失败：保留日志和未公开 Artifact，修复后重新运行 dry-run；不要修改已经发布的 tag。
- 证书指纹不一致：立即停止发布，核对 Environment secret 与离线副本；不得绕过指纹检查。
- Draft Release 创建后失败：先检查 draft 是否没有公开资产，再决定是否清理；已发布 Release 不删除、不替换资产，改发新的 patch/beta/rc。
- keystore 丢失或疑似泄露：暂停发布，保护账户和剩余副本，评估迁移/通知方案。新证书不能为旧安装提供原地升级。

## 事件记录

恢复演练和真实事件只记录日期、操作者、结果、tag、证书指纹是否匹配以及下一步，不记录秘密本身。
