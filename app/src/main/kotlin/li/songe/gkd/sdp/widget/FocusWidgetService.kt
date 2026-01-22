package li.songe.gkd.sdp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.db.DbSet

class FocusWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FocusViewsFactory(this.applicationContext, intent)
    }
}

class FocusViewsFactory(private val context: Context, intent: Intent) : RemoteViewsService.RemoteViewsFactory {
    private val appWidgetId: Int = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    private var rules: List<FocusRule> = emptyList()

    override fun onCreate() {
        // Data loading is done in onDataSetChanged()
    }

    override fun onDataSetChanged() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val selectedIdsStr = prefs.getString("widget_$appWidgetId", "") ?: ""
        
        if (selectedIdsStr.isBlank()) {
            rules = emptyList()
            return
        }

        val selectedIds = selectedIdsStr.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        
        // Query DB
        runBlocking {
            val allRules = DbSet.focusRuleDao.getEnabledList()
            rules = allRules.filter { selectedIds.contains(it.id) }
        }
    }

    override fun onDestroy() {
        rules = emptyList()
    }

    override fun getCount(): Int = rules.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= rules.size) return RemoteViews(context.packageName, R.layout.widget_focus_item)

        val rule = rules[position]
        val views = RemoteViews(context.packageName, R.layout.widget_focus_item)
        views.setTextViewText(R.id.item_name, rule.name)
        views.setTextViewText(R.id.item_duration, rule.formatDuration())

        val fillInIntent = Intent().apply {
            putExtra(FocusQuickStartWidget.EXTRA_RULE_ID, rule.id)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = rules.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
