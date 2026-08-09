# 使用申请节律与数字自律复盘重构 Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复使用申请标签排序和最近间隔图表少点问题，把“距离上次结束使用”恢复到申请表单顶部，把间用比反馈与申请时长合并并统一计算展示单位，重构数字自律复盘的信息架构与图表表达，完成自动化验证、功能 PR、版本 PR 和 `v2.0.0-beta.5` prerelease 发布。

**Architecture:** 保留 `UsageGuardRecord`、`requestGapMs`、`lastUsageEndedAt` 和 `SelfControlAttemptEvent` 作为事实来源，不新增数据库字段，不升级 Room schema。标签最终顺序由 `UsageGuardTagDao.queryAll()` 统一提供；最近间隔组件保留 30 天一次性加载方式，但把“总记录、有效样本、图形点”分开建模，并只在有效样本超过窗口上限时执行时间桶聚合；间用比继续使用毫秒级原始值计算，在纯 Kotlin presentation policy 中生成同单位公式；数字自律复盘只保留一条 current/previous 数据流，并按“概览、趋势、分布、明细”生成一个统一的 `ReviewSummary`。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room 33（仅改 DAO 查询，不改 schema）、Kotlin Coroutines/Flow、Vico 2.0.0-alpha.28、JUnit 4、Python/shell 发布校验、GitHub Actions、GitHub Releases。

---

## 0. 固定执行合同

按本计划从 Task 1 顺序执行到 Task 13，不改变任务顺序，不引入第二套数据源，不新增依赖，不修改 Room 版本，不回填旧记录，不把缺失间隔写成 `0`，不把真机/OEM 验收设为 prerelease 发布门禁。

实现期间固定保留以下行为：

1. 使用申请取消时不插入 `UsageGuardRecord`，不关闭旧记录，不重置结束时间锚点。
2. `requestGapMs` 仍按“本次申请时间减去上一份记录最后一次可确认的实际结束使用时间”冻结。
3. strict/resumable 的离开、返回、到期、主动结束和 runtime disconnect 语义保持不变。
4. 使用申请 Overlay 继续使用 `FLAG_SECURE`。
5. 申请图表一次加载当前应用近 30 天数据，24 小时、7 天、30 天切换只做内存过滤和展示，不在切换时重新查询 Room。
6. 图表、复盘或 presentation 失败不得影响申请提交、倒计时、拦截退出、HOME 回退和 Overlay 生命周期。
7. 用户申请理由、完整 URL、URL pattern、selector、node text、屏幕文本和凭据不得进入新增日志、测试夹具、图表语义或发布说明。
8. `gkd` 与 `play` 两个 flavor 必须继续编译；本任务不增加 gkd-only API。
9. 发布前不执行真机/OEM 验收；公开 `v2.0.0-beta.5` 后由用户下载验证。

## 1. 已确认代码根因与固定修复目标

执行实现前，把以下事实作为验收基线写入 PR 描述，不再重新选择方案：

1. `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt` 的 `queryAll()` 当前使用 `ORDER BY is_preset DESC, created_at ASC`。预设“其他”早于所有自定义标签，因此新增标签只能出现在“其他”后面。
2. `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt` 当前先渲染标签、理由、申请时长，再渲染 `SelfControlElapsedCard`，与“距离上次结束使用位于顶部”的固定顺序不一致。
3. `app/src/main/kotlin/li/songe/gkd/sdp/util/SelfControlInsightWindowPolicy.kt` 当前对所有样本无条件按 1 小时、6 小时、1 天分桶。多次申请落入同一桶时，即使总样本远低于 24/28/30 点上限，也会被压成一根柱。这是“申请很多但图上只有一两根”的直接根因。
4. `app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt` 当前通过 `mapNotNull` 丢弃 `requestGapMs == null` 的申请行。统计口径本身没有把空值当成 0，但 UI 无法告诉用户“总申请数”和“有效间隔数”之间的差值，造成继续漏数的观感。
5. `UsageRequestRhythmPolicy.ratio()` 当前已经用毫秒除以分钟转换后的毫秒，核心计算没有整数分钟截断；`UsageRequestRhythmSummary` 却直接显示“几秒/几小时 ÷ 几分钟”，视觉公式没有统一单位，而且小于 `0.01` 的正数会被格式化得像 `0.0×`。
6. `UsageGuardReviewPage` 当前同时维护 `reviewUiStateFlow` 和 `usageGuardSummaryFlow` 两条统计流；页面在申请/拦截主摘要之后固定追加“使用申请补充复盘”，拦截页也会出现申请统计，数据层次重复且上下文不一致。

固定完成结果：

- 标签顺序为“非其他预设标签 → 自定义标签（创建时间升序）→ 其他”。
- 表单顺序为“标题和模式 → 距离上次结束使用及历史图表 → 选择标签 → 申请理由 → 申请时长及间用比反馈 → 提交/取消”。
- 24 小时有效样本不超过 24 条、7 天不超过 28 条、30 天不超过 30 条时，一条有效样本对应一个图形点。
- 超过上限时才按现有 1 小时、6 小时、1 天桶聚合；统计继续使用全部有效原始样本。
- 所有洞察区域明确显示“总记录 N 条 · 有效样本 M 条 · 图形点 P 个”，并在存在空间隔时显示“未纳入 K 条”。
- 间用比公式的两个操作数使用同一单位，正的小数不显示成 0。
- 复盘页只显示当前类型的数据，并使用一个统一 summary 和一条 UI state 流。

## 2. 外部方案调研结果对应的固定 UI 规则

把下列公开产品规则直接落实到本项目，不再增加产品方案分支：

1. 采用 Apple Screen Time 和 Android Digital Wellbeing 的“先选时间范围，再看总览，再进入应用/类别明细”层次。参考：
   - <https://support.apple.com/en-lamr/guide/iphone/iphbfa595995/ios>
   - <https://support.google.com/android/answer/9346420?hl=en>
2. 采用 RescueTime 的“顶部回答总量、变化、去向三个问题，下面用排行和占比展开”的结构。参考：
   - <https://help.rescuetime.com/article/30-dashboard>
   - <https://help.rescuetime.com/article/435-rescuetime-for-android-features>
3. 采用 Oura Trends 的“一张图只聚焦一个指标，突出本期值和平均值，再切换日/周/月”的结构；标签只作为趋势上下文和分布信息，不与数值混成第二纵轴。参考：
   - <https://support.ouraring.com/hc/en-us/articles/360055983614-Using-Trends>
   - <https://support.ouraring.com/hc/en-us/articles/360038676993-Using-Tags>
4. 采用 Android Compose semantics 和 window-size guidance：图表提供完整文字摘要，控件阅读顺序与视觉顺序一致，48dp 触控区域，布局按可用宽度适配。参考：
   - <https://developer.android.com/develop/ui/compose/accessibility/semantics>
   - <https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes>
5. 页面保持现有 Material 3 主题、圆角、surface token、字体和图标体系；不加入自定义字体、渐变装饰、emoji 图标、红绿好坏评价和医学/心理效果文案。

固定复盘信息架构：

