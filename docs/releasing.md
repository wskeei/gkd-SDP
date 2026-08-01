# GKD-SDP Release Guide

本文描述版本化发布基础设施落地后的目标流程。迁移 PR 尚未全部合并时，仓库中的旧 workflow 可能仍会创建 legacy `latest` 快照；在新 workflow、版本源和 Environment 完成并通过 Actions 之前，不要把本文件的目标状态当成当前仓库已具备的能力。

## Version policy

GKD-SDP 使用 Semantic Versioning 2.0：

- `MAJOR`：不兼容的产品/数据/权限语义变化；
- `MINOR`：向后兼容的新功能；
- `PATCH`：向后兼容的 bug/security 修复；
- `-alpha.N`、`-beta.N`、`-rc.N`：稳定版前的可安装预发布。

当前上游基础是 `1.12.1`，首个独立 SDP 产品版本建议为 `2.0.0-beta.1`，`versionCode` 从 92 增加到 93。以后每一个出现在 Releases 页面并供用户安装的版本都必须拥有：

- 唯一的 `vX.Y.Z[-pre]` tag；
- 递增且不可复用的 Android `versionCode`；
- `gradle/version.properties` 中的 `versionName`；
- `CHANGELOG.md` 对应版本段和日期；
- 独立的 GitHub Release、APK、`update.json` 和 `SHA256SUMS.txt`。

Nightly 是 Actions Artifact，不是正式版本，也不能替代版本号或 Release 历史。

## Release flow

```text
版本 PR / CHANGELOG
        ↓
PR CI + dependency review + CodeQL
        ↓
合并 main，生成 Nightly artifact
        ↓
更新 version.properties 并创建 annotated vX.Y.Z tag
        ↓
Release workflow：测试、Lint、签名、验签、checksum、attestation
        ↓
Draft Release：上传 APK/update.json/SHA256SUMS
        ↓
人工检查后发布不可变 Release
```

正式发布不强推 tag、不覆盖已发布资产、不把 main 的每次提交写成 Release。若已发布版本有问题，创建新的 patch/beta/rc 版本，并在旧版本说明中标注已知问题。

## Signing safety

Release signing secrets 只放在 GitHub `release` Environment：`GKD_STORE_FILE_BASE64`、`GKD_STORE_PASSWORD`、`GKD_KEY_ALIAS`、`GKD_KEY_PASSWORD`。不要把 keystore、密码、base64 或临时 `gradle.properties` 提交到 Git、Issue、日志或 Artifact。

维护者应在本地加密保存至少两份离线 keystore 副本，并记录 alias、创建日期和证书 SHA-256 指纹，不记录密码到仓库。证书指纹必须与 `RELEASE_CERT_SHA256` 对照；指纹变化必须停止发布并调查。

## Release notes

`CHANGELOG.md` 使用 Keep a Changelog，保持 `[Unreleased]` 和每个版本段。每个 Release body 只提取对应版本段；GitHub generated notes 仅作为 PR 分类补充，不把整份历史 changelog 重复粘贴到每个版本。

更新说明应告诉用户新增、改变、修复、安全影响、权限影响和已知限制，不要写未经验证的承诺。

## Failure and recovery

- tag workflow 在创建公开 Release 前失败：修复代码并创建新的 prerelease/tag，或在确认没有公开资产时清理 draft。
- 已发布 Release 失败：不覆盖 tag/资产，发布新的 patch/prerelease。
- 签名失败或证书指纹不一致：停止发布，检查 Environment 和离线 keystore，不得绕过验签。
- GitHub API/Actions 暂时故障：保留待发布状态，恢复后从同一个已确认 commit 重新运行；不要移动已经公开的 tag。
