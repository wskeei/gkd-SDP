# Upstream relationship

GKD-SDP 是基于 [gkd-kit/gkd](https://github.com/gkd-kit/gkd) 的非官方 fork。上游代码、选择器运行时和文档是重要基础，但 GKD-SDP 的数字自律功能、版本、Issue、Release 和支持入口由本仓库独立维护。

## Current base

- Upstream repository: `https://github.com/gkd-kit/gkd`
- Current recorded base: `v1.12.1`
- Last base audit: `2026-08-02`
- GKD-SDP repository: `https://github.com/wskeei/gkd-SDP`
- GKD-SDP product versions use their own `v2.x` tags; upstream `v1.x` tags are historical references and must not be reused for SDP releases.
- The product version source is [`gradle/version.properties`](gradle/version.properties); the release verifier enforces a matching `v${versionName}` tag and strictly increasing `versionCode` across SDP tags.

## Sync procedure

```bash
git remote add upstream https://github.com/gkd-kit/gkd.git  # only once
git fetch upstream --tags
git switch main
git pull --ff-only origin main
git switch -c codex/upstream-vX.Y.Z
```

在合并前先阅读上游 changelog 和 breaking changes，确认与无障碍、数字自律、数据库、签名和应用 ID 的影响。使用普通 merge/可审计的冲突解决，不要用 `git reset --hard upstream/main` 覆盖 GKD-SDP 历史。

同步分支必须：

1. 运行 selector JVM、app unit test、Android Lint 和构建；
2. 检查 Room schemas/migrations；
3. 手工回归 Accessibility 和 Automation/Shizuku 两种模式；
4. 手工回归应用拦截、使用申请、Self Control、URL 拦截、自动重开和无障碍守护；
5. 通过 PR 合并到 `main`；
6. 更新本文的 base tag 和日期，并在 CHANGELOG 的 `[Unreleased]` 记录来源。

上游同步不是自动发布；只有明确准备好的 GKD-SDP 版本才创建自己的 tag。