```text
Top App Bar：数字自律复盘
├─ 主类型 Tab：使用申请 | 拦截记录
├─ 范围分段控件：今日 | 近 7 天 | 近 30 天
├─ 拦截子类型（仅拦截记录）：全部 | 应用 | 选择器 | 网址
├─ 数据概览卡
│  ├─ 使用申请：申请次数 | 申请总时长 | 平均未使用间隔 | 平均间用比
│  ├─ 拦截记录：拦截次数 | 有效间隔 | 平均间隔 | 中位间隔
│  └─ 数据完整度：总记录 | 有效间隔 | 有效间用比 | 未纳入
├─ 趋势卡
│  ├─ 使用申请指标：间用比（默认） | 未使用间隔
│  ├─ 拦截记录指标：拦截间隔
│  ├─ 本期平均、上一周期、差值、样本数
│  └─ 单指标时间趋势 + 可展开文字明细
├─ 分布卡
│  ├─ 使用申请：应用分布 | 标签分布 | 结束状态 | 集中时段
│  └─ 拦截记录：高频拦截目标
└─ 最近明细卡：最多 10 条，时间倒序
```

## Task 1: 建立实现工作树并锁定基线

**Files:**

- Read: `AGENTS.md`
- Read: `README_DEV.md`
- Read: `docs/releasing.md`
- Read: `docs/testing/self-control-interval-insights-test-matrix.md`
- Read: `docs/testing/release-smoke-checklist.md`
- Read: `gradle/version.properties`
- Read: `CHANGELOG.md`

**Step 1: 检查用户工作区并同步远端引用**

```bash
cd /Users/zeiy/Project/gkd-SDP
git status --short --branch
git fetch origin --prune --tags
git worktree list
```

Expected: 记录主工作区现有修改和未跟踪文件，不清理、不暂存、不格式化这些文件；`origin/main` 包含 `v2.0.0-beta.4`。

**Step 2: 创建固定实现分支和独立工作树**

```bash
git check-ignore -q .worktrees
git worktree add .worktrees/usage-rhythm-review-refinement \
  -b codex/usage-rhythm-review-refinement origin/main
cd .worktrees/usage-rhythm-review-refinement
git status --short --branch
```

Expected: 工作树干净，分支为 `codex/usage-rhythm-review-refinement`，基线为 `origin/main`。

**Step 3: 锁定版本和 schema 基线**

```bash
cat gradle/version.properties
git tag --list 'v2.0.0-beta.4'
test -f app/schemas/li.songe.gkd.sdp.db.AppDb/33.json
```

Expected:

```text
versionName=2.0.0-beta.4
versionCode=96
```

Room 最新 schema 为 33。本功能分支不修改上述版本值，不生成 schema 34。

**Step 4: 运行基线检查**

```bash
./gradlew :app:testGkdDebugUnitTest
git diff --check
```

Expected: 单元测试通过，`git diff --check` 无输出。

## Task 2: 让“其他”永久排在标签列表最后

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/data/UsageGuardTagDaoOrderContractTest.kt`

**Step 1: 先写失败测试，固定 DAO 完整排序规则**

在 `UsageGuardTagDaoOrderContractTest.kt` 读取即将由 DAO 注解直接使用的
`UsageGuardTag.QUERY_ALL_SQL`，断言规范化后的 SQL 严格包含以下排序：

```sql
ORDER BY
  CASE WHEN TRIM(name) = '其他' THEN 1 ELSE 0 END ASC,
  is_preset DESC,
  created_at ASC,
  id ASC
```

该 SQL 固定表达：

- 非“其他”预设标签先按 `createdAt`、`id` 升序；
- 自定义标签随后按 `createdAt`、`id` 升序；
- 名称为“其他”的行始终最后；
- 相同时间戳由 `id` 保证稳定顺序。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*UsageGuardTagDaoOrderContractTest'
```

Expected: FAIL，因为 `QUERY_ALL_SQL` 尚不存在，当前 DAO 查询也没有“其他”末位权重。

**Step 2: 实现 DAO 的唯一查询常量**

在 `UsageGuardTag` companion object 定义编译期常量：

```kotlin
const val OTHER_TAG_NAME = "其他"
const val QUERY_ALL_SQL = """
    SELECT * FROM usage_guard_tag
    ORDER BY
      CASE WHEN TRIM(name) = '其他' THEN 1 ELSE 0 END ASC,
      is_preset DESC,
      created_at ASC,
      id ASC
"""
```

把 `UsageGuardTagDao.queryAll()` 注解改为 `@Query(QUERY_ALL_SQL)`。Overlay、
`UsageGuardVm`、`UsageGuardEngine` 继续只消费 `queryAll()`，不在调用方重复排序。

**Step 3: 运行测试和编译契约**

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*UsageGuardTagDaoOrderContractTest'
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
```

Expected: PASS；Room/KSP 接受新的查询；不产生 schema diff。

**Step 4: 提交标签修复**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/data/UsageGuardTagDaoOrderContractTest.kt
git commit -m "fix: keep other usage tag last"
```

## Task 3: 保留全部申请行并区分记录数、有效样本数和图形点数

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/util/SelfControlInsightWindowPolicy.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepositoryTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestIntervalContractTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/util/SelfControlInsightWindowPolicyTest.kt`

**Step 1: 先写失败测试，暴露当前漏数**

在 `SelfControlIntervalRepositoryTest` 增加一个近 24 小时含 6 条申请行的数据集：

- 4 条 `requestGapMs >= 0`；
- 2 条 `requestGapMs == null`；
- 全部属于同一 app；
- 全部 `requestedAt` 在范围内。

断言 `loadUsageRequestOverlayData().samples.size == 6`，并断言 2 条样本的 `gapMs == null`。

在 `UsageGuardRequestIntervalContractTest` 把现有 “uses all stored gaps” 断言扩展为：

- `samples.size` 等于该 app 范围内的全部申请行；
- `mapNotNull { gapMs }` 只包含可信 gap；
- 其他 app 行仍不进入数据集。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*SelfControlIntervalRepositoryTest' \
  --tests '*UsageGuardRequestIntervalContractTest'
```

Expected: FAIL，因为 repository 当前通过 `mapNotNull` 删除空 gap 行。

**Step 2: Repository 保留空 gap 行**

把 `loadUsageRequestOverlayData()` 的样本映射改为一行对应一个 `IntervalSample`：

```kotlin
rows.map { row ->
    IntervalSample(
        id = row.id,
        occurredAtEpochMs = row.requestedAt,
        gapMs = row.requestGapMs?.takeIf { it >= 0L },
        requestedDurationMinutes = row.requestedDurationMinutes,
    )
}
```

负 gap 转为 `null`，不删除记录，不写回数据库，不把空值转为 0。

**Step 3: 扩展 series 数据完整度字段**

在 `SelfControlInsightWindowPolicy.Series` 固定增加：

```kotlin
val aggregationApplied: Boolean
val aggregationLabel: String?
val excludedSampleCount: Int
```

继续保留：

- `rawSampleCount`：窗口内全部记录数；
- `stats.sampleCount`：当前 metric 可计算的有效样本数；
- `points.size`：实际绘制点数；
- `excludedSampleCount = rawSampleCount - stats.sampleCount`。

`aggregationLabel` 固定为：

- 24 小时：`按 1 小时聚合`；
- 7 天：`按 6 小时聚合`；
- 30 天：`按 1 天聚合`；
- 未聚合：`null`。

**Step 4: 运行 repository 和 policy 测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*SelfControlIntervalRepositoryTest' \
  --tests '*UsageGuardRequestIntervalContractTest' \
  --tests '*SelfControlInsightWindowPolicyTest'
