# GKD-SDP Release Guide

本文描述 GKD-SDP 的版本化发布流程。Android 编译的权威结果来自 GitHub Actions；本地不需要执行完整 Gradle 构建。

## Version policy

GKD-SDP 使用 Semantic Versioning 2.0：

- `MAJOR`：不兼容的产品、数据、权限、更新或运行时契约变化；
- `MINOR`：向后兼容的用户可见功能或较大兼容性重构；
- `PATCH`：向后兼容的 bug 修复、UI 修正、性能、安全、文档或发布工具维护。

一个版本包含多类变更时，使用影响最高的一类。公开版本只使用稳定的 `X.Y.Z`，不再创建新的 `alpha`、`beta` 或 `rc` tag/Release；日常测试统一使用 Nightly Artifact。历史 `v2.0.0-beta.1` 至 `v2.0.0-beta.6` 保持原样，用于升级兼容与版本递增校验。稳定版本线从 `v2.1.0`、Android `versionCode=99` 开始。

每一个出现在 Releases 页面并供用户安装的版本都必须拥有：

- 唯一的稳定 `vX.Y.Z` annotated tag；
- 大于所有历史 GKD-SDP 版本且不可复用的 Android `versionCode`；
- `gradle/version.properties` 中的 `versionName`；
- `CHANGELOG.md` 对应版本段和日期；
- 独立的 GitHub Release、APK、`update.json` 和 `SHA256SUMS.txt`。

禁止创建字面量为 `latest` 的滚动 tag 或 Release。GitHub Release 的“Latest”标记只指向最新稳定版，不是一个名为 `latest` 的版本。Nightly 是 Actions Artifact，不是正式版本，也不能替代版本号或 Release 历史。

Nightly 通过 [`nightly.yml`](../.github/workflows/nightly.yml) 在 `main` 成功提交后构建 debug keystore 签名的非发布 APK。它不读取 release Environment，不创建 tag/Release，不覆盖任何历史资产，Artifact 保留 7 天。

## Release flow

```text
发布 PR：版本号 / CHANGELOG
        ↓
PR CI + dependency review + CodeQL
        ↓
合并 main，等待 main CI
        ↓
在已验证的 main 提交创建 annotated `vX.Y.Z` tag
        ↓
Release workflow：测试、Lint、签名、验签、checksum、attestation
        ↓
Draft Release：上传 APK/update.json/SHA256SUMS
        ↓
人工检查后发布不可变 Release
```

项目先完成自动化与静态资产验证，再公开 Release。真机/OEM、实际
Accessibility/Shizuku、`FLAG_SECURE` 截图合成、升级安装和应用内更新验证不
作为 Draft 发布门禁；Release 公开后由用户下载公开资产自行执行，维护者不得
把未执行的设备检查写成已通过。

正式发布不强推 tag、不覆盖已发布资产、不把 main 的每次提交写成 Release。Tag 必须与 `versionName` 完全匹配，稳定 SemVer 和 `versionCode` 都必须相对历史版本单调递增；若已发布版本有问题，创建下一个稳定 PATCH 版本，并在旧版本说明中标注已知问题。发布工作流发现同名 Release 已存在时会停止，不覆盖历史资产。

## Signing safety

Release signing secrets 只放在 GitHub `release` Environment：`GKD_STORE_FILE_BASE64`、`GKD_STORE_PASSWORD`、`GKD_KEY_ALIAS`、`GKD_KEY_PASSWORD`。不要把 keystore、密码、base64 或临时 `gradle.properties` 提交到 Git、Issue、日志或 Artifact。

维护者还要在 `release` Environment/repository variable 中设置 `RELEASE_CERT_SHA256`，并从可信本机记录证书指纹。工作流把 keystore 解码到 `$RUNNER_TEMP`，通过 `ORG_GRADLE_PROJECT_*` 仅向当前 Gradle 进程提供配置，完成后无论成功失败都会清理临时文件。证书指纹必须与 `RELEASE_CERT_SHA256` 对照；指纹变化必须停止发布并调查。维护者应在本地加密保存至少两份离线 keystore 副本，记录 alias、创建日期和指纹，不记录密码到仓库。

手动触发 `Release` workflow 时必须选择受保护的 `main` ref，默认 `publish=false`：它仍会签名、验签并上传 7 天 Artifact，但不会创建 Release。真实 tag 或明确 `publish=true` 的手动运行只会创建并上传稳定 Draft Release；维护者检查三项资产、notes、checksum 和 attestation 后，再在 GitHub UI 或 `gh release edit <tag> --draft=false --prerelease=false --latest` 手动发布。只有已有的、与 `versionName` 匹配的稳定 tag 才允许手动发布；正常发布应将 annotated `vX.Y.Z` tag 推送到 `main` 的已合并提交。来自 feature branch 的手动运行会被 job 条件跳过，避免把签名 Environment 暴露给未合并代码。

## Release notes

`CHANGELOG.md` 使用 Keep a Changelog，保持 `[Unreleased]` 和每个版本段。每个 Release body 只提取对应版本段；GitHub generated notes 仅作为 PR 分类补充，不把整份历史 changelog 重复粘贴到每个版本。

更新说明应告诉用户新增、改变、修复、安全影响、权限影响和已知限制，不要写未经验证的承诺。

## Failure and recovery

- tag workflow 因临时平台故障失败且代码、提交和 tag 均不变：从同一 tag 重新运行；需要修改代码时使用下一个稳定 PATCH 和更大的 `versionCode`，不移动已推送 tag。
- Draft Release 失败：确认没有公开资产后只清理对应 draft；不得删除或替换已发布 Release。
- 已发布 Release 出现问题：不覆盖 tag/资产，发布下一个稳定 PATCH。
- 签名失败或证书指纹不一致：停止发布，检查 Environment 和离线 keystore，不得绕过验签。
- GitHub API/Actions 暂时故障：保留待发布状态，恢复后从同一个已确认 commit 重新运行；不要移动已经公开的 tag。

发布完成后独立运行 `sha256sum --check SHA256SUMS.txt`、`gh attestation verify <apk> --repo wskeei/gkd-SDP`，并按 [`docs/testing/release-smoke-checklist.md`](testing/release-smoke-checklist.md) 做真机升级与应用内更新检查。
