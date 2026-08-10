# GKD-SDP 内容风格指南

本文档固定用户可见文案的术语、结构与语气。所有用户文案必须来自
`app/src/main/res/values/strings.xml`（简体中文基准）与
`app/src/main/res/values-en/strings.xml`（英文）；`quality-lint` 的
`HardcodedText` 检查阻止硬编码用户文案。

## 固定术语

| 英文 | 简体中文 |
|---|---|
| Usage Request | 使用申请 |
| request gap | 距离上次结束使用 |
| interval-to-request ratio | 间用比 |
| self-control review | 数字自律复盘 |
| interception source | 拦截来源 |
| Automation/Shizuku | 自动化模式（Shizuku） |
| Accessibility | 无障碍模式 |
| auto re-enable | 自动重开 |
| daily close quota | 每日关闭限额 |
| usage guard | 使用申请（守护） |
| focus mode | 专注模式 |
| app blocker | 应用拦截 |
| URL blocker | 网址拦截 |

解释口径：间用比 = 距离上次结束使用 ÷ 本次申请时长。

## 动作表达

- 使用“开启 / 关闭”“保存 / 取消”“删除 / 保留”，同一动作不混用
  “确定 / 好的 / 继续”。
- 破坏性动作必须写明对象名称与后果（例：删除规则组「X」？组内所有规则
  也会被删除。）。
- 全量删除要求输入“删除全部数据”。

## 语气与边界

- 文案只描述功能与数据，不承诺治疗、成瘾改善、意志力提升或心理效果。
- 使用中性、可证实的描述；避免“帮你戒掉”“养成习惯”类表述。
- 错误提示使用稳定错误码 + 可执行恢复动作 + 中性说明，不展示底层异常
  文本。

## 结构与格式

- 资源命名：`feature_element_state`（例：`usage_request_duration_label`）；
  迁移生成的哈希键以 `s_` 前缀并保持稳定。
- 数量使用 plurals；日期、数字与时长使用 Locale formatter，不拼接单位。
- 长文本使用 `\n` 分段；正文不设置 `maxLines=1`。
- `values` 为简体中文基准，`values-en` 为完整英文；两边 key 集合一致。
  `app_name`、技术协议名与用户生成标签不翻译。

## 一致性检查

- 每次修改用户文案同步更新中英两个文件与 `DesignTokenContractTest`
  无关的页面快照。
- 新增 key 必须同时出现在 `values` 与 `values-en`。
