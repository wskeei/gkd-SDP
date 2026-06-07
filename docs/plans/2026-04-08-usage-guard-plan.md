# Usage Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `数字自律` feature that forces the user to justify opening controlled apps with tags, a written reason, and a requested duration, then records the session locally and hard-blocks the app again when time runs out.

**Architecture:** Build this as a new independent subsystem, not as another branch inside `AppBlockerEngine`. The subsystem has four layers: persistent config/history (`UsageGuard*` data models), a pure policy layer (`UsageGuardPolicy`), a runtime engine that listens to app foreground changes (`UsageGuardEngine`), and two overlays: a request overlay before use and a timeout overlay after the grant expires. The page under `数字自律` manages scope mode, selected apps, whitelist apps, global tags, minimum reason length, and record browsing.

**Tech Stack:** Kotlin, Room, Kotlin Flow, Jetpack Compose Material3, existing accessibility app-change pipeline, overlay services, JUnit4 unit tests.

---

## File Map

**Create:**
- `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardAppProfile.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardPolicy.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardTimeoutOverlayService.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardPage.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardVm.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardPolicyTest.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/ui/UsageGuardVmTest.kt`

**Modify:**
- `app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yFeat.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`

**Responsibilities:**
- `UsageGuardAppProfile.kt`: per-app targeting and grant-mode overrides.
- `UsageGuardTag.kt`: global tag library with preset and custom tags.
- `UsageGuardRecord.kt`: local request/grant/end history for later export.
- `UsageGuardPolicy.kt`: pure request validation and app-protection decision logic.
- `UsageGuardEngine.kt`: runtime foreground-app guard, request launch, session expiry, strict-mode reset.
- `UsageGuardRequestOverlayService.kt`: pre-use application form overlay.
- `UsageGuardTimeoutOverlayService.kt`: time-up overlay with reason recap and home button.
- `UsageGuardPage.kt` / `UsageGuardVm.kt`: feature page under `数字自律`.

---

### Task 1: Lock the core policy with failing tests first

**Files:**
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardPolicyTest.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardPolicy.kt`

- [ ] **Step 1: Write the failing test for selected-app scope**

```kotlin
package li.songe.gkd.sdp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardPolicyTest {
    @Test
    fun selectedScopeOnlyProtectsSelectedApps() {
        val selectedProfile = UsageGuardPolicy.AppProfileSnapshot(
            appId = "com.example.chat",
            selectedTarget = true,
            globalWhitelist = false,
            grantMode = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
        )

        assertTrue(
            UsageGuardPolicy.shouldProtectApp(
                enabled = true,
                scopeMode = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                appProfile = selectedProfile,
            )
        )
        assertFalse(
            UsageGuardPolicy.shouldProtectApp(
                enabled = true,
                scopeMode = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
                appProfile = null,
            )
        )
    }
}
```

- [ ] **Step 2: Write the failing test for global mode with whitelist**

```kotlin
@Test
fun globalScopeProtectsNonWhitelistedApps() {
    val whitelistedProfile = UsageGuardPolicy.AppProfileSnapshot(
        appId = "com.example.bank",
        selectedTarget = false,
        globalWhitelist = true,
        grantMode = UsageGuardPolicy.GRANT_MODE_STRICT,
    )

    assertFalse(
        UsageGuardPolicy.shouldProtectApp(
            enabled = true,
            scopeMode = UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
            appProfile = whitelistedProfile,
        )
    )
    assertTrue(
        UsageGuardPolicy.shouldProtectApp(
            enabled = true,
            scopeMode = UsageGuardPolicy.SCOPE_GLOBAL_EXCEPT_WHITELIST,
            appProfile = null,
        )
    )
}
```

- [ ] **Step 3: Write the failing test for request validation**

```kotlin
@Test
fun requestValidationRequiresTagReasonAndPositiveDuration() {
    val invalid = UsageGuardPolicy.validateRequest(
        selectedTags = emptyList(),
        reason = "太短",
        minReasonLength = 6,
        requestedDurationMinutes = 0,
    )

    assertFalse(invalid.accepted)

    val valid = UsageGuardPolicy.validateRequest(
        selectedTags = listOf("查资料"),
        reason = "查资料准备今晚的演讲",
        minReasonLength = 6,
        requestedDurationMinutes = 15,
    )

    assertTrue(valid.accepted)
}
```

- [ ] **Step 4: Run tests to verify RED**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardPolicyTest"
```