```

Expected: repository/contract 测试 PASS；下一任务新增的逐点图测试仍未完成。

## Task 4: 修复低样本量被无条件分桶的图表缺陷

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/util/SelfControlInsightWindowPolicy.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalInsightCard.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalChart.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/util/SelfControlInsightWindowPolicyTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalPresentationTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/component/SelfControlInsightAccessibilityContractTest.kt`

**Step 1: 用边界测试固定“先逐条、后聚合”**

在 `SelfControlInsightWindowPolicyTest` 增加以下失败断言：

1. 24 小时内 12 条有效样本全部发生在同一小时，`points.size == 12`，每点 `sampleCount == 1`，`aggregationApplied == false`。
2. 24 小时内 24 条有效样本，`points.size == 24`，不聚合。
3. 24 小时内 25 条有效样本，执行 1 小时桶聚合，`points.size <= 24`，`stats.sampleCount == 25`，`aggregationApplied == true`。
4. 7 天内 28 条有效样本不聚合；29 条执行 6 小时桶聚合。
5. 30 天内 30 条有效样本不聚合；31 条执行 1 天桶聚合。
6. 同一毫秒发生的两条记录按 `id` 稳定排序并保留为两个独立点。
7. 40 条总记录中只有 2 条对当前 metric 有效时，使用 2 个逐条点，不因总记录数 40 而聚合。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*SelfControlInsightWindowPolicyTest'
```

Expected: FAIL，因为当前实现无条件 groupBy 时间桶。

**Step 2: 在 aggregate 中先生成有效值行**

把 `aggregate()` 的内部步骤固定为：

```text
窗口过滤全部记录
→ 按 occurredAt、id 排序
→ 为当前 metric 计算 value
→ 保留 value 非空的有效值行
→ 用全部有效值行计算平均、中位、最小、最大和样本数
→ 有效值行数不超过上限时逐条生成点
→ 有效值行数超过上限时按窗口时间桶生成平均点
```

聚合阈值只使用有效值行数，不使用总记录数。

**Step 3: 给图形点保存稳定来源 ID**

把 `ChartPoint` 扩展为：

```kotlin
val sourceIds: Set<Long>
```

逐条点保存一个 ID；聚合点保存桶内全部 ID。`SelfControlInsightPresentation` 使用 `sourceIds.contains(currentEventId)` 标记当前拦截事件，不再通过相同 `bucketStartAt` 推断，防止相同时间戳误标多个点。

逐条点的时间标签固定为：

- 24 小时：`HH:mm`；
- 7 天：`MM-dd HH:mm`；
- 30 天：`MM-dd HH:mm`。

聚合点继续使用当前桶标签：24 小时和 7 天显示日期时间，30 天显示 `MM-dd`。

**Step 4: 更新 presentation 的数据完整度文案**

`SelfControlInsightPresentation.semanticSummary` 固定包含：

```text
<范围><指标>平均 … · 中位 … · 总记录 N 条 · 有效样本 M 条 · 图形点 P 个
```

存在空 gap/ratio 时追加：

```text
未纳入 K 条（首次记录、旧版记录或实际结束时间未知）
```

执行聚合时追加 `aggregationLabel`；不聚合时显示“逐条显示”。

`supportingText` 使用同一数据，不再用 `points.any { sampleCount > 1 }` 猜测聚合状态。

**Step 5: 更新图表参数和文字明细**

把 `SelfControlWindowChart` 的 `aggregated: Boolean` 替换为 `aggregationLabel: String?`。语义摘要固定读出聚合粒度。文字明细逐条显示：

- 原始点：`时间：值`；
- 聚合点：`时间桶：平均值，共 X 条`；
- 当前拦截所在点：追加“本次”。

不通过颜色单独表达当前点或聚合状态。

**Step 6: 更新 presentation 和无障碍测试**

在 `SelfControlIntervalPresentationTest` 断言：

- 12 条同小时数据生成 12 个 `chartPoints` 和 12 个 `textRows`；
- 25 条数据的语义同时包含“有效样本 25 条”和聚合粒度；
- 相同时间戳不同 ID 时只标记包含 `currentEventId` 的点；
- 6 条记录、4 条有效时语义包含总记录 6、有效 4、未纳入 2、图形点 4。

在 `SelfControlInsightAccessibilityContractTest` 断言图表与文字明细一一对应，语义不包含 `reasonText`、URL、pattern 或 selector。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*SelfControlInsightWindowPolicyTest' \
  --tests '*SelfControlIntervalPresentationTest' \
  --tests '*SelfControlInsightAccessibilityContractTest'
```

Expected: PASS。

**Step 7: 提交样本与图表修复**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/util/SelfControlInsightWindowPolicy.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalInsightCard.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalChart.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepositoryTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestIntervalContractTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/SelfControlInsightWindowPolicyTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalPresentationTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/ui/component/SelfControlInsightAccessibilityContractTest.kt
git commit -m "fix: preserve usage insight samples before aggregation"
```

## Task 5: 统一间用比计算单位和显示公式

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageRequestRhythmPolicy.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/UsageRequestRhythmPresentation.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageRequestRhythmPolicyTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/component/UsageRequestRhythmPresentationTest.kt`

**Step 1: 先写跨单位失败测试**

在 `UsageRequestRhythmPolicyTest` 增加：

```text
3 秒 ÷ 2 分钟 = 0.025
2 小时 ÷ 30 分钟 = 4.0
3 小时 ÷ 120 分钟 = 1.5
0 秒 ÷ 2 分钟 = 0.0
1 秒 ÷ 10 分钟 > 0 且显示 <0.01，不显示 0.0
```

断言计算先把申请分钟转换为毫秒，再执行 `BigDecimal` 除法；不把 gap 预先截断为整分钟。

在 `UsageRequestRhythmPresentationTest` 增加公式断言：

```text
3 秒 ÷ 120 秒 = 0.03×
120 分钟 ÷ 30 分钟 = 4.0×
3 小时 ÷ 2 小时 = 1.5×
```

运行：

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageRequestRhythmPolicyTest' \
  --tests '*UsageRequestRhythmPresentationTest'
```

Expected: FAIL，因为当前 presentation 没有统一单位公式，小正数格式也不符合新规则。

**Step 2: 增加公式单位模型**

在 `UsageRequestRhythmPolicy` 定义：

```kotlin
enum class FormulaUnit(val divisorMs: Long, val label: String) {
    SECONDS(1_000L, "秒"),
    MINUTES(60_000L, "分钟"),
    HOURS(3_600_000L, "小时"),
}

data class Formula(
    val gapValue: BigDecimal,
    val durationValue: BigDecimal,
    val unit: FormulaUnit,
    val ratio: Double,
)
```

公共单位固定按两个操作数中较短的量级选择：

```text
gap >= 1 小时 且 duration >= 1 小时 → 小时
否则 gap >= 1 分钟 且 duration >= 1 分钟 → 分钟
否则 → 秒
```

因此“几秒 ÷ 几分钟”统一转为秒，“几小时 ÷ 几分钟”统一转为分钟，两个操作数始终带相同单位。

**Step 3: 固定数值格式**

公式操作数使用 `BigDecimal` 除以单位毫秒数，保留最多 2 位小数并去掉无意义尾零。

间用比显示规则固定为：

- 无效值：`—`；
- 精确 0：`0.0×`；
- `0 < value < 0.01`：`<0.01×`；
- `0.01 <= value < 10`：最多 2 位小数，整数至少保留 1 位；
- `value >= 10`：最多 1 位小数，去除多余尾零。

历史平均、当前值、比较差值、图表轴和 TalkBack 摘要全部调用同一个 formatter。

**Step 4: 扩展 presentation 字段**

`UsageRequestRhythmPresentation` 增加：

```kotlin
val currentRatioText: String
val selectedWindowAverageText: String
val equationText: String?
val gapText: String
val durationText: String
```

`equationText` 直接由 `Formula` 生成：

```text
<gapValue> <unit> ÷ <durationValue> <unit> = <ratioText>
```

当前 gap 或申请时长无效时 `equationText = null`，UI 显示现有状态原因，不生成 `0` 或猜测值。

**Step 5: 运行计算与 presentation 测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageRequestRhythmPolicyTest' \
  --tests '*UsageRequestRhythmPresentationTest'
```

