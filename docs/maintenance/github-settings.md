# GitHub 仓库设置基线

本文件记录 GKD-SDP 依赖的 GitHub 原生设置。它不包含 token、secret 值或账户隐私信息；修改设置后应更新审计日期和相关命令输出摘要。

## 协作与安全

- 仓库公开，Issues、Discussions 已启用；`main` 合并后自动删除 head branch。
- Secret scanning、push protection、Dependabot security updates、automated security fixes 和 private vulnerability reporting 保持启用。
- CodeQL default setup 保留 GitHub Actions 分析；Kotlin/Java 使用仓库内固定 SHA 的 `codeql-java-kotlin.yml`，以 JDK 21 手动编译后扫描。项目 Gradle 插件要求 JDK 21，不能依赖 default setup 的 JDK 17 autobuild。
- Actions 默认 `GITHUB_TOKEN` 权限为 `read`，禁止 workflow 批准 pull request review。
- Actions SHA pinning required；所有工作流中的 `uses:` 使用完整 commit SHA。当前允许 action 来源为 `all`，后续移除不再使用的第三方 action 后再评估 allowlist。
- Actions spending/budget 保持在 GitHub Free 的标准 hosted runner 范围，不使用 larger runner。
- GitHub Immutable Releases 已启用（当前 API `enabled: true`）；它只保护启用后发布的 Release，旧 `latest` 快照不自动获得不可变保护。

## Main Ruleset

规则集 `main-quality-gate`（当前 ID `20211725`）作用于 `refs/heads/main`，状态为 active：

- 禁止删除和 non-fast-forward 更新；
- 只能通过 Pull Request 合并，允许 merge/squash/rebase；
- 必须解决 review conversations；审批数为 0，当前单维护者不要求 code owner approval；
- required checks 为 `quality`、`build`、`dependency-review`，并要求 branch 为最新状态；
- 仓库管理员只可通过 pull-request bypass，不允许直接绕过为 main 推送或强推。

当前规则使用 Rulesets API，因此旧的 branch-protection API 可能返回 404，不代表规则未启用。

## 审计命令

```bash
gh api repos/wskeei/gkd-SDP \
  --jq '{has_issues,has_discussions,delete_branch_on_merge,security_and_analysis}'
gh api repos/wskeei/gkd-SDP/actions/permissions/workflow
gh api repos/wskeei/gkd-SDP/actions/permissions
gh api repos/wskeei/gkd-SDP/rulesets/20211725
gh api repos/wskeei/gkd-SDP/immutable-releases
gh run list --repo wskeei/gkd-SDP --limit 20
gh release list --repo wskeei/gkd-SDP --limit 20
```

修改 required check 名称、workflow 权限或 release Environment 后，先在 PR 上验证，再更新 Ruleset；不要先写入一个尚不存在的 check，避免锁住 `main`。

## Release Environment

`release` Environment 只供 `.github/workflows/release.yml` 使用。正式发布所需 secret 和变量见 [`docs/releasing.md`](../releasing.md)；Environment 不应配置不必要的 reviewer，避免单维护者自锁。不要把 Environment secret 复制进 PR、Dependabot 或 Nightly workflow。
