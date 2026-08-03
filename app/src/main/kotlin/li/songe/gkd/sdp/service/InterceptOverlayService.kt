package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.a11y.A11yRuleEngine
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.ui.component.SelfControlElapsedCard
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.InterceptUtils
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy

class InterceptOverlayService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        const val EXTRA_SUBS_ID = "subsId"
        const val EXTRA_GROUP_KEY = "groupKey"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_COOLDOWN = "cooldown"
        const val EXTRA_EVENT_KEY = "eventKey"
        const val EXTRA_EVENT_KIND = "eventKind"
        const val EXTRA_SUBJECT_ID = "subjectId"
        const val EXTRA_SUBJECT_LABEL = "subjectLabel"
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var elapsedState by mutableStateOf<SelfControlElapsedPolicy.ElapsedState>(
        SelfControlElapsedPolicy.ElapsedState.Loading,
    )
    private var recentCompletedIntervalsMs by mutableStateOf(emptyList<Long>())
    
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (view != null) return START_NOT_STICKY

        val subsId = intent?.getLongExtra(EXTRA_SUBS_ID, -1) ?: -1
        val groupKey = intent?.getIntExtra(EXTRA_GROUP_KEY, -1) ?: -1
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "这真的重要吗？"
        val cooldown = intent?.getIntExtra(EXTRA_COOLDOWN, 5) ?: 5
        val eventKey = intent?.getStringExtra(EXTRA_EVENT_KEY).orEmpty()
        val eventKind = intent?.getIntExtra(EXTRA_EVENT_KIND, 0) ?: 0
        val subjectId = intent?.getStringExtra(EXTRA_SUBJECT_ID).orEmpty()
        val subjectLabel = intent?.getStringExtra(EXTRA_SUBJECT_LABEL).orEmpty().ifBlank { subjectId }

        if (subsId != -1L && groupKey != -1) {
            elapsedState = SelfControlElapsedPolicy.ElapsedState.Loading
            recentCompletedIntervalsMs = emptyList()
            if (showOverlay(subsId, groupKey, message, cooldown)) {
                recordElapsedAttempt(
                    eventKey = eventKey,
                    eventKind = eventKind,
                    subjectId = subjectId,
                    subjectLabel = subjectLabel,
                    occurredAt = System.currentTimeMillis(),
                )
            }
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun recordElapsedAttempt(
        eventKey: String,
        eventKind: Int,
        subjectId: String,
        subjectLabel: String,
        occurredAt: Long,
    ) {
        if (eventKey.isBlank() || eventKind !in setOf(
                SelfControlAttempt.KIND_SELECTOR_INTERCEPT,
                SelfControlAttempt.KIND_URL_INTERCEPT,
            )
        ) {
            elapsedState = SelfControlElapsedPolicy.ElapsedState.Unavailable
            return
        }
        lifecycleScope.launch {
            val result = runCatching {
                val insight = withContext(Dispatchers.IO) {
                    SelfControlIntervalRepository.fromDb().recordIntercept(
                        descriptor = SelfControlIntervalRepository.AttemptDescriptor(
                            eventKey = eventKey,
                            eventKind = eventKind,
                            subjectId = subjectId,
                            subjectLabel = subjectLabel,
                        ),
                        occurredAt = occurredAt,
                    )
                }
                insight
            }
            result.onSuccess { insight ->
                recentCompletedIntervalsMs = insight.recentCompletedIntervalsMs
                elapsedState = SelfControlElapsedPolicy.stateForAttempt(
                    insight.previousOccurredAt,
                    occurredAt,
                )
            }.onFailure {
                recentCompletedIntervalsMs = emptyList()
                elapsedState = SelfControlElapsedPolicy.ElapsedState.Unavailable
            }
        }
    }

    private fun showOverlay(subsId: Long, groupKey: Int, message: String, cooldown: Int): Boolean {
        if (view != null) return false

        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@InterceptOverlayService)
            setViewTreeSavedStateRegistryOwner(this@InterceptOverlayService)
            setContent {
                AppTheme {
                    InterceptScreen(
                        message = message,
                        cooldown = cooldown,
                        elapsedState = elapsedState,
                        recentCompletedIntervalsMs = recentCompletedIntervalsMs,
                        onContinue = {
                            InterceptUtils.setAllowed(subsId, groupKey, cooldown)
                            stopSelf()
                        },
                        onExit = {
                            A11yRuleEngine.performActionHome()
                            stopSelf()
                        }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        
        var mounted = false
        runCatching {
            windowManager.addView(view, params)
            mounted = true
        }.onFailure { error ->
            view?.let { runCatching { windowManager.removeViewImmediate(it) } }
            view = null
            LogUtils.d("selector intercept overlay mount rejected", error::class.java.simpleName)
            UrlBlockerEngine.clearCooldown()
            stopSelf()
        }
        return mounted
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }
}

@Composable
fun InterceptScreen(
    message: String,
    cooldown: Int, // Kept for API compatibility but we use 10s for auto-exit
    onContinue: () -> Unit, // Kept for API compatibility but unused
    onExit: () -> Unit,
    elapsedState: SelfControlElapsedPolicy.ElapsedState =
        SelfControlElapsedPolicy.ElapsedState.Unavailable,
    recentCompletedIntervalsMs: List<Long> = emptyList(),
) {
    var timeLeft by remember { mutableIntStateOf(10) }
    
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        onExit()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background // Opaque
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            SelfControlElapsedCard(
                context = SelfControlElapsedPolicy.Context.RULE_TRIGGER,
                state = elapsedState,
                recentCompletedIntervalsMs = recentCompletedIntervalsMs,
                modifier = Modifier.padding(top = 24.dp),
            )
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("算了 (退出) ${timeLeft}s")
            }
        }
    }
}