Expected: PASS；秒、分钟、小时三个公式和小正数显示全部通过。

## Task 6: 把距离信息移到表单顶部，把间用比反馈并入申请时长

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/UsageRequestRhythmPresentation.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt`

**Step 1: 写表单顺序契约测试**

在 `UsageGuardRequestLayoutContractTest` 读取 `UsageGuardRequestOverlayService.kt` 的 `UsageGuardRequestContent` 源段，严格断言以下调用顺序：

```text
SelfControlElapsedCard
Text("选择标签")
OutlinedTextField（申请理由）
Text("申请时长")
UsageDurationRatioFeedback
Button（开始使用）
TextButton（取消）
```

同时断言 `UsageRequestRhythmSummary` 不再位于时长区之外。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*UsageGuardRequestLayoutContractTest'
```

Expected: FAIL，因为当前 elapsed card 和 ratio summary 都在申请时长之后。

**Step 2: 调整表单顺序**

在标题、目标 app、strict/resumable 模式说明后立即渲染 `SelfControlElapsedCard`。该卡片结束后立即渲染“选择标签”。保持标签、理由和提交校验回调原样。

对使用申请调用 `SelfControlElapsedCard` 时：

- 保留 `supportsUsageRatio = true`，历史图表仍能切换“间隔/间用比”；
- 传入 `currentReference = null`，顶部历史图表不再重复显示本次间用比；
- 当前距离继续由 `ElapsedState.Running` 的 headline 秒级更新；
- 三个范围的历史平均和样本覆盖信息继续显示。

**Step 3: 把反馈组件重构为时长区内 tonal card**

把 `UsageRequestRhythmSummary` 重命名为 `UsageDurationRatioFeedback`，紧跟在时长 chips、自定义分钟输入和 `durationError` 之后。

组件固定布局：

```text
间用比
本次 <currentRatioText>        <selectedWindow>平均 <averageText>
未使用间隔 <gapText>          申请时长 <durationText>
同单位换算
<equationText>
<comparisonText>
<statusText>
```

视觉规则：

- 使用 `Surface` 的 `surfaceVariant`/tonal color，不增加硬编码色值；
- “本次”数值使用 `titleLarge`，其余使用 `bodyMedium/bodySmall`；
- 两列在窄屏和 200% 字体下通过 `FlowRow` 自动换行，不强制三列并排；
- 公式使用普通文字，不新增字体依赖；
- 不用红绿表示高低，比较只写“高/低/相同”的中性文本；
- 无可用当前值时显示 `—` 和原因；
- 不设置及格线，不阻止提交。

**Step 4: 保持一次性数据加载和秒级刷新边界**

保留 `rhythmHistory = remember(insightAnchorAt, samples)`，历史平均只在数据集变化时计算。秒级 ticker 只重算：

- 当前距离；
- 当前间用比；
- 同单位公式；
- 与所选窗口平均的差值文案。

范围切换和时长切换不得触发 Room 查询。

**Step 5: 运行布局、presentation 和提交契约测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRequestLayoutContractTest' \
  --tests '*UsageRequestRhythmPresentationTest' \
  --tests '*UsageGuardRequestIntervalContractTest'
```

Expected: PASS；布局顺序、公式、取消边界和 app 隔离不变。

**Step 6: 提交表单和公式重构**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/util/UsageRequestRhythmPolicy.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/component/UsageRequestRhythmPresentation.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/UsageRequestRhythmPolicyTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/ui/component/UsageRequestRhythmPresentationTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt
git commit -m "feat: refine usage request rhythm feedback"
```

## Task 7: 扩展统一复盘 policy，加入间用比、完整度、分布和中性比较

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/util/DigitalSelfDisciplineReviewPolicy.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/data/UsageGuardRecordDaoContractTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepositoryTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/util/DigitalSelfDisciplineReviewPolicyTest.kt`

**Step 1: 先写最小复盘 projection 的失败测试**

在 `UsageGuardRecordDaoContractTest` 断言新增 `UsageReviewRow` 只包含：

```text
id、appId、appName、tagNames、requestedDurationMinutes、requestedAt、endReason、requestGapMs
```

断言它不包含：

```text
reasonText、grantedAt、expiresAt、endedAt、lastUsageEndedAt
```

断言 DAO 存在 `queryReviewRowsByRequestedAtRange(startAt, endAt)`，SQL 使用
`requested_at >= :startAt AND requested_at < :endAt`，并只 SELECT 上述列。

在 `SelfControlIntervalRepositoryTest` 断言 `observeReviewSource()` 返回
`usageRows` 和 `interceptEvents`，不再把完整 `UsageGuardRecord` 传入复盘。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRecordDaoContractTest' \
  --tests '*SelfControlIntervalRepositoryTest'
```

Expected: FAIL，因为最小 `UsageReviewRow` 和 DAO 查询尚不存在。

**Step 2: 实现最小复盘 projection 和 repository 数据源**

在 `UsageGuardRecord.kt` 定义 `UsageReviewRow`，并在 DAO 增加精确列查询。
把 `SelfControlIntervalRepository.ReviewSource.usageRecords` 改为 `usageRows`，
`observeReviewSource()` 直接组合 `queryReviewRowsByRequestedAtRange()` 与现有拦截事件 Flow。

删除复盘链路对完整 `UsageGuardRecord` 的读取。首页、widget 和使用申请历史页继续使用
现有完整记录查询，不改变它们的行为。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRecordDaoContractTest' \
  --tests '*SelfControlIntervalRepositoryTest'
```

Expected: PASS；projection 不包含申请理由。

**Step 3: 先写统一 summary 的失败测试**

使用固定 `ZoneId`、固定自然日范围和合成 `UsageReviewRow`，覆盖：

1. 8 次申请、6 条有效 gap、6 条有效 ratio、2 条空 gap，断言完整度为 `8/6/6/2`。
2. `gap=120m,duration=30m` 和 `gap=60m,duration=60m`，断言平均间用比为 `2.5×`。
3. 申请总时长使用 `Long` 求和，不发生 `Int` 溢出。
4. 应用、标签、结束状态、集中时段按 count 降序、label 升序稳定排列，并计算 `count / eventCount` 的占比。
5. 最近 10 条申请包含时间、app、gap、申请时长、ratio、标签和结束状态，但不包含 `reasonText`。
6. 拦截 summary 的 ratio stats 为空，拦截子类型过滤继续生效。
7. 当前周期和上一周期都有平均值时直接给出中性精确差值，并同时保留两边样本数；不再用“双方至少 3 个样本”隐藏差值。
8. 上一周期无有效值时显示“上一周期暂无有效样本”，不显示 0。
9. 今日按单条记录生成趋势输入；7 天和 30 天按自然日生成 daily bucket；无样本日期保持缺失，不补 0。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*DigitalSelfDisciplineReviewPolicyTest'
```

