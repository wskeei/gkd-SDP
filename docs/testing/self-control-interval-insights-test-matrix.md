# 数字自律间隔与拦截归因测试矩阵

本矩阵覆盖规则归因、申请节律、滚动洞察和 Room 迁移。自动化数据使用测试包名、虚构规则名和虚构申请理由；不写入真实 URL、屏幕文本或设备标识。
本次发布不把真机/OEM 验收设为门禁，device-only 状态统一为：**未执行；用户选择在公开 prerelease 下载后自行验证，不作为本次发布门禁。**

## 自动化矩阵

| requirement | data setup | automated test | expected result | device-only status |
| --- | --- | --- | --- | --- |
| exact selector attribution | 同一组两条带 key/无 key 规则、两个 app | `SelectorRuleSnapshotTest`、`SelfControlElapsedPolicyTest` | v2 key 隔离订阅/app/group/exact rule，来源只含安全名称/编号 | 未执行；用户选择在公开 prerelease 下载后自行验证，不作为本次发布门禁。 |
| mounted success boundary | accepted、duplicate、invalid、mount failure | `MountedInterceptRecorderTest`、`SelfControlAttemptRecordingContractTest` | 只有 `addView` 成功写 ActionLog/attempt；失败不写冷却或历史 | 同上 |
| missing ActionLog regression | selector 命中拦截分支 | `ActionLogOutcomeContractTest`、`RuleTriggerLogRepositoryTest` | intercepted 行写入，且不调用 `rule.trigger()` | 同上 |
| strict/resumable usage end | 离开、返回、到期、终止、断开 | `UsageGuardUsageEndPolicyTest`、`UsageGuardRecordDaoContractTest` | mark-only/mark+close 语义正确，未知结束不猜时间 | 同上 |
| cancel/no reset | 有 anchor 后取消表单 | `UsageGuardRequestIntervalContractTest`、`UsageRequestRhythmPresentationTest` | 不插入、不关闭、不重置 anchor | 同上 |
| 24h/7d/30d windows | 固定 now、边界内外样本 | `SelfControlInsightWindowPolicyTest`、`SelfControlIntervalPresentationTest` | 含边界、排除未来/旧样本，切换复用同一 30 天数据 | 同上 |
| >30 point aggregation | 31+ 原始点、空桶 | `SelfControlInsightWindowPolicyTest`、`SelfControlInsightAccessibilityContractTest` | 24/28/30 桶上限、桶内平均、原始样本数保留 | 同上 |
| 间用比 formula | gap 120m、时长 30/60、缺失/回拨 | `UsageRequestRhythmPolicyTest`、`UsageRequestRhythmPresentationTest` | 4.0×/2.0×，缺失不变成 0，当前值不进历史平均 | 同上 |
| three ratio averages | 同一数据集分别落入三个窗口 | `SelfControlInsightWindowPolicyTest`、`UsageRequestRhythmPresentationTest` | 近 24h/7d/30d 平均和有效样本数分别显示 | 同上 |
| legacy null data | 旧 row gap/end 为 null、gap=0 | `DigitalSelfDisciplineReviewPolicyTest`、`SelfControlIntervalRepositoryTest` | null 不纳入有效样本，0 合法；显示新口径积累/未知 | 同上 |
| review alignment | requestedAt 与 lastUsageEndedAt 不同 | `DigitalSelfDisciplineReviewPolicyTest` | 复盘使用冻结 `requestGapMs`，不再用相邻 requestedAt 配对 | 同上 |
| privacy | selector/url/reason/node text 作为输入 | `InterceptionSourcePresentationTest`、事件实体契约测试 | 持久化和语义不含敏感文本或 URL | 同上 |
| Room compatibility | schema 32 → 33 新列 | `scripts/tests/test_room_schema_33.py`、Room processor | 仅新增可空/默认字段，32 不变，无 destructive migration | 同上 |
| flavor parity | gkd/play 共享源码 | CI `:app:lintGkdDebug :app:lintPlayDebug`、双 flavor assemble | 两 flavor 编译/Lint 通过 | 同上 |

## 发布后由用户执行的设备检查

- 申请：首条、连续申请、取消、strict/resumable 离开/返回、升级迁移。
- 拦截：selector exact 来源、URL/app 来源、重复启动/挂载失败、10 秒倒计时、HOME 回退。
- UI：24h/7d/30d 下拉、间隔/间用比切换、长数据、360dp/大字体/TalkBack、深色主题。
- 平台：无障碍/Shizuku 两 owner、悬浮窗拒绝、系统 Back/Home/最近任务/Force stop/OEM 截图表现。

## 隐私静态检查

```bash
rg -n "reasonText|interceptMessage|pattern|redirectUrl|actualUrl" \
  app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlAttemptEvent.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt
rg -n "liveRegion" app/src/main/kotlin/li/songe/gkd/sdp/ui/component
```

第一条不得命中新事件字段、事件日志或图表语义摘要；第二条不得命中秒级洞察组件。