Expected: FAIL because `UsageGuardPolicy` does not exist yet.

- [ ] **Step 5: Implement the minimal pure policy**

```kotlin
package li.songe.gkd.sdp.util

object UsageGuardPolicy {
    const val SCOPE_SELECTED_ONLY = 0
    const val SCOPE_GLOBAL_EXCEPT_WHITELIST = 1

    const val GRANT_MODE_STRICT = 0
    const val GRANT_MODE_RESUMABLE = 1

    data class AppProfileSnapshot(
        val appId: String,
        val selectedTarget: Boolean,
        val globalWhitelist: Boolean,
        val grantMode: Int,
    )

    data class RequestValidationResult(
        val accepted: Boolean,
        val reasonError: String? = null,
        val durationError: String? = null,
        val tagsError: String? = null,
    )

    fun shouldProtectApp(
        enabled: Boolean,
        scopeMode: Int,
        appProfile: AppProfileSnapshot?,
    ): Boolean {
        if (!enabled) return false
        return when (scopeMode) {
            SCOPE_SELECTED_ONLY -> appProfile?.selectedTarget == true
            SCOPE_GLOBAL_EXCEPT_WHITELIST -> appProfile?.globalWhitelist != true
            else -> false
        }
    }

    fun validateRequest(
        selectedTags: List<String>,
        reason: String,
        minReasonLength: Int,
        requestedDurationMinutes: Int,
    ): RequestValidationResult {
        if (selectedTags.isEmpty()) {
            return RequestValidationResult(accepted = false, tagsError = "至少选择一个标签")
        }
        if (reason.trim().length < minReasonLength) {
            return RequestValidationResult(accepted = false, reasonError = "理由长度不足")
        }
        if (requestedDurationMinutes <= 0) {
            return RequestValidationResult(accepted = false, durationError = "时长必须大于 0")
        }
        return RequestValidationResult(accepted = true)
    }
}
```

- [ ] **Step 6: Run tests to verify GREEN**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardPolicyTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/util/UsageGuardPolicy.kt app/src/test/kotlin/li/songe/gkd/sdp/util/UsageGuardPolicyTest.kt
git commit -m "feat: add usage guard policy and tests"
```

---

### Task 2: Add persistence for app targeting, tag library, and usage history

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardAppProfile.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`

- [ ] **Step 1: Add the per-app profile entity**

```kotlin
package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.sdp.util.UsageGuardPolicy

@Entity(tableName = "usage_guard_app_profile")
data class UsageGuardAppProfile(
    @PrimaryKey
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "selected_target") val selectedTarget: Boolean = false,
    @ColumnInfo(name = "global_whitelist") val globalWhitelist: Boolean = false,
    @ColumnInfo(name = "grant_mode") val grantMode: Int = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
) {
    @Dao
    interface UsageGuardAppProfileDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(profile: UsageGuardAppProfile)

        @Query("SELECT * FROM usage_guard_app_profile ORDER BY updated_at DESC")
        fun queryAll(): Flow<List<UsageGuardAppProfile>>

        @Query("SELECT * FROM usage_guard_app_profile WHERE app_id = :appId LIMIT 1")
        suspend fun getByAppId(appId: String): UsageGuardAppProfile?

        @Query("DELETE FROM usage_guard_app_profile WHERE selected_target = 0 AND global_whitelist = 0")
        suspend fun deleteUnusedProfiles(): Int
    }
}
```

- [ ] **Step 2: Add the global tag entity**

```kotlin
package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "usage_guard_tag")
data class UsageGuardTag(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_preset") val isPreset: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    @Dao
    interface UsageGuardTagDao {
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insert(tag: UsageGuardTag): Long

        @Query("SELECT * FROM usage_guard_tag ORDER BY is_preset DESC, created_at ASC")
        fun queryAll(): Flow<List<UsageGuardTag>>

        @Query("DELETE FROM usage_guard_tag WHERE id = :id AND is_preset = 0")
        suspend fun deleteCustomTag(id: Long): Int

        @Query("SELECT COUNT(*) FROM usage_guard_tag")
        suspend fun count(): Int
    }
}
```