Expected: FAIL，因为当前 summary 没有 ratio、完整度、占比和统一 usage details。

**Step 4: 定义统一领域模型**

在 `DigitalSelfDisciplineReviewPolicy` 增加：

```kotlin
enum class ReviewMetric {
    USAGE_RATIO,
    USAGE_GAP,
    INTERCEPT_INTERVAL,
}

data class RatioStats(
    val sampleCount: Int,
    val average: Double?,
    val median: Double?,
    val min: Double?,
    val max: Double?,
)

data class DataCoverage(
    val eventCount: Int,
    val validIntervalCount: Int,
    val validRatioCount: Int,
    val excludedIntervalCount: Int,
)

data class RankedShare(
    val key: String,
    val label: String,
    val count: Int,
    val share: Double,
)

data class UsageDetails(
    val totalRequestedMinutes: Long,
    val averageRequestedMinutes: Double?,
    val appBreakdown: List<RankedShare>,
    val tagBreakdown: List<RankedShare>,
    val endReasonBreakdown: List<RankedShare>,
    val busiestPeriod: RankedShare?,
)
```

扩展 `RecentIntervalItem`，加入：

```kotlin
val requestedDurationMinutes: Int?
val ratio: Double?
val tagNames: List<String>
val eventKind: Int?
val endReason: Int?
```

扩展 `DailyIntervalBucket`，加入每日事件数、有效 gap 数、gap 平均/中位、ratio 平均/中位。

`ReviewSummary` 固定包含：

```kotlin
val coverage: DataCoverage
val intervalStats: SelfControlIntervalPolicy.Stats
val ratioStats: RatioStats?
val usageDetails: UsageDetails?
val dailyBuckets: List<DailyIntervalBucket>
val recentIntervals: List<RecentIntervalItem>
val rankedTargets: List<RankedShare>
val comparison: PeriodComparison
```

删除重复的 `intervalsMs`、`chartDates` 和可由上述字段直接推导的 page-only 字段。

**Step 5: 固定 ratio 和完整度计算**

使用申请 summary 对范围内每条记录执行：

```kotlin
val gap = record.requestGapMs?.takeIf { it >= 0L }
val ratio = UsageRequestRhythmPolicy.ratio(gap, record.requestedDurationMinutes)
```

计数固定为：

```text
eventCount = 范围内申请记录数
validIntervalCount = gap 非空数
validRatioCount = ratio 非空数
excludedIntervalCount = eventCount - validIntervalCount
```

拦截 summary 使用范围和子类型过滤后的事件数作为 `eventCount`，使用 `intervalMs != null && intervalMs >= 0` 作为有效间隔数，`validRatioCount = 0`。

**Step 6: 固定分布和占比**

- 应用：每份申请记录计 1 次；
- 标签：一份记录的每个非空标签各计 1 次；标签占比分母为标签出现总次数；
- 结束状态：每份申请记录计 1 次；
- 集中时段：继续使用上午、午间、下午、晚间、夜间分段，只显示中性名称“集中时段”，删除“高风险”和行为劝导文案；
- 拦截目标：按 stable eventKey 分组，显示安全 `subjectLabel`；
- 所有排行按 count 降序、label 升序，页面展示前 5 项，其余合并成“其他”一项。

**Step 7: 固定周期比较**

`PeriodComparison` 保存：

```kotlin
val currentEventCount: Int
val previousEventCount: Int
val currentMetricValue: Double?
val previousMetricValue: Double?
val metricDelta: Double?
val currentSampleCount: Int
val previousSampleCount: Int
```

比较仅描述数值，不声明改善、恶化、健康或自律程度。gap/interval 使用毫秒差，ratio 使用倍数差。

**Step 8: 运行 policy 测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRecordDaoContractTest' \
  --tests '*SelfControlIntervalRepositoryTest' \
  --tests '*DigitalSelfDisciplineReviewPolicyTest'
```

Expected: PASS；自然日、DST、空值、0 gap、ratio、分布、上一周期全部覆盖。

**Step 9: 提交统一复盘模型**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/util/DigitalSelfDisciplineReviewPolicy.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/data/UsageGuardRecordDaoContractTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepositoryTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/DigitalSelfDisciplineReviewPolicyTest.kt
git commit -m "refactor: unify digital review metrics"
```

## Task 8: 重构复盘 presentation 和单指标趋势模型

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewPresentation.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineTrendChart.kt`
- Delete: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlReviewChart.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/DigitalSelfDisciplineReviewPresentationTest.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewAccessibilityTest.kt`

**Step 1: 先写 presentation 失败测试**

固定断言：

1. 使用申请默认 metric 为 `USAGE_RATIO`；切到 `USAGE_GAP` 后只改变趋势值，不改变 summary 数据。
2. 拦截记录固定 metric 为 `INTERCEPT_INTERVAL`，不显示 ratio selector。
3. 今日趋势按有效单条记录排序；记录数超过 24 时按 1 小时聚合。
4. 7 天和 30 天趋势按自然日聚合，分别最多 7/30 个点。
5. ratio 趋势使用 `×`，gap/interval 趋势使用秒/分/小时/天自适应轴单位。
6. 趋势摘要包含本期平均、上一周期、差值、总记录、有效样本、图形点和未纳入数。
7. 0 样本显示明确空态；1 个样本仍显示数值和文字明细，不生成上一周期 0。
8. 图表文字明细按时间顺序列出每个原始点或聚合点。
9. 页面展示文案和 semantics 不含申请理由、URL、pattern、selector 和 node text。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*DigitalSelfDisciplineReviewPresentationTest' \
  --tests '*DigitalSelfDisciplineReviewAccessibilityTest'
```

Expected: FAIL，因为当前 presentation 只支持 interval chart points。

**Step 2: 建立纯 presentation model**

`DigitalSelfDisciplineReviewPresentation` 固定输出：

```kotlin
data class MetricCard(...)
data class CoverageRow(...)
data class TrendPoint(...)
data class TrendPresentation(...)
data class RankedBar(...)
data class RecentRow(...)
data class PagePresentation(...)
```

所有中文格式化、比例格式、时间格式和空态在 presentation 层完成；Composable 不重新计算业务统计。

**Step 3: 实现单指标趋势图**

在 `DigitalSelfDisciplineTrendChart.kt` 使用现有 Vico 依赖创建一条时间序列：

- 一张图只包含一个 metric；
- 使用 line layer 和可见 point marker；
- 不使用双纵轴；
- 不启用入场动画；
- x 轴最多显示 6 个标签；
- y 轴明确显示 `×` 或时间单位；
- chart contentDescription 使用完整 `TrendPresentation.semanticSummary`；
- 图下提供“查看文字明细”按钮；
- 文字明细与图形点一一对应；
- 颜色只使用 Material theme token，线与背景对比至少 3:1；
- 趋势方向同时由差值文字表达，不只依赖颜色或斜率。

删除仅服务旧复盘结构的 `SelfControlReviewChart.kt`，Overlay 图表继续使用 `SelfControlWindowChart`。

**Step 4: 固定趋势 headline**

趋势卡顶部固定显示：

```text
本期平均 <value>
上一周期 <value/暂无> · 差值 <signed delta/—>
有效样本 M / 总记录 N · 图形点 P
```

ratio 使用 `UsageRequestRhythmPolicy` formatter；时间使用 `SelfControlIntervalPolicy` formatter。

**Step 5: 运行 presentation 和无障碍测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*DigitalSelfDisciplineReviewPresentationTest' \
  --tests '*DigitalSelfDisciplineReviewAccessibilityTest'
```

