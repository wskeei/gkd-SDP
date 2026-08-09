package li.songe.gkd.sdp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.LogUtils

class FocusQuickStartWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_START_FOCUS = "li.songe.gkd.sdp.action.START_FOCUS"
        const val EXTRA_RULE_ID = "li.songe.gkd.sdp.extra.RULE_ID"
        const val EXTRA_WIDGET_ID = "li.songe.gkd.sdp.extra.WIDGET_ID"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_START_FOCUS) {
            if (intent.component != ComponentName(context, FocusQuickStartWidget::class.java)) return
            val appWidgetId = intent.getIntExtra(
                EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            if (!isValidWidgetId(context, appWidgetId, FocusQuickStartWidget::class.java)) return
            val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
            if (ruleId == -1L) return
            val selectedRuleIds = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .getString("widget_$appWidgetId", "")
                .orEmpty()
                .split(',')
                .mapNotNull(String::toLongOrNull)
                .toSet()
            if (ruleId !in selectedRuleIds) return

            val pendingResult = goAsync()
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val rule = DbSet.focusRuleDao.getById(ruleId)
                    if (rule != null) {
                        val session = DbSet.focusSessionDao.getSessionNow() ?: li.songe.gkd.sdp.data.FocusSession(
                            whitelistApps = "[]",
                            interceptMessage = "专注当下"
                        )
                        val now = System.currentTimeMillis()
                        val endTime = now + rule.durationMinutes * 60 * 1000
                        
                        val newSession = session.copy(
                            isActive = true,
                            ruleId = rule.id,
                            startTime = now,
                            endTime = endTime,
                            whitelistApps = rule.whitelistApps,
                            interceptMessage = rule.interceptMessage,
                            isManual = true,
                            isLocked = rule.isLocked,
                            lockEndTime = if (rule.isLocked) now + rule.lockDurationMinutes * 60 * 1000 else 0
                        )
                        DbSet.focusSessionDao.insert(newSession)
                        
                        // 注意：不需要直接启动 FocusOverlayService
                        // 专注会话已保存到数据库，FocusModeEngine 会自动检测并在用户打开被阻止的应用时显示拦截页面
                        // 直接启动 overlay 会导致无法退出的问题

                        // Launch Main Activity to show feedback
                        val appIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            data = Uri.parse("gkd://self-control")
                        }
                        context.startActivity(appIntent)
                    }
                } catch (e: Exception) {
                    LogUtils.d("focus widget action failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
        } else if (intent.action in WIDGET_SYSTEM_ACTIONS) {
            super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.widget_focus_quick_start)

    // Set up the collection adapter
    val intent = Intent(context, FocusWidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
    }
    @Suppress("DEPRECATION")
    views.setRemoteAdapter(R.id.widget_list, intent)

    // Set the empty view (optional, if we had one)
    // views.setEmptyView(R.id.widget_list, R.id.empty_view)

    // Set up the pending intent template for items
    val clickIntent = Intent(context, FocusQuickStartWidget::class.java).apply {
        action = FocusQuickStartWidget.ACTION_START_FOCUS
        putExtra(FocusQuickStartWidget.EXTRA_WIDGET_ID, appWidgetId)
        data = Uri.parse("gkd://widget/focus/$appWidgetId")
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        clickIntent,
        // Collection-item fill-in intents require a mutable template PendingIntent.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    views.setPendingIntentTemplate(R.id.widget_list, pendingIntent)

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
    appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.widget_list)
}

internal val WIDGET_SYSTEM_ACTIONS = setOf(
    AppWidgetManager.ACTION_APPWIDGET_UPDATE,
    AppWidgetManager.ACTION_APPWIDGET_ENABLED,
    AppWidgetManager.ACTION_APPWIDGET_DISABLED,
    AppWidgetManager.ACTION_APPWIDGET_DELETED,
    AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,
    AppWidgetManager.ACTION_APPWIDGET_RESTORED,
)

internal fun isValidWidgetId(
    context: Context,
    appWidgetId: Int,
    providerClass: Class<*>,
): Boolean {
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
    val expected = ComponentName(context, providerClass)
    return AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider == expected
}