- [ ] **Step 3: Add the history entity with active-session semantics**

```kotlin
package li.songe.gkd.sdp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.sdp.util.UsageGuardPolicy

@Entity(tableName = "usage_guard_record")
data class UsageGuardRecord(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "tag_names") val tagNames: List<String>,
    @ColumnInfo(name = "reason_text") val reasonText: String,
    @ColumnInfo(name = "grant_mode") val grantMode: Int = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
    @ColumnInfo(name = "requested_duration_minutes") val requestedDurationMinutes: Int,
    @ColumnInfo(name = "requested_at") val requestedAt: Long,
    @ColumnInfo(name = "granted_at") val grantedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long = 0L,
    @ColumnInfo(name = "end_reason") val endReason: Int = END_REASON_ACTIVE,
) {
    companion object {
        const val END_REASON_ACTIVE = 0
        const val END_REASON_EXPIRED = 1
        const val END_REASON_LEFT_APP = 2
        const val END_REASON_REPLACED = 3
        const val END_REASON_HOME_BUTTON = 4
    }

    @Dao
    interface UsageGuardRecordDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(record: UsageGuardRecord): Long

        @Query("SELECT * FROM usage_guard_record WHERE app_id = :appId AND ended_at = 0 ORDER BY id DESC LIMIT 1")
        suspend fun getActiveRecord(appId: String): UsageGuardRecord?

        @Query("SELECT * FROM usage_guard_record ORDER BY id DESC LIMIT :limit")
        fun queryLatest(limit: Int = 100): Flow<List<UsageGuardRecord>>

        @Query("UPDATE usage_guard_record SET ended_at = :endedAt, end_reason = :endReason WHERE id = :id")
        suspend fun closeRecord(id: Long, endedAt: Long, endReason: Int): Int
    }
}
```

- [ ] **Step 4: Wire the entities into Room and settings**

```kotlin
// AppDb.kt
@Database(
    version = 30,
    entities = [
        // existing entities...
        UsageGuardAppProfile::class,
        UsageGuardTag::class,
        UsageGuardRecord::class,
    ],
    autoMigrations = [
        // existing migrations...
        AutoMigration(from = 29, to = 30),
    ]
)
abstract class AppDb : RoomDatabase() {
    abstract fun usageGuardAppProfileDao(): UsageGuardAppProfile.UsageGuardAppProfileDao
    abstract fun usageGuardTagDao(): UsageGuardTag.UsageGuardTagDao
    abstract fun usageGuardRecordDao(): UsageGuardRecord.UsageGuardRecordDao
}
```

```kotlin
// SettingsStore.kt
val usageGuardEnabled: Boolean = false,
val usageGuardScopeMode: Int = UsageGuardPolicy.SCOPE_SELECTED_ONLY,
val usageGuardDefaultGrantMode: Int = UsageGuardPolicy.GRANT_MODE_RESUMABLE,
val usageGuardMinReasonLength: Int = 8,
```

- [ ] **Step 5: Seed preset tags on first launch of the feature**

Preset set:
```kotlin
listOf("联系工作", "回复消息", "查资料", "支付", "其他")
```

Put the seeding helper in `UsageGuardVm` or `UsageGuardEngine`, not in `App.onCreate()`.

- [ ] **Step 6: Run a compile-focused verification**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardPolicyTest"
```

Expected: PASS and no Room symbol errors in compile output.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardAppProfile.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt
git commit -m "feat: add usage guard persistence models"
```

---