Expected: PASS。

## Task 9: 用单一数据流重构数字自律复盘页面

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewPage.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewPresentation.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/DigitalSelfDisciplineReviewPresentationTest.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewStateContractTest.kt`

**Step 1: 写 ViewModel 单流契约测试**

断言 `UsageGuardReviewVm`：

- 不再声明 `usageGuardSummaryFlow`；
- `reviewUiStateFlow` 是页面唯一统计 state；
- range/type/intercept filter/metric 全部进入 `ReviewSelection`；
- 选择“使用申请”时 metric 重置为 `USAGE_RATIO`；
- 选择“拦截记录”时 metric 固定为 `INTERCEPT_INTERVAL`；
- 日期或时区变化继续重建 current/previous 自然日范围；
- 当前周期与上一周期各读取一次统一 `ReviewSource`；
- error state 不泄露异常中的数据库内容，只显示固定用户文案。

运行：

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*UsageGuardReviewStateContractTest'
```

Expected: FAIL，因为当前页面仍有第二条 `usageGuardSummaryFlow`。

**Step 2: 删除重复申请 summary 流**

从 `UsageGuardReviewVm` 删除 `usageGuardSummaryFlow` 和页面的 `usageSummary` collection。从 `UsageGuardReviewPage` 删除：

- `UsageRequestSummaryCard`；
- `LegacyRankingList`；
- 固定追加的 `legacy_usage_summary` item；
- 页面对 `UsageGuardReviewPolicy` 的依赖。

Home 页面和 widget 继续使用 `UsageGuardReviewPolicy`，本任务不改变首页摘要。

**Step 3: 实现固定顶部控件**

Top App Bar 下按以下顺序渲染：

1. `PrimaryTabRow`：使用申请、拦截记录；
2. `SingleChoiceSegmentedButtonRow`：今日、近 7 天、近 30 天；
3. 拦截记录下的 `FilterChip` 行：全部、应用、选择器、网址。

所有控件使用明确 `stateDescription` 和 48dp 最小触控高度。切换控件时保留列表位置，不做自动滚动和装饰动画。

**Step 4: 实现概览卡**

使用申请概览显示四项：

```text
申请次数
申请总时长
平均未使用间隔
平均间用比
```

拦截概览显示四项：

```text
拦截次数
有效间隔
平均间隔
中位间隔
```

概览卡底部始终显示数据完整度。使用申请固定文案：

```text
总申请 N 条 · 有效间隔 M 条 · 有效间用比 R 条 · 未纳入 K 条
```

拦截固定文案：

```text
总拦截 N 条 · 已完成间隔 M 条 · 首次或未完成 K 条
```

**Step 5: 实现趋势卡**

使用申请显示“间用比/未使用间隔”单选分段控件，默认间用比。拦截不显示 metric 控件。趋势卡渲染 `DigitalSelfDisciplineTrendChart` 和可展开文字明细。

**Step 6: 实现分布卡**

创建通用 `ReviewRankedBarList`：

- 左侧显示名称；
- 右侧显示 `count` 和百分比；
- 下方使用 `LinearProgressIndicator` 显示 share；
- 文本同时表达所有数值，不只用进度条颜色；
- 前 5 项后合并“其他”；
- 空态显示“所选范围暂无分布数据”。

使用申请依次显示：应用分布、标签分布、结束状态、集中时段。拦截记录只显示高频拦截目标。

**Step 7: 实现最近明细卡**

使用申请行固定显示：

```text
MM-dd HH:mm · 应用名
间用比 X · 未使用间隔 Y · 申请 Z 分钟
标签：A、B · 结束状态
```

拦截行固定显示：

```text
MM-dd HH:mm · 安全来源名
间隔 X · 拦截类型
```

最多 10 条，时间倒序；不显示申请理由、实际 URL、pattern、selector 和屏幕文本。

**Step 8: 实现宽度与字体适配**

- compact width：指标使用 2×2 网格，卡片单列；
- width >= 600dp：四项指标单行，趋势与分布仍按阅读顺序纵向排列，内容最大宽度 840dp 并居中；
- 200% 字体下指标允许换行，不使用固定高度；
- 360dp 下不出现横向滚动；
- 横屏下使用同一主垂直滚动，不创建嵌套滚动区；
- 深色主题只使用现有 semantic colors；
- loading/error/empty 三态都保留页面顶部筛选控件。

**Step 9: 增加合成数据 Preview**

为申请有数据、申请空态、拦截有数据创建 `@Preview`，全部使用合成 app 名、标签和规则名。Preview 覆盖：

- 360dp；
- 600dp；
- fontScale 2.0；
- dark mode。

Preview 不连接数据库，不读取真实记录。

**Step 10: 运行页面契约和全部复盘测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardReviewStateContractTest' \
  --tests '*DigitalSelfDisciplineReviewPolicyTest' \
  --tests '*DigitalSelfDisciplineReviewPresentationTest' \
  --tests '*DigitalSelfDisciplineReviewAccessibilityTest'
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
```

Expected: PASS；两个 flavor 编译；页面不再出现重复申请补充卡。

**Step 11: 提交复盘 UI 重构**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewPage.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewPresentation.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineTrendChart.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlReviewChart.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/ui/DigitalSelfDisciplineReviewPresentationTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewAccessibilityTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewStateContractTest.kt
git commit -m "feat: redesign digital self discipline review"
```

## Task 10: 更新文档、测试矩阵和隐私边界说明

**Files:**

- Modify: `README.md`
- Modify: `README_DEV.md`
- Modify: `docs/testing/self-control-interval-insights-test-matrix.md`
- Modify: `docs/testing/release-smoke-checklist.md`
- Modify: `CHANGELOG.md`

**Step 1: 更新 README 用户行为**

在使用申请章节写明：

- “其他”固定排在标签末尾；
- 距离上次结束使用及历史节律位于表单顶部；
- 间用比反馈紧邻申请时长；
- 公式自动把秒、分钟、小时换为相同单位；
- 低样本量逐条显示，超限后聚合；
- 图表区分总记录、有效样本和图形点。

在复盘章节写明新的概览、单指标趋势、分布和最近明细结构。

**Step 2: 更新 README_DEV 数据与聚合合同**

在 “Interception attribution and rhythm data” 中加入：

- repository 保留窗口内全部轻量 projection 行；
- null gap 只参与总记录计数，不参与平均、ratio 或图形值；
- 有效样本不超过 24/28/30 时逐条绘制；
- 超限时使用 1h/6h/1d 聚合；
- `sourceIds` 用于准确标记当前事件；
- ratio 原始计算统一使用毫秒，显示公式使用公共单位；
- 复盘 current/previous 只通过一个统一 summary 流生成。

**Step 3: 扩展自动化测试矩阵**

新增行：

- “其他”末位排序；
- 12 条同小时样本逐条显示；
- 24/25、28/29、30/31 聚合边界；
- 总记录/有效样本/图形点分离；
- 秒÷分钟、小时÷分钟、小时÷小时；
- 小正 ratio 不显示为 0；
- 表单信息顺序；
- 复盘单流；
- ratio 趋势；
- 排行占比和 10 条明细；
- 360dp/200% 字体/深色 Preview 编译。

