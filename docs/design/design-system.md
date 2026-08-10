# GKD-SDP 设计系统

本文档固定 UI 视觉与交互契约。Token 定义在
`app/src/main/kotlin/li/songe/gkd/sdp/ui/style/` 下的 `*Tokens.kt`，
`DesignTokenContractTest` 守护数值；变更 Token 必须同步修改测试与本文档。

## 颜色角色

| 角色 | Light | Dark |
|---|---:|---:|
| primary | #4F46E5 | #A5B4FC |
| onPrimary | #FFFFFF | #1E1B4B |
| primaryContainer | #E0E7FF | #312E81 |
| onPrimaryContainer | #1E1B4B | #E0E7FF |
| secondary | #0F766E | #5EEAD4 |
| onSecondary | #FFFFFF | #042F2C |
| secondaryContainer | #CCFBF1 | #134E4A |
| onSecondaryContainer | #042F2C | #CCFBF1 |
| tertiary | #B45309 | #FCD34D |
| onTertiary | #FFFFFF | #3A2E00 |
| tertiaryContainer | #FFE7C2 | #6B4E00 |
| onTertiaryContainer | #3A2E00 | #FFE7C2 |
| error | #B3261E | #FFB4AB |
| onError | #FFFFFF | #690005 |
| errorContainer | #F9DEDC | #93000A |
| onErrorContainer | #410E0B | #FFDAD6 |
| background | #F8FAFC | #0F172A |
| onBackground | #0F172A | #F8FAFC |
| surface | #FFFFFF | #111827 |
| onSurface | #0F172A | #F8FAFC |
| surfaceVariant | #F1F5F9 | #1E293B |
| onSurfaceVariant | #475569 | #CBD5E1 |
| outline | #64748B | #94A3B8 |
| outlineVariant | #CBD5E1 | #475569 |

- 语义不得只用颜色表达：状态变化必须配合文字、图标或 role。
- 拦截/错误类操作使用 error 角色；成功使用 secondary；强调使用 primary。

## 栅格与尺寸

- 间距刻度：4、8、12、16、20、24、32、40、48dp。
- 页面水平 padding：紧凑窗口 <600dp 用 16dp，中等 600–839dp 用 24dp，
  扩展 ≥840dp 用 32dp（`ResponsiveTokens.pageHorizontalPadding`）。
- 内容列最大宽度：表单 720dp，列表与复盘 960dp。
- 触控目标最小 48×48dp；图标视觉尺寸 20/24dp，可点击图标必须包在
  ≥48dp 触控壳内。
- 圆角：8、12、16、24dp；卡片默认 16dp。
- 顶部栏 64dp；紧凑底部导航使用 Material 3 默认高度。

## 字体

- displaySmall 32/40、headlineSmall 24/32、titleLarge 20/28、
  titleMedium 16/24、bodyLarge 16/24、bodyMedium 14/20、labelLarge 14/20
  （字号/行高，sp 单位）。
- 使用系统无衬线字体，不捆绑字体文件。
- 字体缩放完全交给 sp；body 文本不设置 `maxLines=1`，溢出时由
  `TextOverflow.Ellipsis` 处理。

## 动效

- 微交互 120ms、页面/主题过渡 180ms、强调过渡 240ms。
- easing 固定 `FastOutSlowInEasing`；进度变化使用 `LinearEasing`。
- 系统开启"移除动画"时（`LocalMotionDurationScale.scaleFactor=0`）所有
  非必要动画时长为 0ms。
- 秒级倒计时只更新文本，不做每秒缩放/闪烁，不设置持续 live region。

## 组件层级

1. `AppScaffold`：页面骨架（顶栏/内容列/底部动作栏）。
2. `ContentState`：Loading / Empty / Content / Error 四态容器。
3. Section 组件：页面内分组（PreferenceBlock、SectionCard 等）。
4. 基础控件：按钮、chip、输入框、开关。

## 状态表达

- Loading 超过 400ms 才显示进度，避免闪烁。
- Empty 包含标题、说明与单一主动作。
- Error 包含稳定错误码、说明、重试与上下文恢复动作。
- 破坏性确认使用 error 色与明确对象名；全量删除要求输入"删除全部数据"。

## 图表

- 24 小时按小时、7 天按 6 小时、30 天按日历日聚合。
- 少于 4 个有效点只显示点图与列表；4–48 个点显示完整点；超过 48 个点只
  显示聚合桶。
- 图表同时提供摘要文字、点选明细与可访问数据表；触摸点播报与 TalkBack
  使用同一字符串。
- 不用 3D、渐变面积、双轴、饼图、装饰动画或红绿二元评价。
