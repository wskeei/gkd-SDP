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
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in WIDGET_SYSTEM_ACTIONS) super.onReceive(context, intent)
    }

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
            views.setTextViewText(R.id.usage_guard_widget_title, widgetTitle(context, widgetSummary))
            views.setTextViewText(R.id.usage_guard_widget_metric, widgetMetric(context, widgetSummary))
            views.setTextViewText(R.id.usage_guard_widget_hint, widgetHint(context, widgetSummary))
            views.setOnClickPendingIntent(
                R.id.usage_guard_widget_root,
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, MainActivity::class.java).apply {
                        data = Uri.parse("gkd://usage-review")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

private fun widgetTitle(context: Context, summary: UsageGuardReviewPolicy.WidgetSummary): String {
    val periodLabel = context.getString(summary.periodLabelRes)
    return if (summary.requestCount == 0) {
        context.getString(summary.titleRes, periodLabel)
    } else {
        context.getString(summary.titleRes, periodLabel, summary.requestCount)
    }
}

private fun widgetMetric(context: Context, summary: UsageGuardReviewPolicy.WidgetSummary): String {
    val duration = formatWidgetDuration(
        context,
        summary.metricDurationSeconds ?: 0L,
    )
    return when (summary.metricRes) {
        R.string.usage_guard_widget_metric_used ->
            context.getString(summary.metricRes, duration)
        R.string.usage_guard_widget_metric_used_top ->
            context.getString(summary.metricRes, duration, summary.metricTopApp.orEmpty())
        else -> context.getString(summary.metricRes)
    }
}

private fun widgetHint(context: Context, summary: UsageGuardReviewPolicy.WidgetSummary): String {
    return if (summary.hintRes == R.string.usage_guard_widget_hint_empty_period) {
        context.getString(summary.hintRes, context.getString(summary.periodLabelRes))
    } else {
        context.getString(summary.hintRes)
    }
}

private fun formatWidgetDuration(context: Context, totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    if (safeSeconds < 60L) return context.getString(R.string.usage_guard_duration_seconds, safeSeconds)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    if (seconds == 0L) return context.getString(R.string.usage_guard_duration_minutes, minutes)
    return context.getString(R.string.usage_guard_duration_minutes_seconds, minutes, seconds)
}