保留“真机/OEM 未执行，用户在公开 prerelease 后验证”的状态。

**Step 4: 更新 release smoke checklist**

把发布后用户检查写成明确项：

- 新建自定义标签后“其他”仍为最后一个 chip；
- 打开申请页首屏先看到距离卡，卡下直接是标签；
- 时长区内实时显示间用比和同单位公式；
- 12 次同小时申请显示 12 个点；
- 超限聚合显示总记录、有效样本、图形点和聚合粒度；
- 复盘申请/拦截 Tab 不串数据；
- 复盘 metric/range/filter、排行占比、明细和空态清晰；
- 旧 null 数据显示未纳入，不显示为 0。

所有复选框保持未勾选，供发布后用户执行。

**Step 5: 更新 `[Unreleased]`**

在 `CHANGELOG.md` 的 `[Unreleased]` 增加 Added/Changed/Fixed：

- Fixed：其他标签排序、无条件聚合导致图形点过少；
- Changed：距离卡回到顶部、ratio 与时长合并、统一单位公式；
- Changed：复盘改为概览/趋势/分布/明细单流结构；
- Fixed：总记录与有效样本差异不可见；
- Security/Privacy：无新增持久字段，不记录理由/URL/selector；
- Known limitations：旧版和结束事件缺失记录仍不参与可信 gap/ratio。

**Step 6: 文档检查并提交**

```bash
git diff --check
git add README.md README_DEV.md \
  docs/testing/self-control-interval-insights-test-matrix.md \
  docs/testing/release-smoke-checklist.md CHANGELOG.md
git commit -m "docs: document rhythm and review refinements"
```

Expected: `git diff --check` 无输出；文档没有未决执行项。

## Task 11: 完成功能分支验证、代码审查和功能 PR

**Files:**

- Verify: all task files
- Verify unchanged: `app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`
- Verify unchanged: `app/schemas/li.songe.gkd.sdp.db.AppDb/33.json`

**Step 1: 运行针对性测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardTagDaoOrderContractTest' \
  --tests '*SelfControlIntervalRepositoryTest' \
  --tests '*UsageGuardRequestIntervalContractTest' \
  --tests '*SelfControlInsightWindowPolicyTest' \
  --tests '*SelfControlIntervalPresentationTest' \
  --tests '*SelfControlInsightAccessibilityContractTest' \
  --tests '*UsageRequestRhythmPolicyTest' \
  --tests '*UsageRequestRhythmPresentationTest' \
  --tests '*UsageGuardRequestLayoutContractTest' \
  --tests '*DigitalSelfDisciplineReviewPolicyTest' \
  --tests '*DigitalSelfDisciplineReviewPresentationTest' \
  --tests '*DigitalSelfDisciplineReviewAccessibilityTest' \
  --tests '*UsageGuardReviewStateContractTest'
```

Expected: PASS。

**Step 2: 运行完整本地质量基线**

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash scripts/generate-security-dependency-report.sh
./gradlew \
  :selector:jvmTest \
  :app:testGkdDebugUnitTest \
  :app:lintGkdDebug \
  :app:lintPlayDebug
python3 scripts/verify-security-dependency-report.py \
  --report build/reports/security-dependencies.txt
./gradlew \
  :app:assembleGkdDebug \
  :app:assemblePlayDebug \
  :app:assembleGkdRelease
git diff --check
```

Expected: 全部通过。

**Step 3: 验证没有 schema、依赖和敏感数据变化**

```bash
git diff --exit-code origin/main -- \
  app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt \
  app/schemas gradle/libs.versions.toml
rg -n 'reasonText|actualUrl|redirectUrl|pattern|selector|nodeText' \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewPresentation.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineTrendChart.kt
rg -n 'LogUtils.*(reason|url|pattern|selector|node)' \
  app/src/main/kotlin/li/songe/gkd/sdp
```

Expected: 第一条无 diff；presentation/chart 不读取敏感字段；没有新增敏感日志。

**Step 4: 使用 requesting-code-review 技能审查完整差异**

审查范围固定为 `origin/main..HEAD`，逐项确认：

- 标签 DAO 和 policy 顺序一致；
- `其他` 末位不依赖 UI 临时排序；
- repository 保留 null 行但不把 null 计入数值；
- 低样本逐条、超限聚合边界准确；
- current event 使用 source ID 标记；
- ratio 计算不做整分钟截断；
- 表单 UI 顺序和 TalkBack 顺序一致；
- 复盘没有重复 Flow 和跨 Tab 数据；
- previous period 使用同 range 与 zone；
- 两 flavor、隐私和 Overlay 不变量未回归。

修复所有 P0/P1/P2 问题，重新执行 Step 1–3。

**Step 5: 推送功能分支并创建 PR**

```bash
git status --short --branch
git log --oneline origin/main..HEAD
git push -u origin codex/usage-rhythm-review-refinement
gh pr create \
  --base main \
  --head codex/usage-rhythm-review-refinement \
  --title "feat: refine usage rhythm and digital review" \
  --body-file /tmp/gkd-sdp-usage-rhythm-review-pr.md
```

PR body 固定包含：

- 六个已确认根因；
- 固定 UI 顺序和复盘信息架构；
- 无 Room/schema/依赖变化；
- 完整测试命令和结果；
- 隐私检查；
- 真机/OEM 未执行且不作为此次 prerelease 门禁；
- 发布后由用户下载验证。

**Step 6: 等待所有 required checks**

```bash
gh pr checks --watch --fail-fast
gh pr view --json state,mergeStateStatus,statusCheckRollup,url
```

Expected: `quality`、`build`、`dependency-review` 和适用 CodeQL 全部成功，merge state 可合并。

**Step 7: 合并功能 PR**

```bash
gh pr merge --merge --delete-branch
git fetch origin --prune
git merge-base --is-ancestor HEAD origin/main
```

Expected: 功能 PR merged；远端功能分支删除；合并提交进入 `origin/main`。

## Task 12: 准备并合并 `v2.0.0-beta.5` 版本 PR

**Files:**

- Modify: `gradle/version.properties`
- Modify: `CHANGELOG.md`

**Step 1: 创建固定 release 分支**

```bash
cd /Users/zeiy/Project/gkd-SDP
git fetch origin --prune --tags
git worktree add .worktrees/release-v2.0.0-beta.5 \
  -b codex/release-v2.0.0-beta.5 origin/main
cd .worktrees/release-v2.0.0-beta.5
```

**Step 2: 验证版本未占用**

```bash
test -z "$(git tag --list 'v2.0.0-beta.5')"
! gh release view v2.0.0-beta.5 >/dev/null 2>&1
grep -Fx 'versionName=2.0.0-beta.4' gradle/version.properties
grep -Fx 'versionCode=96' gradle/version.properties
```

Expected: tag/Release 不存在，当前版本为 beta.4/code 96。

**Step 3: 更新唯一版本源**

把 `gradle/version.properties` 固定更新为：

```text
versionName=2.0.0-beta.5
versionCode=97
upstreamBase=1.12.1
upstreamVersionCode=92
```

不修改 `app/build.gradle.kts` 或 workflow 中的版本值。

**Step 4: 冻结 changelog**

把 `[Unreleased]` 下本任务内容移动到：

```markdown
## [2.0.0-beta.5] - <合并当日 Asia/Shanghai 日期>
```