### Task 3: Build the runtime guard engine and session transitions

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yFeat.kt`

- [ ] **Step 1: Write the failing ViewModel-facing test for strict versus resumable session semantics**

```kotlin
package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.util.UsageGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageGuardVmTest {
    @Test
    fun activeRecordCloseReasonsStayStable() {
        assertEquals(UsageGuardPolicy.GRANT_MODE_STRICT, 0)
        assertEquals(UsageGuardRecord.END_REASON_EXPIRED, 1)
        assertEquals(UsageGuardRecord.END_REASON_LEFT_APP, 2)
        assertEquals(UsageGuardRecord.END_REASON_HOME_BUTTON, 4)
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardVmTest"
```

Expected: FAIL because `UsageGuardRecord` and/or the constants do not yet fully match the test.

- [ ] **Step 3: Implement the engine**

```kotlin
package li.songe.gkd.sdp.a11y

import android.content.Intent
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.UsageGuardRequestOverlayService
import li.songe.gkd.sdp.service.UsageGuardTimeoutOverlayService
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.UsageGuardPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object UsageGuardEngine {
    private var lastProtectedAppId: String? = null

    val appProfilesFlow = DbSet.usageGuardAppProfileDao().queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    val tagsFlow = DbSet.usageGuardTagDao().queryAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    fun onAppChanged(packageName: String, service: A11yService) {
        appScope.launch(Dispatchers.IO) {
            closeStrictSessionIfNeeded(packageName)

            val settings = storeFlow.value
            val profile = DbSet.usageGuardAppProfileDao().getByAppId(packageName)
            val shouldProtect = UsageGuardPolicy.shouldProtectApp(
                enabled = settings.usageGuardEnabled,
                scopeMode = settings.usageGuardScopeMode,
                appProfile = profile?.let {
                    UsageGuardPolicy.AppProfileSnapshot(
                        appId = it.appId,
                        selectedTarget = it.selectedTarget,
                        globalWhitelist = it.globalWhitelist,
                        grantMode = it.grantMode,
                    )
                },
            )

            if (!shouldProtect) {
                lastProtectedAppId = null
                return@launch
            }

            val activeRecord = DbSet.usageGuardRecordDao().getActiveRecord(packageName)
            val now = System.currentTimeMillis()
            if (activeRecord == null) {
                showRequestOverlay(service, packageName)
                lastProtectedAppId = packageName
                return@launch
            }

            if (activeRecord.expiresAt <= now) {
                DbSet.usageGuardRecordDao().closeRecord(
                    id = activeRecord.id,
                    endedAt = now,
                    endReason = UsageGuardRecord.END_REASON_EXPIRED,
                )
                showTimeoutOverlay(service, packageName, activeRecord.reasonText)
                lastProtectedAppId = packageName
                return@launch
            }

            lastProtectedAppId = packageName
        }
    }

    private suspend fun closeStrictSessionIfNeeded(nextAppId: String) {
        val previousAppId = lastProtectedAppId ?: return
        if (previousAppId == nextAppId) return
        val active = DbSet.usageGuardRecordDao().getActiveRecord(previousAppId) ?: return
        if (active.grantMode != UsageGuardPolicy.GRANT_MODE_STRICT) return
        DbSet.usageGuardRecordDao().closeRecord(
            id = active.id,
            endedAt = System.currentTimeMillis(),
            endReason = UsageGuardRecord.END_REASON_LEFT_APP,
        )
    }

    private fun showRequestOverlay(service: A11yService, appId: String) {
        service.startService(Intent(service, UsageGuardRequestOverlayService::class.java).apply {
            putExtra("appId", appId)
        })
    }

    private fun showTimeoutOverlay(service: A11yService, appId: String, reasonText: String) {
        service.startService(Intent(service, UsageGuardTimeoutOverlayService::class.java).apply {
            putExtra("appId", appId)
            putExtra("reasonText", reasonText)
        })
    }
}
```

- [ ] **Step 4: Hook the engine into the accessibility app-change path**

Add inside `A11yFeat.kt` next to the existing app-change handlers:

```kotlin
private fun A11yService.useUsageGuard() {
    onAppChanged {
        UsageGuardEngine.onAppChanged(currentAppId, this@useUsageGuard)
    }
}
```

And register it together with the existing feature hooks.

- [ ] **Step 5: Run tests to verify GREEN**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuard*Test"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yFeat.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/UsageGuardVmTest.kt
git commit -m "feat: add usage guard runtime engine"
```

---

### Task 4: Build the request overlay and timeout overlay

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardTimeoutOverlayService.kt`

- [ ] **Step 1: Create the request overlay service with the full application form**

Minimum request screen content:

```kotlin
@Composable
private fun UsageGuardRequestScreen(
    appName: String,
    tags: List<String>,
    selectedTags: Set<String>,
    reasonText: String,
    minReasonLength: Int,
    durationOptions: List<Int>,
    customMinutesText: String,
    onToggleTag: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onPickDuration: (Int) -> Unit,
    onCustomMinutesChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    // show tags, allow creating custom tag, reason text field, preset chips, custom minutes field, start button
}
```

On submit, the service must:
```kotlin
val now = System.currentTimeMillis()
DbSet.usageGuardRecordDao().insert(
    UsageGuardRecord(
        appId = appId,
        appName = appName,
        tagNames = selectedTags.toList(),
        reasonText = reasonText.trim(),
        grantMode = resolvedGrantMode,
        requestedDurationMinutes = requestedDurationMinutes,
        requestedAt = now,
        grantedAt = now,
        expiresAt = now + requestedDurationMinutes * 60_000L,
    )
)
stopSelf()
```

- [ ] **Step 2: Create the timeout overlay service**

The timeout screen must show:

```kotlin
@Composable
private fun UsageGuardTimeoutScreen(
    reasonText: String,
    onGoHome: () -> Unit,
) {
    Column {
        Text("时间已到", style = MaterialTheme.typography.displaySmall)
        Text(reasonText, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onGoHome) { Text("回到桌面") }
    }
}
```

On `回到桌面`:

```kotlin
A11yService.instance?.performGlobalAction(
    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
)
stopSelf()
```

- [ ] **Step 3: Keep overlays isolated from existing `AppBlockerOverlayService`**

Do not overload `AppBlockerOverlayService` with feature flags. The timeout UI needs different copy, different behavior, and no countdown auto-exit.

- [ ] **Step 4: Run focused compile verification**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuard*Test"
```

Expected: PASS and no unresolved service/Compose symbols.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardTimeoutOverlayService.kt
git commit -m "feat: add usage guard overlays"
```

---

### Task 5: Add the `数字自律` page, app lists, tags, and local history UI

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardVm.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardPage.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`

- [ ] **Step 1: Add the ViewModel for page state**

Core flows:

```kotlin
class UsageGuardVm : BaseViewModel() {
    val appProfilesFlow = DbSet.usageGuardAppProfileDao().queryAll()
    val tagsFlow = DbSet.usageGuardTagDao().queryAll()
    val historyFlow = DbSet.usageGuardRecordDao().queryLatest(50)

    fun updateEnabled(enabled: Boolean) { /* storeFlow.update */ }
    fun updateScopeMode(scopeMode: Int) { /* storeFlow.update */ }
    fun updateDefaultGrantMode(grantMode: Int) { /* storeFlow.update */ }
    fun updateMinReasonLength(minLength: Int) { /* storeFlow.update */ }
    fun saveSelectedTargets(appIds: List<String>) { /* selectedTarget flags */ }
    fun saveWhitelist(appIds: List<String>) { /* globalWhitelist flags */ }
    fun saveAppGrantMode(appId: String, grantMode: Int) { /* upsert profile */ }
    fun addCustomTag(name: String) { /* insert if normalized unique */ }
    fun deleteCustomTag(tag: UsageGuardTag) { /* delete only custom */ }
}
```

- [ ] **Step 2: Add the page**

The page must contain these sections in this order:

```kotlin
1. 功能总开关
2. 生效范围：仅选中应用 / 全局生效（白名单跳过）
3. 默认授权模式：严格 / 普通
4. 最少理由字数
5. 选中应用列表 or 白名单列表（根据 scopeMode 切换）
6. 全局标签库管理
7. 最近申请记录
```

Use `AppPickerDialog` for both selected-target and whitelist picking.

- [ ] **Step 3: Add the new card to `FocusLockPage`**

Insert a new card near `应用拦截`:

```kotlin
item(key = "usage_guard") {
    UsageGuardCard(
        enabled = settings.usageGuardEnabled,
        scopeMode = settings.usageGuardScopeMode,
        onClick = { mainVm.navigatePage(UsageGuardPageDestination) }
    )
    Spacer(modifier = Modifier.height(12.dp))
}
```

The card text should read like:

```kotlin
title = "使用申请"
subtitle = if (enabled) "打开受控应用前先说明原因并申请时长" else "未启用"
```

- [ ] **Step 4: Seed preset tags when page opens and the tag table is empty**

Seed set:

```kotlin
listOf("联系工作", "回复消息", "查资料", "支付", "其他")
```

Do it once in `UsageGuardVm.init {}` with a `count()` guard.

- [ ] **Step 5: Run focused tests**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuardVmTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardVm.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardPage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt
git commit -m "feat: add usage guard settings page"
```

---

### Task 6: Harden runtime behavior around repeat entry, strict mode, and time-up reentry

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardTimeoutOverlayService.kt`

- [ ] **Step 1: Prevent duplicate request overlays for the same foreground app**

Add engine state:

```kotlin
private var requestOverlayAppId: String? = null
private var timeoutOverlayAppId: String? = null
```

Guard behavior:

```kotlin
if (requestOverlayAppId == packageName) return
if (timeoutOverlayAppId == packageName) return
```

Clear the fields when each service stops.

- [ ] **Step 2: Ensure strict mode re-prompts after leaving the app**

Keep this exact close-on-background rule:

```kotlin
if (active.grantMode == UsageGuardPolicy.GRANT_MODE_STRICT && previousAppId != nextAppId) {
    closeRecord(... END_REASON_LEFT_APP)
}
```

- [ ] **Step 3: Ensure expired records always lead to the timeout overlay until the user re-applies**

Do not silently auto-close the timeout overlay.
Do not convert an expired record into a resumable one.
Do not auto-open the request form from the timeout overlay itself.

- [ ] **Step 4: Preserve the reason text in the timeout overlay**

Pass:

```kotlin
putExtra("reasonText", activeRecord.reasonText)
```

And render that exact text as the small copy under `时间已到`.

- [ ] **Step 5: Run broad feature tests**

Run:
```powershell
.\gradlew :app:testGkdDebugUnitTest --tests "*UsageGuard*"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardTimeoutOverlayService.kt
git commit -m "fix: harden usage guard session transitions"
```

---

### Task 7: Full verification and manual acceptance

**Files:**
- No new files

- [ ] **Step 1: Run all focused tests**

```powershell
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuardPolicyTest"
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuardVmTest"
.\gradlew :app:testGkdDebugUnitTest --rerun-tasks --tests "*UsageGuard*"
```

Expected: all commands `BUILD SUCCESSFUL`.

- [ ] **Step 2: Manual acceptance for selected-app scope**

Check:
1. Enable feature.
2. Set scope to `仅选中应用`.
3. Pick one target app.
4. Open it and confirm request overlay appears.
5. Select/add a tag, enter a reason that satisfies the min-length rule, choose 10 minutes, start.
6. Confirm the app is usable.
7. Wait for expiry or shorten duration for testing.
8. Confirm `时间已到` overlay appears with the same reason text and a `回到桌面` button.

- [ ] **Step 3: Manual acceptance for global scope**

Check:
1. Switch scope to `全局生效（白名单跳过）`.
2. Put one app into whitelist.
3. Confirm a non-whitelisted app requires a request.
4. Confirm the whitelisted app opens directly.

- [ ] **Step 4: Manual acceptance for strict versus resumable**

Check:
1. Set one app to `严格模式`.
2. Approve a request, leave the app, re-enter.
3. Confirm it asks again immediately.
4. Set another app to `普通模式`.
5. Approve a request, leave and re-enter before expiry.
6. Confirm it does not ask again.

- [ ] **Step 5: Manual acceptance for records**

Check:
1. Open the `使用申请` page.
2. Confirm recent records show app, tags, reason, duration, and end state.
3. Confirm custom tags remain in the global tag library after reuse.

- [ ] **Step 6: Report exact verification results**

Do not claim completion without quoting the exact Gradle command results and explicitly stating any manual checks that were not run.

