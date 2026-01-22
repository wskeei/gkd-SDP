package li.songe.gkd.sdp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.data.AppInstallLog
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.LogUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 监听应用安装/卸载事件
 */
class AppInstallReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AppInstallReceiver"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        
        // 忽略自己的包名
        if (packageName == context.packageName) return
        
        val action = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> AppInstallLog.ACTION_INSTALL
            Intent.ACTION_PACKAGE_REMOVED -> AppInstallLog.ACTION_UNINSTALL
            Intent.ACTION_PACKAGE_REPLACED -> return  // 忽略更新
            else -> return
        }
        
        LogUtils.d("$TAG: $action - $packageName")
        
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 检查是否在监控列表中
                val enabledPackages = DbSet.monitoredAppDao.getEnabledPackageNames()
                if (!enabledPackages.contains(packageName)) {
                    LogUtils.d("$TAG: $packageName not in monitored list, skipping")
                    return@launch
                }
                
                // 获取应用名称
                val appName = getAppName(context, packageName) ?: packageName
                
                val now = System.currentTimeMillis()
                val log = AppInstallLog(
                    packageName = packageName,
                    appName = appName,
                    action = action,
                    timestamp = now,
                    date = dateFormat.format(Date(now))
                )
                
                DbSet.appInstallLogDao.insert(log)
                
                // 更新监控应用的安装状态
                DbSet.monitoredAppDao.updateInstalledStatus(
                    packageName = packageName,
                    installed = action == AppInstallLog.ACTION_INSTALL
                )
                
                LogUtils.d("$TAG: Logged $action for $appName ($packageName)")
                
            } catch (e: Exception) {
                LogUtils.d("$TAG: Error logging install: ${e.message}")
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun getAppName(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
    }
}
