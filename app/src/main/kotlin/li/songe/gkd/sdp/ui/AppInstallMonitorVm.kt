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
        
        // 更新数据库中的安装状态
        monitored.forEach { packageName ->
            val isInstalled = installedPackages.contains(packageName)
            DbSet.monitoredAppDao.updateInstalledStatus(packageName, isInstalled)
        }
    }
    
    private fun calculateHeatmapData(logs: List<AppInstallLog>): Map<String, Int> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val resultMap = mutableMapOf<String, Int>()
        
        // 计算过去 90 天每一天的数据
        for (i in 0 until 90) {
            calendar.timeInMillis = System.currentTimeMillis() - i * 24 * 60 * 60 * 1000L
            val dateStr = dateFormat.format(calendar.time)
            
            // 计算当天存在的应用数量
            // 逻辑：在该日期之前（含）安装，且（在该日期之后卸载 OR 从未卸载）
            val count = logs
                .filter { it.action == AppInstallLog.ACTION_INSTALL && it.date <= dateStr }
                .count { installLog ->
                    val uninstallLog = logs.find { 
                        it.packageName == installLog.packageName && 
                        it.action == AppInstallLog.ACTION_UNINSTALL && 
                        it.timestamp > installLog.timestamp 
                    }
                    // 如果没有卸载记录，或者卸载日期在当天之后，则认为当天仍存在
                    uninstallLog == null || uninstallLog.date > dateStr
                }
            
            resultMap[dateStr] = count
        }
        return resultMap
    }
    
    fun loadLogsForDate(date: String) = viewModelScope.launch(Dispatchers.IO) {
        // 获取对应日期存在的应用
        val allLogs = DbSet.appInstallLogDao.getAll()
        
        val presentApps = allLogs
            .filter { it.action == AppInstallLog.ACTION_INSTALL && it.date <= date }
            .mapNotNull { installLog ->
                // 查找该安装记录之后的卸载记录
                val uninstallLog = allLogs.find { 
                    it.packageName == installLog.packageName && 
                    it.action == AppInstallLog.ACTION_UNINSTALL && 
                    it.timestamp > installLog.timestamp 
                }
                
                // 如果在所选日期之前（含）就已卸载，则不属于当天存在的应用
                if (uninstallLog != null && uninstallLog.date <= date) {
                    return@mapNotNull null
                }
                
                PresenceInfo(
                    packageName = installLog.packageName,
                    appName = installLog.appName,
                    installTime = installLog.timestamp,
                    uninstallTime = uninstallLog?.timestamp,
                    isStillInstalledNow = checkIfStillInstalled(installLog.packageName)
                )
            }
            
        _presentAppsOnDate.value = presentApps
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
