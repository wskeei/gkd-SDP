# 数字自律间隔洞察测试矩阵

这份矩阵补充 `release-smoke-checklist.md`，用于 beta.2 → beta.3 的真实设备回归。所有测试数据使用测试包名、虚构规则名和虚构申请理由，不记录真实 URL、截图或个人内容。

## 自动化覆盖

| 区域 | 覆盖内容 | 测试位置 |
| --- | --- | --- |
| 间隔算法 | 稳定 key 配对、负值过滤、最近 5 个、平均/中位数、当前值不进入历史、超大整数 | `SelfControlIntervalPolicyTest` |
| Room/保留 | 事件表字段、DAO 事务、90 天清理、10,000 行上限、31 → 32 schema | `SelfControlAttemptDaoContractTest`、`SelfControlAttemptEventDaoContractTest`、`32.json` |
| 数据协调 | 申请范围前驱、拦截 descriptor、标签回退、申请/拦截来源合并 | `SelfControlIntervalRepositoryTest`、`SelfControlAttemptRecordingContractTest` |
| 弹窗 presentation | 0/1/5 历史、最多 6 柱、本次增长、平均差值、无敏感文案、超大跨度提示 | `SelfControlIntervalPresentationTest` |
| 复盘策略 | 自然日/DST、跨范围前驱、按后一个事件归日、筛选、每日中位数、最近 10 条、比较门槛 | `DigitalSelfDisciplineReviewPolicyTest` |
| 复盘 presentation | 今日/多日图表点、空状态、申请/拦截筛选可见性、首页摘要 | `DigitalSelfDisciplineReviewPresentationTest` |

## 真实设备矩阵

| 场景 | 操作 | 预期 |
| --- | --- | --- |
| 申请首条 | 打开纳入申请的测试应用，取消表单，再次打开 | 取消不产生记录；第二次仍显示无历史或原有锚点，不阻塞输入 |
| 申请连续 | 提交两次合法申请，中间等待并再次打开 | 计时以最后成功提交为锚点；出现最近间隔、平均/中位数和本次动态柱 |
| 应用拦截 | 触发同一包名两次，再触发另一包名 | 同一包名才配对；另一包名从首条开始，不跨应用平均 |
| 选择器拦截 | 同一订阅/组在两个实际应用触发 | 每个实际前台应用分开统计；继续/退出、冷却和回桌面行为不变 |
| 网址拦截 | 同一规则触发两次，检查记录和界面 | 以规则 ID 配对；图表/日志无实际 URL、pattern 或页面文本 |
| 挂载边界 | 拒绝悬浮窗权限或制造重复 start | 不新增事件；原 cooldown 恢复和业务判定保持原样 |
| 数据库异常 | 在测试环境阻断数据库读取后打开拦截/申请 | 洞察显示不可用；申请提交、拦截退出/继续和 10 秒倒计时仍可用 |
| 复盘范围 | 分别选择今日、近 7 天、近 30 天和上一周期无数据 | 今日显示最近样本；多日显示每日中位数；缺失日期不绘制 0；比较显示样本不足 |
| 跨午夜 | 23:59 触发一次，00:01 触发一次并返回复盘 | 间隔归到第二天；首页摘要和复盘范围自动切换到新日期 |
| 升级 | 安装 beta.2，制造申请/拦截记录，覆盖安装候选版本 | Room 31 → 32 成功；既有申请和最后拦截时间保留；第一条新拦截能承接旧锚点 |
| 保留 | 构造超过 90 天和超过 10,000 行的事件 | 旧事件按规则清理，最新 10,000 行保留；`self_control_attempt` 最后状态仍存在 |
| 无障碍 | TalkBack、200% 字体、360dp、横屏、深色 | 图表有稳定文字摘要和明细；秒级数字不主动播报；控件不少于 48dp |

## 隐私/静态检查

```bash
rg -n "reasonText|interceptMessage|pattern|redirectUrl|actualUrl" \
  app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlAttemptEvent.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt
rg -n "liveRegion" app/src/main/kotlin/li/songe/gkd/sdp/ui/component
```

第一条只允许命中测试说明或既有调用方，不得出现在新事件实体、事件日志或图表语义摘要；第二条不得命中秒级洞察组件。
