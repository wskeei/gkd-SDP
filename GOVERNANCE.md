# Governance

## Current model

GKD-SDP 当前是个人维护的公开 fork。`@wskeei` 是项目维护者、路线决策者和 Release owner，对合并、版本、签名发布和仓库设置拥有最终决定权。

这是透明的维护责任说明，不代表贡献者的讨论没有价值。设计决策应尽量在 Issue、Discussion 或 Pull Request 中留下可搜索的理由和验证证据。

## Quality and release gates

- `main` 只接受 Pull Request；Ruleset 要求 `quality`、`build` 和 `dependency-review` checks 全部通过并解决 conversations。
- CodeQL、Dependabot、secret scanning 和 private vulnerability reporting 是 GitHub 原生安全基线。
- 每个正式 `vX.Y.Z[-pre]` tag 必须匹配 `gradle/version.properties`，递增 `versionCode`，并通过 release Environment 的签名、证书指纹、SHA-256 和 provenance 校验。
- `main` 的 Nightly 只产生短期 debug Artifact；Releases 页面只保留不可覆盖的 tagged 版本。

## Contributions and maintainers

- 任何人都可以提交 Issue、Discussion、PR 和文档改进。
- 贡献不保证合并；维护者会综合产品方向、隐私/安全风险、Android 兼容性和长期维护成本判断。
- 持续提供高质量反馈或代码的贡献者，未来可能被邀请为 triager 或 maintainer；没有自动晋升承诺。
- 在有可信共同维护者之前，不引入强制双人审批、复杂委员会或付费协作平台。

## Releases and emergency bypass

正式版本必须经过 PR、GitHub Actions、版本校验、签名验证和 Release checklist。已发布版本的 tag、APK 和说明不覆盖、不强推。

只有 GitHub 平台故障或严重安全事件才允许使用 Ruleset 的管理员 PR bypass。维护者必须在事后补充 Issue 或私密安全记录、重新运行 CI，并发布后续修复版本；bypass 不能成为日常直推入口。

## Future changes

如果项目出现可信的共同维护者、稳定的贡献群体或组织化需要，再公开讨论 GitHub Organization、第二名 release owner、审批规则和继任方案。在此之前，简单、可恢复和低维护成本优先。
