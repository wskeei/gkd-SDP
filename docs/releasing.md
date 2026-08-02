# GKD-SDP Release Guide

本文描述 GKD-SDP 的版本化发布流程。Android 编译的权威结果来自 GitHub Actions；本地不需要执行完整 Gradle 构建。

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

Nightly 通过 [`nightly.yml`](../.github/workflows/nightly.yml) 在 `main` 成功提交后构建未签名的 debug APK。它不读取 release Environment，不创建 tag/Release，不覆盖任何历史资产，Artifact 保留 7 天。

## Release flow

```text
版本 PR / CHANGELOG
        ↓
PR CI + dependency review + CodeQL
        ↓
合并 main，生成 Nightly artifact
        ↓
更新 `gradle/version.properties` 和 `CHANGELOG.md`，运行元数据测试，并创建 annotated `vX.Y.Z` tag
        ↓
Release workflow：测试、Lint、签名、验签、checksum、attestation
        ↓
Draft Release：上传 APK/update.json/SHA256SUMS
        ↓
人工检查后发布不可变 Release
```

正式发布不强推 tag、不覆盖已发布资产、不把 main 的每次提交写成 Release。Tag 必须与 `versionName` 完全匹配，`versionCode` 只能递增且不可复用；若已发布版本有问题，创建新的 patch/beta/rc 版本，并在旧版本说明中标注已知问题。发布工作流会在发现同名 Release 或 tag 已存在时停止，不覆盖历史资产。

## Signing safety

Release signing secrets 只放在 GitHub `release` Environment：`GKD_STORE_FILE_BASE64`、`GKD_STORE_PASSWORD`、`GKD_KEY_ALIAS`、`GKD_KEY_PASSWORD`。不要把 keystore、密码、base64 或临时 `gradle.properties` 提交到 Git、Issue、日志或 Artifact。

维护者还要在 `release` Environment/repository variable 中设置 `RELEASE_CERT_SHA256`，并从可信本机记录证书指纹。工作流把 keystore 解码到 `$RUNNER_TEMP`，通过 `ORG_GRADLE_PROJECT_*` 仅向当前 Gradle 进程提供配置，完成后无论成功失败都会清理临时文件。证书指纹必须与 `RELEASE_CERT_SHA256` 对照；指纹变化必须停止发布并调查。维护者应在本地加密保存至少两份离线 keystore 副本，记录 alias、创建日期和指纹，不记录密码到仓库。

手动触发 `Release` workflow 时必须选择受保护的 `main` ref，默认 `publish=false`：它仍会签名、验签并上传 7 天 Artifact，但不会创建 Release。只有已有的、与 `versionName` 匹配的 tag 才允许手动发布；正常发布应将 annotated `vX.Y.Z[-pre]` tag 推送到 `main` 的已合并提交。来自 feature branch 的手动运行会被 job 条件跳过，避免把签名 Environment 暴露给未合并代码。

## Release notes

`CHANGELOG.md` 使用 Keep a Changelog，保持 `[Unreleased]` 和每个版本段。每个 Release body 只提取对应版本段；GitHub generated notes 仅作为 PR 分类补充，不把整份历史 changelog 重复粘贴到每个版本。

更新说明应告诉用户新增、改变、修复、安全影响、权限影响和已知限制，不要写未经验证的承诺。

## Failure and recovery

- tag workflow 在创建公开 Release 前失败：修复代码并创建新的 prerelease/tag，或在确认没有公开资产时清理 draft。
- 已发布 Release 失败：不覆盖 tag/资产，发布新的 patch/prerelease。
- 签名失败或证书指纹不一致：停止发布，检查 Environment 和离线 keystore，不得绕过验签。
- GitHub API/Actions 暂时故障：保留待发布状态，恢复后从同一个已确认 commit 重新运行；不要移动已经公开的 tag。

发布完成后独立运行 `sha256sum --check SHA256SUMS.txt`、`gh attestation verify <apk> --repo wskeei/gkd-SDP`，并按 [`docs/testing/release-smoke-checklist.md`](testing/release-smoke-checklist.md) 做真机升级与应用内更新检查。
