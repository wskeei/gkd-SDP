package li.songe.gkd.sdp.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.receiver.AdminReceiver
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.toast

class AntiUninstallVm : BaseViewModel() {
    private val adminComponent = ComponentName(app, AdminReceiver::class.java)
    private val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val _isAdminActive = MutableStateFlow(false)
    val isAdminActive = _isAdminActive.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isLockedFlow = FocusLockUtils.allConstraintsFlow.map { constraints ->
        constraints.any {
            it.targetType == ConstraintConfig.TYPE_ANTI_UNINSTALL &&
            it.isLocked
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lockEndTimeFlow = FocusLockUtils.allConstraintsFlow.map { constraints ->
        constraints
            .filter { it.targetType == ConstraintConfig.TYPE_ANTI_UNINSTALL }
            .maxOfOrNull { it.lockEndTime } ?: 0L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    var selectedLockDuration by mutableIntStateOf(480) // Minutes
    var isCustomLockDuration by mutableStateOf(false)
    var customLockDaysText by mutableStateOf("")
    var customLockHoursText by mutableStateOf("")

    fun checkAdminStatus() {
        _isAdminActive.value = dpm.isAdminActive(adminComponent)
    }

    fun requestEnableAdmin(context: Context) {
        if (_isAdminActive.value) {
            // Already active, maybe user wants to disable?
            // But we redirect to settings anyway.
            // If locked, we block this action in UI.
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "开启后可防止应用被误卸载")
        }
        context.startActivity(intent)
    }

    fun requestDisableAdmin(context: Context) {
        // We cannot directly disable, we must guide user to settings
        // But to be user friendly, we can just open the settings page
        // However, there is no direct intent to remove specific admin.
        // We can open the list.
        val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
        context.startActivity(intent)
        toast("请在列表中找到本应用并取消激活")
    }

    fun lock() = viewModelScope.launch(Dispatchers.IO) {
        val durationMinutes = if (isCustomLockDuration) {
            val days = customLockDaysText.toIntOrNull() ?: 0
            val hours = customLockHoursText.toIntOrNull() ?: 0
            days * 24 * 60 + hours * 60
        } else {
            selectedLockDuration
        }

        if (durationMinutes <= 0) {
            toast("请输入有效的锁定时长")
            return@launch
        }

        val now = System.currentTimeMillis()
        val currentEndTime = lockEndTimeFlow.value
        val newEndTime = if (currentEndTime > now) {
            currentEndTime + durationMinutes * 60 * 1000L
        } else {
            now + durationMinutes * 60 * 1000L
        }

        // Remove old config if exists (we only need one)
        // Or just insert new one. FocusLockUtils checks all.
        // Better to cleanup old ones to keep DB clean, but insert is safer.
        val config = ConstraintConfig(
            targetType = ConstraintConfig.TYPE_ANTI_UNINSTALL,
            subsId = 0, // Not used
            lockEndTime = newEndTime
        )
        DbSet.constraintConfigDao.insert(config)
        toast("防卸载保护已锁定")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AntiUninstallPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AntiUninstallVm>()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isAdminActive by vm.isAdminActive.collectAsState()
    val isLocked by vm.isLockedFlow.collectAsState()
    val lockEndTime by vm.lockEndTimeFlow.collectAsState()

    var showLockSheet by remember { mutableStateOf(false) }

    // Resume 时刷新状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.checkAdminStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popBackStack() },
                    )
                },
                title = { Text(text = "防卸载保护") }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(padding)) {
            item {
                ElevatedCard(
                    colors = surfaceCardColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "设备管理器权限",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isAdminActive) "已激活" else "未激活",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAdminActive,
                                onCheckedChange = {
                                    if (it) {
                                        vm.requestEnableAdmin(context)
                                    } else {
                                        if (isLocked) {
                                            toast("保护已锁定，无法取消激活")
                                        } else {
                                            vm.requestDisableAdmin(context)
                                        }
                                    }
                                },
                                enabled = !isLocked // 锁定时禁止操作 Switch (虽然 toggle 逻辑也挡住了，但视觉上也应该禁止)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "激活后，系统将禁止直接卸载本应用。配合锁定功能，可防止手动取消权限。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                ElevatedCard(
                    colors = surfaceCardColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .itemPadding()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(PerfIcon.Lock, contentDescription = null, tint = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLocked) "已锁定" else "锁定保护",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isLocked) {
                                    val remainingMinutes = ((lockEndTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                                    Text(
                                        text = "剩余 ${if (remainingMinutes >= 60) "${remainingMinutes / 60}小时${remainingMinutes % 60}分钟" else "${remainingMinutes}分钟"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        text = "锁定期间无法取消设备管理器权限",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = { showLockSheet = true },
                                enabled = isAdminActive // 只有激活了才能锁定
                            ) {
                                Text(if (isLocked) "延长" else "锁定")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLockSheet) {
        // Reuse UrlLockSheet logic but simplified or duplicating code to avoid coupling?
        // Let's reuse UrlLockSheet structure but passing VM params is tricky if types differ.
        // Actually we can just copy-paste the sheet UI content here or refactor into a generic LockSheet.
        // For speed and safety, I'll inline a simple sheet here.
        
        ModalBottomSheet(
            onDismissRequest = { showLockSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            UrlLockSheet( // Reuse UrlLockSheet from UrlBlockerComponents.kt? No, it expects UrlBlockVm
                // Let's create a local one or use a generic one if available.
                // Creating local one to match AntiUninstallVm
                title = if (isLocked) "延长锁定" else "锁定防卸载",
                description = "锁定后将强制守护设备管理器权限，无法卸载。",
                currentLockEndTime = if (isLocked) lockEndTime else null,
                vm = vm, // AntiUninstallVm needs to match interface? No, UrlLockSheet takes UrlBlockVm.
                // So I cannot reuse UrlLockSheet directly.
                // I will duplicate the sheet UI here using AntiUninstallVm.
                onDismiss = { showLockSheet = false },
                onLock = {
                    vm.lock()
                    showLockSheet = false
                }
            )
        }
    }
}

// Temporary duplication of LockSheet for AntiUninstallVm
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlLockSheet(
    title: String,
    description: String,
    currentLockEndTime: Long?,
    vm: AntiUninstallVm,
    onDismiss: () -> Unit,
    onLock: () -> Unit
) {
    val durationOptions = listOf(
        480 to "8小时",
        1440 to "1天",
        4320 to "3天"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (currentLockEndTime != null && currentLockEndTime > System.currentTimeMillis()) {
            val remaining = currentLockEndTime - System.currentTimeMillis()
            val remainingMinutes = (remaining / 60000).coerceAtLeast(0)
            Text(
                text = "当前剩余: ${if (remainingMinutes >= 60) "${remainingMinutes / 60}小时${remainingMinutes % 60}分钟" else "${remainingMinutes}分钟"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Text(
            text = "选择时长",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            durationOptions.forEach { (minutes, label) ->
                val isSelected = !vm.isCustomLockDuration && vm.selectedLockDuration == minutes
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable {
                            vm.isCustomLockDuration = false
                            vm.selectedLockDuration = minutes
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { vm.isCustomLockDuration = !vm.isCustomLockDuration }
        ) {
            Switch(
                checked = vm.isCustomLockDuration,
                onCheckedChange = { vm.isCustomLockDuration = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("自定义时长", style = MaterialTheme.typography.bodyMedium)
        }

        if (vm.isCustomLockDuration) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = vm.customLockDaysText,
                    onValueChange = { vm.customLockDaysText = it },
                    label = { Text("天") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    // Note: KeyboardOptions requires import, assuming imports are present or will be added
                )
                androidx.compose.material3.OutlinedTextField(
                    value = vm.customLockHoursText,
                    onValueChange = { vm.customLockHoursText = it },
                    label = { Text("小时") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLock,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确认锁定")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
