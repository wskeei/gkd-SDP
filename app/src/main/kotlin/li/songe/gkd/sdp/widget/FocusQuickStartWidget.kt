package li.songe.gkd.sdp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import li.songe.gkd.sdp.R

class FocusQuickStartWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_START_FOCUS = "li.songe.gkd.sdp.action.START_FOCUS"
        const val EXTRA_RULE_ID = "li.songe.gkd.sdp.extra.RULE_ID"
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
    views.setRemoteAdapter(R.id.widget_list, intent)

    // Set the empty view (optional, if we had one)
    // views.setEmptyView(R.id.widget_list, R.id.empty_view)

    // Set up the pending intent template for items
    val clickIntent = Intent(context, FocusQuickStartWidget::class.java).apply {
        action = FocusQuickStartWidget.ACTION_START_FOCUS
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        clickIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    views.setPendingIntentTemplate(R.id.widget_list, pendingIntent)

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
}
