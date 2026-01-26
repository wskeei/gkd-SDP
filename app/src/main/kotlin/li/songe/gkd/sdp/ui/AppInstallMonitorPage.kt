package li.songe.gkd.sdp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.sdp.ui.share.LocalNavController
import java.text.SimpleDateFormat
import java.util.*

@Destination<RootGraph>
@Composable
fun AppInstallMonitorPage() {
    val navController = LocalNavController.current
    val vm: AppInstallMonitorVm = viewModel()
    
    val monitoredApps by vm.monitoredAppsFlow.collectAsState()
    val heatmapData by vm.heatmapDataFlow.collectAsState()
    val selectedDateLogs by vm.selectedDateLogs.collectAsState()
    val presentApps by vm.presentAppsOnDate.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("软件安装监测") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.exportToCsv() }) {
                        Icon(Icons.Default.Share, contentDescription = "导出")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加监控")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 热力图区域
            item {
                Text(
                    "安装记录热力图",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                HeatmapGrid(
                    data = heatmapData,
                    onDateClick = { date ->
                        selectedDate = date
                        vm.loadLogsForDate(date)
                    }
                )
            }
            
            // 日详情
            if (selectedDate != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "$selectedDate 当日存在应用",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { selectedDate = null }) {
                                    Text("关闭")
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (presentApps.isEmpty()) {
                                Text("当日无监控应用")
                            } else {
                                presentApps.forEach { app ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        // 状态指示器
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF4CAF50))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                app.appName,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            val installWaitDays = (System.currentTimeMillis() - app.installTime) / (1000 * 60 * 60 * 24)
                                            Text(
                                                "安装于: ${formatTime(app.installTime)} (已安装 ${installWaitDays}天)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            if (app.uninstallTime != null) {
                                                val uninstallDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(app.uninstallTime))
                                                val durationDays = (app.uninstallTime - app.installTime) / (1000 * 60 * 60 * 24)
                                                Text(
                                                    "卸载于: $uninstallDate (共存活 ${durationDays}天)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                        
                                        // 是否现在仍存在
                                        Text(
                                            if (app.isStillInstalledNow) "✓ 存在" else "✗ 已删",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (app.isStillInstalledNow) Color(0xFF4CAF50) else Color(0xFFF44336)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 监控应用列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.isListExpanded = !vm.isListExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "监控列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        if (vm.isListExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "toggle"
                    )
                }
            }
            
            if (vm.isListExpanded) {
                items(monitoredApps, key = { it.packageName }) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    app.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                if (app.isCurrentlyInstalled) {
                                    Text(
                                        "⚠️ 当前已安装",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            Switch(
                                checked = app.enabled,
                                onCheckedChange = { vm.toggleAppEnabled(app) }
                            )
                            
                            IconButton(onClick = { vm.deleteMonitoredApp(app) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
    
    // 添加监控应用对话框
    if (showAddDialog) {
        AddMonitoredAppDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { packageName, displayName ->
                vm.addMonitoredApp(packageName, displayName)
                showAddDialog = false
            }
        )
    }
    
    // 初始化默认应用列表
    LaunchedEffect(Unit) {
        vm.initDefaultApps()
    }
}

@Composable
private fun HeatmapGrid(
    data: Map<String, Int>,
    onDateClick: (String) -> Unit
) {
    // 获取最近90天的日期
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dates = (0 until 90).map { daysAgo ->
        calendar.timeInMillis = System.currentTimeMillis() - daysAgo * 24 * 60 * 60 * 1000L
        dateFormat.format(calendar.time)
    }.reversed()
    
    val maxCount = data.values.maxOrNull() ?: 1
    
    // 每行显示7天
    val weeks = dates.chunked(7)
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(weeks) { week ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { date ->
                    val count = data[date] ?: 0
                    val intensity = if (maxCount > 0) count.toFloat() / maxCount else 0f
                    
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    count == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                    intensity < 0.25f -> Color(0xFFB9F6CA)
                                    intensity < 0.5f -> Color(0xFF69F0AE)
                                    intensity < 0.75f -> Color(0xFF00E676)
                                    else -> Color(0xFF00C853)
                                }
                            )
                            .clickable { onDateClick(date) },
                        contentAlignment = Alignment.Center
                    ) {}
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // 图例
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("少", style = MaterialTheme.typography.labelSmall)
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            Color(0xFFB9F6CA),
            Color(0xFF69F0AE),
            Color(0xFF00E676),
            Color(0xFF00C853)
        ).forEach { color ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        Text("多", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AddMonitoredAppDialog(
    onDismiss: () -> Unit,
    onAdd: (packageName: String, displayName: String) -> Unit
) {
    val vm: AppInstallMonitorVm = viewModel()
    val installedApps by vm.installedAppsFlow.collectAsState()
    val appIcons by vm.appIconsFlow.collectAsState() // 获取图标 Map
    val searchKeyword by vm.searchKeyword.collectAsState()
    val showSystemApps by vm.showSystemApps.collectAsState()
    val monitoredApps by vm.monitoredAppsFlow.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // 手动输入的状态
    var inputPackageName by remember { mutableStateOf("") }
    var inputDisplayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("添加监控应用")
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("列表选择") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("手动输入") }
                    )
                }
            }
        },
        text = {
            if (selectedTab == 0) {
                // 列表选择模式
                Column(modifier = Modifier.height(400.dp)) {
                    OutlinedTextField(
                        value = searchKeyword,
                        onValueChange = { vm.searchKeyword.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索应用名或包名") },
                        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, null) },
                        singleLine = true
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = showSystemApps,
                            onCheckedChange = { vm.showSystemApps.value = it }
                        )
                        Text("显示系统应用", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    if (installedApps.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("未找到应用", color = Color.Gray)
                        }
                    } else {
                        LazyColumn {
                            items(installedApps, key = { it.id }) { app ->
                                val isAdded = monitoredApps.any { it.packageName == app.id }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAdded) {
                                            onAdd(app.id, app.name)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 尝试显示应用图标，如果没有则显示默认图标
                                    val icon = appIcons[app.id]
                                    if (icon != null) {
                                        androidx.compose.foundation.Image(
                                            painter = com.google.accompanist.drawablepainter.rememberDrawablePainter(icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(40.dp).background(Color.LightGray, RoundedCornerShape(8.dp))
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(app.id, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    
                                    if (isAdded) {
                                        Text("已添加", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 手动输入模式
                Column {
                    OutlinedTextField(
                        value = inputPackageName,
                        onValueChange = { inputPackageName = it },
                        label = { Text("包名") },
                        placeholder = { Text("com.example.app") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputDisplayName,
                        onValueChange = { inputDisplayName = it },
                        label = { Text("显示名称") },
                        placeholder = { Text("应用名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1) {
                TextButton(
                    onClick = { onAdd(inputPackageName.trim(), inputDisplayName.trim()) },
                    enabled = inputPackageName.isNotBlank() && inputDisplayName.isNotBlank()
                ) {
                    Text("添加")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
        dismissButton = null
    )
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
