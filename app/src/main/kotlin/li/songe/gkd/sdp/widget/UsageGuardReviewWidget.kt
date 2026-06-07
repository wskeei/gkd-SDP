package li.songe.gkd.sdp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.util.UsageGuardHistoryPolicy
import li.songe.gkd.sdp.util.UsageGuardReviewPolicy
import java.time.LocalDate

class UsageGuardReviewWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateUsageGuardReviewWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UsageGuardReviewWidget::class.java))
            updateUsageGuardReviewWidgets(context, manager, ids)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
internal fun updateUsageGuardReviewWidgets(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
) {
    if (appWidgetIds.isEmpty()) return
    GlobalScope.launch(Dispatchers.IO) {
        val today = LocalDate.now()
        val (startAt, endAt) = UsageGuardHistoryPolicy.dayRange(today)
        val records = DbSet.usageGuardRecordDao.queryByRequestedAtRange(startAt, endAt).first()
        val widgetSummary = UsageGuardReviewPolicy.widgetSummary(
            UsageGuardReviewPolicy.summarize(records),
        )

        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_usage_guard_review)
            views.setTextViewText(R.id.usage_guard_widget_title, widgetSummary.title)
            views.setTextViewText(R.id.usage_guard_widget_metric, widgetSummary.metric)
            views.setTextViewText(R.id.usage_guard_widget_hint, widgetSummary.hint)
            views.setOnClickPendingIntent(
                R.id.usage_guard_widget_root,
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, MainActivity::class.java).apply {
                        data = Uri.parse("gkd://page?tab=0")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
