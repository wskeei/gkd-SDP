# 上游Cherry-Pick计划

## 关键修复列表（按优先级排序）

### 高优先级 - 安全性和兼容性修复
1. `50f121e8` - fix: compat takeScreenshot for android-16.0.0_r4
2. `370807bb` - fix: ignore a11y event from other display (#1350)
3. `b4009b85` - fix: rm WeakHashMap in LifecycleCallbacks
4. `84bfecff` - fix: can not receive ACTION_PACKAGE_ADDED

### 中优先级 - 功能修复
5. `28f7d340` - fix: check a11y running state
6. `809586ee` - fix: TriStateSwitch a11y not working
7. `6811211d` - fix: a11y update activity not work
8. `b8a1d271` - fix: rule filter not work in AppConfigPage (#1340)
9. `959f751c` - fix: globalGroup show in AppConfigPage
10. `fa606991` - fix: actualCheckedGroupSet not work
11. `4cd02095` - fix: auto check app update
12. `22fc6812` - fix: preFillExpVars screenHeight/screenWidth (#1334)
13. `fbc16529` - fix: Swipe ActionResult (#1334)
14. `7b841b83` - fix: getExternalFilesDir null (#1333)
15. `3da6fcd8` - fix: set maximumObscuringOpacityForTouch for TrackService (#1325)
16. `50a6baa9` - fix: rm appRect
17. `913d0de8` - fix: windowInsets may be null (#1315)
18. `d532a948` - fix: performActionBack (#1310)
19. `b10fe9aa` - fix: remove subs sheet in ActionLogPage (#1245)
20. `877714d1` - fix: grantSelf failed
21. `ab2014da` - fix: save a11yScopeAppListFlow
22. `8b0a148c` - fix: skip onTaskStackChanged
23. `a5c2796c` - fix: killRelaunchApp when uiAutomation connected

### 性能优化（可选）
24. `27382b7d` - perf: upgrade to android 17
25. `86034d72` - perf: use remap plugin access android hidden api
26. `fa7eaac2` - perf: TrackService FloatLayer
27. `a0976f3b` - perf: rm empty log folder
28. `cb47bb62` - perf: change automation desc

## Cherry-pick策略

由于项目包名从 `li.songe.gkd` 改为 `li.songe.gkd.sdp`，需要：
1. 尝试cherry-pick每个提交
2. 如果有路径冲突，手动调整路径
3. 测试确保功能正常

## 注意事项
- 所有涉及包名路径的提交都需要手动调整
- 优先应用不涉及UI的核心逻辑修复
- 每次cherry-pick后都要检查编译是否通过
