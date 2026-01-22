package li.songe.gkd.sdp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.FocusRule
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.style.AppTheme

class FocusWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            AppTheme {
                FocusWidgetConfigScreen(
                    onSave = { selectedIds ->
                        saveWidgetConfig(this, appWidgetId, selectedIds)
                    }
                )
            }
        }
    }

    private fun saveWidgetConfig(context: Context, appWidgetId: Int, selectedIds: Set<Long>) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        edit.putString("widget_$appWidgetId", selectedIds.joinToString(","))
        edit.apply()

        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusWidgetConfigScreen(onSave: (Set<Long>) -> Unit) {
    var rules by remember { mutableStateOf<List<FocusRule>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    
    LaunchedEffect(Unit) {
         val allRules = DbSet.focusRuleDao.getEnabledList()
         rules = allRules.filter { it.isQuickStart }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                 title = { Text("选择专注规则") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(rules) { rule ->
                    val isSelected = selectedIds.contains(rule.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (isSelected) {
                                    selectedIds - rule.id
                                } else {
                                    selectedIds + rule.id
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { 
                                selectedIds = if (it) selectedIds + rule.id else selectedIds - rule.id
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = rule.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = rule.formatDuration(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider()
                }
            }
            Button(
                onClick = { onSave(selectedIds) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("保存")
            }
        }
    }
}
