package li.songe.gkd.sdp.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.data.AppInstallLog
import li.songe.gkd.sdp.data.DateCount
import li.songe.gkd.sdp.data.MonitoredApp
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.util.toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AppInstallMonitorVm : BaseViewModel() {
    
    val monitoredAppsFlow = DbSet.monitoredAppDao.queryAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    // 获取所有日志，在内存中计算热力图数据
    val heatmapDataFlow: StateFlow<Map<String, Int>> = DbSet.appInstallLogDao.queryAll()
        .map { logs -> calculateHeatmapData(logs) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    
    private val _selectedDateLogs = MutableStateFlow<List<AppInstallLog>>(emptyList())
    val selectedDateLogs: StateFlow<List<AppInstallLog>> = _selectedDateLogs
    
    private val _presentAppsOnDate = MutableStateFlow<List<PresenceInfo>>(emptyList())
    val presentAppsOnDate: StateFlow<List<PresenceInfo>> = _presentAppsOnDate
    
    // 添加应用 - 搜索和过滤状态
    val searchKeyword = MutableStateFlow("")
    val showSystemApps = MutableStateFlow(false)
    
    // 本机已安装应用列表 (搜索过滤后)
    val installedAppsFlow = kotlinx.coroutines.flow.combine(
        li.songe.gkd.sdp.util.appInfoMapFlow,
        searchKeyword,
        showSystemApps
    ) { appMap, keyword, showSystem ->
        appMap.values.asSequence()
            .filter { app -> 
                if (!showSystem && app.isSystem) return@filter false
                if (keyword.isBlank()) return@filter true
                app.name.contains(keyword, true) || app.id.contains(keyword, true)
            }
            .sortedWith(compareBy({ !it.name.matches(Regex("[\u4e00-\u9fa5].*")) }, { it.name })) // 简单排序：中文在前
            .toList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    val appIconsFlow = li.songe.gkd.sdp.util.appIconMapFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    
    var isListExpanded by mutableStateOf(true)
    
    // 缓存当前已安装的监控应用
    private var installedPackages: Set<String> = emptySet()
    
    init {
        refreshInstalledStatus()
    }
    
    fun initDefaultApps() = viewModelScope.launch(Dispatchers.IO) {
        DbSet.monitoredAppDao.insertAll(MonitoredApp.DEFAULT_APPS)
        refreshInstalledStatus()
    }
    
    private fun refreshInstalledStatus() = viewModelScope.launch(Dispatchers.IO) {
        val pm = app.packageManager
        val monitored = DbSet.monitoredAppDao.getEnabledPackageNames()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        installedPackages = monitored.filter { packageName ->
            try {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
                
                // 关键修复：如果已安装但数据库无记录，补录安装日志
                // 这样热力图就能立即显示历史数据
                val hasLog = DbSet.appInstallLogDao.hasInstallLog(packageName)
                if (!hasLog) {
                    val installTime = packageInfo.firstInstallTime
                    val appName = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
                    val log = AppInstallLog(
                        packageName = packageName,
                        appName = appName,
                        action = AppInstallLog.ACTION_INSTALL,
                        timestamp = installTime,
                        date = dateFormat.format(Date(installTime))
                    )
                    DbSet.appInstallLogDao.insert(log)
                }
                true
            } catch (e: Exception) {
                false
            }
        }.toSet()
        
        // 补录“丢失”的卸载记录 (Ghost Installs)
        // 场景：上次检测时应用还在（或有安装日志），但现在应用已不在，且最后一条日志是 INSTALL
        val allLogs = DbSet.appInstallLogDao.getAll()
        val logsByPackage = allLogs.groupBy { it.packageName }
        
        monitored.forEach { packageName ->
            val isInstalled = installedPackages.contains(packageName)
            // 如果当前未安装
            if (!isInstalled) {
                val packageLogs = logsByPackage[packageName]?.sortedBy { it.timestamp }
                val lastLog = packageLogs?.lastOrNull()
                
                // 如果最后一条记录是 安装，说明我们错过了解载广播（或者在应用没运行时卸载了）
                if (lastLog != null && lastLog.action == AppInstallLog.ACTION_INSTALL) {
                    val now = System.currentTimeMillis()
                    val log = AppInstallLog(
                        packageName = packageName,
                        appName = lastLog.appName, // 沿用之前的名字
                        action = AppInstallLog.ACTION_UNINSTALL,
                        timestamp = now,
                        date = dateFormat.format(Date(now))
                    )
                    DbSet.appInstallLogDao.insert(log)
                }
            }
        }
        
        // 更新数据库中的安装状态
        monitored.forEach { packageName ->
            val isInstalled = installedPackages.contains(packageName)
            DbSet.monitoredAppDao.updateInstalledStatus(packageName, isInstalled)
        }
    }
    
    private fun calculateHeatmapData(logs: List<AppInstallLog>): Map<String, Int> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sortedLogs = logs.sortedBy { it.timestamp }
        val resultMap = mutableMapOf<String, Int>()
        
        // 1. 确定日期范围：从最早日志的前一天到今天（或者固定过去90天）
        // 这里为了和 UI 匹配，计算过去 90 天
        val calendar = Calendar.getInstance()
        val today = System.currentTimeMillis()
        val startDateMillis = today - 90L * 24 * 60 * 60 * 1000L
        
        // 2. 初始化回放状态：找出起始日期之前的状态
        val runningInstalledApps = mutableSetOf<String>()
        
        // 预处理：先回放到起始日期之前
        sortedLogs.filter { it.timestamp < startDateMillis }.forEach { log ->
            if (log.action == AppInstallLog.ACTION_INSTALL) {
                runningInstalledApps.add(log.packageName)
            } else if (log.action == AppInstallLog.ACTION_UNINSTALL) {
                runningInstalledApps.remove(log.packageName)
            }
        }
        
        // 3. 逐天回放
        for (i in 90 downTo 0) {
            calendar.timeInMillis = today - i * 24 * 60 * 60 * 1000L
            val dateStr = dateFormat.format(calendar.time)
            
            // 获取当天的开始和结束时间戳
            // 注意：简单起见，这里假设日志按时间排序，只需处理当天的日志
            // 更精确的做法是使用 Calendar 设置时分秒
            val dayStart = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
            
            // 找出当天的日志
            val daysLogs = sortedLogs.filter { it.timestamp in dayStart..dayEnd }
            
            // 当天曾经存在的应用 = (当天开始时已存在的) + (当天安装的)
            // 即使当天安装后又卸载，它在当天也算存在过
            val activeAppsToday = runningInstalledApps.toMutableSet()
            
            daysLogs.forEach { log ->
                if (log.action == AppInstallLog.ACTION_INSTALL) {
                    runningInstalledApps.add(log.packageName)
                    activeAppsToday.add(log.packageName)
                } else if (log.action == AppInstallLog.ACTION_UNINSTALL) {
                    runningInstalledApps.remove(log.packageName)
                    // 注意：卸载了，但它今天确实存在过，所以 activeAppsToday 不移除
                }
            }
            
            resultMap[dateStr] = activeAppsToday.size
        }
        
        return resultMap
    }
    
    fun loadLogsForDate(date: String) = viewModelScope.launch(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val targetDateStart = dateFormat.parse(date)?.time ?: return@launch
        val targetDateEnd = targetDateStart + 24 * 60 * 60 * 1000L - 1
        
        val allLogs = DbSet.appInstallLogDao.getAll().sortedBy { it.timestamp }
        
        // 回放状态直到目标日期开始
        val runningInstalledApps = mutableSetOf<String>()
        val packageLastInstallTime = mutableMapOf<String, Long>()
        
        allLogs.filter { it.timestamp < targetDateStart }.forEach { log ->
            if (log.action == AppInstallLog.ACTION_INSTALL) {
                runningInstalledApps.add(log.packageName)
                packageLastInstallTime[log.packageName] = log.timestamp
            } else if (log.action == AppInstallLog.ACTION_UNINSTALL) {
                runningInstalledApps.remove(log.packageName)
            }
        }
        
        // 收集当天存在的应用信息
        val presentInfos = mutableListOf<PresenceInfo>()
        
        // 1. 当天开始时已存在的
        runningInstalledApps.forEach { pkg ->
            val installTime = packageLastInstallTime[pkg] ?: 0L
            // 查找当天的卸载记录（如果有）
            val uninstallLog = allLogs.find { 
                it.packageName == pkg && 
                it.action == AppInstallLog.ACTION_UNINSTALL && 
                it.timestamp in targetDateStart..targetDateEnd 
            }
            
            // 还需要查找应用名，这里可能需要从 logs 或 packageManager 获取
            // 简单起见，我们从最近一条相关日志取名字
            val lastLog = allLogs.findLast { it.packageName == pkg && it.timestamp <= targetDateEnd }
            val appName = lastLog?.appName ?: pkg
            
            presentInfos.add(PresenceInfo(
                packageName = pkg,
                appName = appName,
                installTime = installTime,
                uninstallTime = uninstallLog?.timestamp,
                isStillInstalledNow = checkIfStillInstalled(pkg)
            ))
        }
        
        // 2. 当天新安装的
        val todayInstallLogs = allLogs.filter { 
            it.timestamp in targetDateStart..targetDateEnd && 
            it.action == AppInstallLog.ACTION_INSTALL 
        }
        
        todayInstallLogs.forEach { installLog ->
            // 如果已经在列表里（比如：之前存在，今天卸载又安装），需要小心处理重复
            // 简单逻辑：如果 installLog 发生，它就是今天存在的。
            // 查找对应的卸载（在该安装之后，且仍在当天）
            val uninstallLog = allLogs.find { 
                it.packageName == installLog.packageName && 
                it.action == AppInstallLog.ACTION_UNINSTALL && 
                it.timestamp > installLog.timestamp &&
                it.timestamp <= targetDateEnd
            }
            
            // 如果列表里还没有这个 installTime 的记录，添加
            if (presentInfos.none { it.packageName == installLog.packageName && it.installTime == installLog.timestamp }) {
                presentInfos.add(PresenceInfo(
                    packageName = installLog.packageName,
                    appName = installLog.appName,
                    installTime = installLog.timestamp,
                    uninstallTime = uninstallLog?.timestamp,
                    isStillInstalledNow = checkIfStillInstalled(installLog.packageName)
                ))
            }
        }
        
        _presentAppsOnDate.value = presentInfos.sortedByDescending { it.installTime }
    }
    
    fun checkIfStillInstalled(packageName: String): Boolean {
        return installedPackages.contains(packageName)
    }
    
    fun toggleAppEnabled(app: MonitoredApp) = viewModelScope.launch(Dispatchers.IO) {
        DbSet.monitoredAppDao.update(app.copy(enabled = !app.enabled))
    }
    
    fun addMonitoredApp(packageName: String, displayName: String) = viewModelScope.launch(Dispatchers.IO) {
        if (packageName.isBlank() || displayName.isBlank()) {
            toast("请填写完整信息")
            return@launch
        }
        
        val existing = DbSet.monitoredAppDao.getByPackageName(packageName)
        if (existing != null) {
            toast("该应用已在监控列表中")
            return@launch
        }
        
        DbSet.monitoredAppDao.insert(MonitoredApp(packageName, displayName))
        toast("已添加: $displayName")
        refreshInstalledStatus()
    }
    
    fun deleteMonitoredApp(app: MonitoredApp) = viewModelScope.launch(Dispatchers.IO) {
        DbSet.monitoredAppDao.delete(app)
        toast("已移除: ${app.displayName}")
    }
    
    fun exportToCsv() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val logs = DbSet.appInstallLogDao.queryAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value
            
            if (logs.isEmpty()) {
                toast("暂无数据可导出")
                return@launch
            }
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "app_install_logs_${dateFormat.format(Date())}.csv"
            
            val file = File(app.cacheDir, fileName)
            file.bufferedWriter().use { writer ->
                writer.write("日期,时间,应用名,包名,操作,是否仍存在\n")
                
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                logs.forEach { log ->
                    val stillExists = if (log.action == AppInstallLog.ACTION_INSTALL) {
                        if (checkIfStillInstalled(log.packageName)) "是" else "否"
                    } else {
                        "-"
                    }
                    
                    writer.write("${log.date},${timeFormat.format(Date(log.timestamp))},${log.appName},${log.packageName},${if (log.action == "install") "安装" else "卸载"},$stillExists\n")
                }
            }
            
            // 分享文件
            val uri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            app.startActivity(Intent.createChooser(shareIntent, "导出安装记录").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            
        } catch (e: Exception) {
            toast("导出失败: ${e.message}")
        }
    }
}

data class PresenceInfo(
    val packageName: String,
    val appName: String,
    val installTime: Long,
    val uninstallTime: Long?,
    val isStillInstalledNow: Boolean
)
