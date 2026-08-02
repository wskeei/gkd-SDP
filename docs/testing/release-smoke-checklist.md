# Release smoke checklist

这是版本化 Release workflow 的真实设备检查，不由 JVM/Actions 自动测试替代。记录设备型号、Android 版本、应用版本、测试日期和未覆盖项。旧 `latest` 快照是历史自动构建，不能满足这份清单的 Release 完整性项目。

## 安装与版本

- [ ] 从上一稳定版升级安装，应用 ID、签名和本地数据保持兼容。
- [ ] 冷启动、热启动、应用恢复和首次启动迁移正常。
- [ ] About 页面显示正确 `versionName`/`versionCode`。
- [ ] Release 页面 APK、`update.json`、CHANGELOG 版本段和 SHA256 一致。
- [ ] 应用内“检查更新”稳定版/测试版渠道符合预期，不指向上游 GKD。

## Runtime and permissions

- [ ] Accessibility 模式启动、停止、重新连接和前台应用识别正常。
- [ ] Automation/Shizuku 模式启动、停止、前台应用识别和回桌面正常。
- [ ] 通知权限、通知渠道、前台服务和悬浮窗拒绝路径有清晰提示。
- [ ] 普通 Back、Home、最近任务划走和系统 Force stop 的行为分别符合文档。

## Digital self-discipline

- [ ] 使用申请弹窗、理由、标签、时长、申请记录和倒计时正常。
- [ ] 倒计时横向悬浮条包含理由；截图/录屏不显示安全内容（按 OEM 实际表现记录）。
- [ ] 应用拦截和应用组时间规则命中；无有效时间规则时不会误拦截。
- [ ] Self Control 拦截、经过时长和只回桌面的行为正常。
- [ ] URL 拦截在受支持浏览器中命中并正确处理跳转/覆盖层。
- [ ] 专注模式白名单、锁定和退出路径正常。
- [ ] 自动重开保护、锁定模式和无障碍守护通知时间线正常。
- [ ] 无障碍关闭后守护的通知、倒计时、悬浮窗、前往按钮和恢复路径正常。

## Release integrity

- [ ] `apksigner` 证书 SHA-256 与记录一致。
- [ ] `sha256sum --check SHA256SUMS.txt` 通过。
- [ ] `gh attestation verify` 通过。
- [ ] 日志和 Artifact 没有 keystore、密码、token 或个人申请内容。

## GitHub Actions dry-run

- [ ] 在 `release` Environment 配置四个 signing secrets 和 `RELEASE_CERT_SHA256` variable。
- [ ] 从受保护的 `main` ref 手动运行 `Release` workflow，保持 `publish=false`；没有明确 tag 时只产生 7 天 Artifact，不创建 Release/tag；feature branch 运行应被跳过。
- [ ] 若提供已有 tag，检查元数据、tag ancestry、签名指纹、APK SHA-256 和 provenance attestation。
- [ ] 真实 tag workflow 成功后先检查 Draft Release 的三项资产和 release notes，确认无敏感内容、checksum 和 attestation 后再手动发布。