保留空的 `[Unreleased]`。更新底部链接：

```markdown
[Unreleased]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.5...HEAD
[2.0.0-beta.5]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.4...v2.0.0-beta.5
```

Known limitations 明确写：旧版/未知结束时间记录不进入可信 gap/ratio；真机/OEM 验收由用户在公开 prerelease 后执行。

**Step 5: 运行 release metadata 校验**

```bash
bash scripts/test-verify-release-metadata.sh
bash scripts/verify-release-metadata.sh --no-tag
git diff --check
```

Expected: 全部通过。

**Step 6: 提交并创建 release PR**

```bash
git add gradle/version.properties CHANGELOG.md
git commit -m "chore: prepare v2.0.0-beta.5"
git push -u origin codex/release-v2.0.0-beta.5
gh pr create \
  --base main \
  --head codex/release-v2.0.0-beta.5 \
  --title "chore: release v2.0.0-beta.5" \
  --body-file /tmp/gkd-sdp-beta5-release-pr.md
```

Release PR body 固定包含版本、versionCode、changelog、metadata 命令、功能 PR 链接、无 schema 变化、真机/OEM 后置验证说明。

**Step 7: 等待 checks 并合并 release PR**

```bash
gh pr checks --watch --fail-fast
gh pr view --json state,mergeStateStatus,statusCheckRollup,url
gh pr merge --merge --delete-branch
git fetch origin --prune --tags
```

Expected: required checks 全绿，release PR merged。

**Step 8: 等待 release 合并后的 main CI**

```bash
release_merge_sha="$(git rev-parse origin/main)"
gh run list --branch main --commit "$release_merge_sha" --limit 10
gh run watch "$(gh run list --branch main --commit "$release_merge_sha" --workflow CI --limit 1 --json databaseId --jq '.[0].databaseId')" --exit-status
```

Expected: release merge commit 对应的 main CI 成功。

## Task 13: 创建 tag、验证资产并发布 prerelease

**Files:**

- Verify: `gradle/version.properties`
- Verify: `CHANGELOG.md`
- Verify: `.github/workflows/release.yml`
- Output: GitHub Release `v2.0.0-beta.5`

**Step 1: 在 release merge commit 创建 annotated tag**

```bash
cd /Users/zeiy/Project/gkd-SDP/.worktrees/release-v2.0.0-beta.5
git fetch origin --prune --tags
git switch --detach origin/main
grep -Fx 'versionName=2.0.0-beta.5' gradle/version.properties
grep -Fx 'versionCode=97' gradle/version.properties
git tag -a v2.0.0-beta.5 -m 'GKD-SDP v2.0.0-beta.5'
```

**Step 2: 在推送前执行带 tag 的校验**

```bash
bash scripts/test-verify-release-metadata.sh
bash scripts/verify-release-metadata.sh --tag v2.0.0-beta.5
git cat-file -t refs/tags/v2.0.0-beta.5
git merge-base --is-ancestor v2.0.0-beta.5 origin/main
```

Expected: metadata 通过；tag 类型为 `tag`；tag commit 在 `origin/main` 上。

**Step 3: 推送唯一 tag**

```bash
git push origin v2.0.0-beta.5
```

Expected: tag push 触发 `.github/workflows/release.yml`。

**Step 4: 等待 Release workflow**

```bash
release_run_id="$(gh run list \
  --workflow Release \
  --branch v2.0.0-beta.5 \
  --limit 1 \
  --json databaseId \
  --jq '.[0].databaseId')"
gh run watch "$release_run_id" --exit-status
gh run view "$release_run_id" --json conclusion,url,headSha,event
```

Expected: workflow success，创建 Draft prerelease 和三个不可变资产。

**Step 5: 下载并验证 Draft 资产**

```bash
release_verify_dir="$(mktemp -d)"
gh release download v2.0.0-beta.5 --dir "$release_verify_dir"
cd "$release_verify_dir"
sha256sum --check SHA256SUMS.txt
python3 - <<'PY'
import json
from pathlib import Path

manifest = json.loads(Path('update.json').read_text(encoding='utf-8'))
assert manifest['versionName'] == '2.0.0-beta.5'
assert manifest['versionCode'] == 97
assert manifest['downloadUrl'].endswith('/gkd-sdp-v2.0.0-beta.5.apk')
print('update.json OK')
PY
gh attestation verify gkd-sdp-v2.0.0-beta.5.apk --repo wskeei/gkd-SDP
```

再使用 Android build-tools 验证：

```bash
apksigner verify --verbose --print-certs gkd-sdp-v2.0.0-beta.5.apk
apkanalyzer manifest application-id gkd-sdp-v2.0.0-beta.5.apk
apkanalyzer manifest version-name gkd-sdp-v2.0.0-beta.5.apk
apkanalyzer manifest version-code gkd-sdp-v2.0.0-beta.5.apk
```

Expected:

```text
application-id = li.songe.gkd.sdp
version-name = 2.0.0-beta.5
version-code = 97
```

证书 SHA-256 必须与 release Environment 记录一致。

**Step 6: 验证 Draft 元数据并公开 prerelease**

```bash
gh release view v2.0.0-beta.5 \
  --json isDraft,isPrerelease,name,tagName,targetCommitish,assets,url
gh release edit v2.0.0-beta.5 --draft=false --prerelease
gh release view v2.0.0-beta.5 \
  --json isDraft,isPrerelease,name,tagName,assets,url
```

Expected: `isDraft=false`、`isPrerelease=true`，资产严格为 APK、`update.json`、`SHA256SUMS.txt`，不覆盖历史 Release。

**Step 7: 发布完成后交接用户验证**

交接中提供：

- Release URL；
- APK 文件名和 SHA-256；
- 功能 PR、release PR 和 Actions URL；
- 自动化测试结果；
- “真机/OEM 未执行，按用户要求在公开 prerelease 后由用户下载验证”；
- `docs/testing/release-smoke-checklist.md` 中与本任务相关的未勾选项目。

不得在用户完成下载验证前声明真机行为已通过。

## 最终完成标准

以下全部成立后结束执行：

1. 新增自定义标签后“其他”仍在所有标签最后。
2. 使用申请表单顶部先显示距离上次结束使用及历史图表，下一节为标签。
3. 间用比反馈与申请时长位于同一视觉区，秒/分钟/小时公式使用统一单位。
4. 正的小 ratio 不显示成 0；所有比值继续从毫秒原始值计算。
5. 低于窗口点数上限时一条有效样本对应一个图形点；超限后才聚合。
6. 总记录、有效样本、未纳入记录和图形点分别显示，缺失值不伪装为 0。
7. 数字自律复盘只有一条 summary/UI state 流，申请与拦截 Tab 不串数据。
8. 复盘包含概览、单指标趋势、应用/标签/结束状态或拦截目标分布、最近 10 条明细。
9. 图表有等价文字明细和 semantics，360dp、200% 字体、深色和宽屏 Preview 可编译。
10. Room 保持 schema 33，现有记录、配置、锁定状态和历史不被迁移或清空。
11. 所有针对性测试、完整 CI、双 flavor 编译、Lint、安全依赖检查和 `git diff --check` 通过。
12. 功能 PR 与 release PR 合并，`v2.0.0-beta.5` annotated tag 发布，Release 资产、checksum、签名和 attestation 验证通过。
13. 真机/OEM 项保持未执行，用户从公开 prerelease 下载后自行验证。
