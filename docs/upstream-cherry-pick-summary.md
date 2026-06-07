# 上游Cherry-Pick总结

## 已成功合并的提交

1. **18e22225** - `perf: rm empty log folder`
   - 修复：过滤掉空的日志文件夹，优化日志打包
   - 文件：`app/src/main/kotlin/li/songe/gkd/sdp/util/FolderExt.kt`

2. **b07f6b42** - `fix: getExternalFilesDir null (#1333)`  
   - 修复：处理getExternalFilesDir()可能返回null的情况
   - 影响：防止在某些设备上崩溃
   - 文件：`app/src/main/kotlin/li/songe/gkd/sdp/util/FolderExt.kt`

3. **9da8d800** - `fix: auto check app update`
   - 修复：自动检查应用更新功能
   - 文件：`app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt`, `app/src/main/kotlin/li/songe/gkd/sdp/util/Upgrade.kt`

4. **aa18760f** - `perf: build.gradle.kts`
   - 优化：清理构建配置
   - 文件：`build.gradle.kts`

5. **bcb263ae** - `perf: update libs`
   - 更新：依赖库版本更新
   - Kotlin: 2.2.21 → 2.3.20
   - AGP: 8.13.2 → 9.1.0
   - Compose: 1.10.0 → 1.10.6
   - Ktor: 3.3.3 → 3.4.2
   - Coil: 3.3.0 → 3.4.0
   - Gradle: 9.2.1 → 9.4.1
   - 等等

## 无法合并的提交（由于冲突）

### 高冲突提交
- **50f121e8** - `fix: compat takeScreenshot for android-16.0.0_r4`
  - 原因：涉及Shizuku相关文件，项目结构差异较大

- **b4009b85** - `fix: rm WeakHashMap in LifecycleCallbacks`
  - 原因：多文件冲突，需要大量手动调整
  - 重要性：高（内存泄漏修复）
  - 建议：后续手动应用

- **877714d1** - `fix: grantSelf failed`
  - 原因：Shizuku API修改冲突

- **8fa475fc** - `perf: update libs`（更早的版本）
  - 原因：依赖命名规范变更（下划线→连字符），影响范围太大

### 中等冲突提交
- **cb47bb62** - `perf: change automation desc`
- **8b0a148c** - `fix: skip onTaskStackChanged`
- **ab2014da** - `perf: synchronized lastFront`
- 以及其他涉及UI和服务层的修复

## 统计

- 上游新增提交：103个
- 已成功合并：5个
- 尝试但失败：约10个
- 未尝试：约88个

## 建议

1. **重要但冲突的修复**：建议手动查看以下提交并手动应用修复逻辑
   - `b4009b85` - WeakHashMap内存泄漏修复
   - `370807bb` - 忽略其他显示器的无障碍事件
   - `28f7d340` - 检查无障碍服务运行状态
   - `84bfecff` - 修复无法接收ACTION_PACKAGE_ADDED

2. **依赖更新**：已更新到较新版本，但未应用最新的依赖命名规范变更（需要大量代码修改）

3. **包名差异**：由于项目包名从 `li.songe.gkd` 改为 `li.songe.gkd.sdp`，大部分涉及代码的提交都需要手动调整路径

4. **后续策略**：
   - 定期关注上游的重大bug修复
   - 对于关键修复，查看上游的diff并手动应用到对应的SDP路径
   - 考虑为核心逻辑修复创建适配脚本

## 验证建议

构建项目以确保合并的更改不会破坏现有功能：
```bash
./gradlew assembleDebug
```
