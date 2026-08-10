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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.a11y.A11yRuleEngine
import li.songe.gkd.sdp.a11y.AppBlockerEngine
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.BlockTimeRule
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.ui.component.InterceptionSourceCard
import li.songe.gkd.sdp.ui.component.InterceptionSourcePresentation
import li.songe.gkd.sdp.ui.component.SelfControlElapsedCard
import li.songe.gkd.sdp.ui.component.SelfControlInsightCurrentReference
import li.songe.gkd.sdp.ui.share.ServiceOverlayLifecycleOwner
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.AppBlockerDecisionPolicy
import androidx.compose.ui.res.stringResource

class AppBlockerOverlayService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_BLOCKED_APP = "blockedApp"
        const val EXTRA_EVENT_KEY = "eventKey"
        const val EXTRA_EVENT_KIND = "eventKind"
        const val EXTRA_SUBJECT_ID = "subjectId"
        const val EXTRA_SUBJECT_LABEL = "subjectLabel"
        const val EXTRA_RULE_ID = "ruleId"
        const val EXTRA_RULE_TARGET_TYPE = "ruleTargetType"
        const val EXTRA_RULE_TARGET_ID = "ruleTargetId"
        const val EXTRA_RULE_START_TIME = "ruleStartTime"
        const val EXTRA_RULE_END_TIME = "ruleEndTime"
        const val EXTRA_RULE_DAYS = "ruleDays"
        const val EXTRA_RULE_ALLOW_MODE = "ruleAllowMode"
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var overlayLifecycleOwner: ServiceOverlayLifecycleOwner? = null
    private var elapsedState by mutableStateOf<SelfControlElapsedPolicy.ElapsedState>(
        SelfControlElapsedPolicy.ElapsedState.Loading,
    )
    private var insightSamples by mutableStateOf(emptyList<SelfControlInsightWindowPolicy.IntervalSample>())
    private var insightAnchorAt by mutableStateOf<Long?>(null)
    private var currentEventId by mutableStateOf<Long?>(null)
    private var currentGapMs by mutableStateOf<Long?>(null)
    private var selectedWindow by mutableStateOf(SelfControlInsightWindowPolicy.Window.LAST_24_HOURS)

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

        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "这真的重要吗？"
        val blockedApp = intent?.getStringExtra(EXTRA_BLOCKED_APP).orEmpty()
        val eventKey = intent?.getStringExtra(EXTRA_EVENT_KEY).orEmpty()
        val eventKind = intent?.getIntExtra(
            EXTRA_EVENT_KIND,
            SelfControlAttempt.KIND_APP_BLOCKER,
        ) ?: SelfControlAttempt.KIND_APP_BLOCKER
        val subjectId = intent?.getStringExtra(EXTRA_SUBJECT_ID).orEmpty().ifBlank { blockedApp }
        val subjectLabel = intent?.getStringExtra(EXTRA_SUBJECT_LABEL).orEmpty().ifBlank { blockedApp }
        val source = intent?.blockerSource() ?: InterceptionSourcePresentation.unknown()
        if (eventKind != SelfControlAttempt.KIND_APP_BLOCKER ||
            blockedApp.isBlank() ||
            eventKey.isBlank() ||
            eventKey != SelfControlElapsedPolicy.appBlockerEventKey(blockedApp) ||
            subjectId.isBlank() ||
            subjectId != blockedApp
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        elapsedState = SelfControlElapsedPolicy.ElapsedState.Loading
        insightSamples = emptyList()
        insightAnchorAt = null
        currentEventId = null
        currentGapMs = null
        selectedWindow = SelfControlInsightWindowPolicy.Window.LAST_24_HOURS
        if (showOverlay(message, blockedApp, source)) {
            recordElapsedAttempt(
                eventKey = eventKey,
                eventKind = eventKind,
                subjectId = subjectId,
                subjectLabel = subjectLabel,
                occurredAt = System.currentTimeMillis(),
            )
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
        if (eventKey.isBlank() || eventKind != SelfControlAttempt.KIND_APP_BLOCKER) {
            elapsedState = SelfControlElapsedPolicy.ElapsedState.Unavailable
            return
        }
        // Persist independently of the overlay lifecycle: pressing an exit action immediately
        // after the window mounts must not cancel the successful-attempt write.
        appScope.launch {
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
                insightSamples = insight.samples
                insightAnchorAt = occurredAt
                currentEventId = insight.currentEventId
                currentGapMs = insight.previousOccurredAt?.let { previous ->
                    (occurredAt - previous).takeIf { it >= 0L }
                }
                elapsedState = SelfControlElapsedPolicy.stateForAttempt(
                    insight.previousOccurredAt,
                    occurredAt,
                )
            }.onFailure {
                insightSamples = emptyList()
                currentEventId = null
                currentGapMs = null
                elapsedState = SelfControlElapsedPolicy.ElapsedState.Unavailable
            }
        }
    }

    private fun showOverlay(
        message: String,
        blockedApp: String,
        source: InterceptionSourcePresentation,
    ): Boolean {
        if (view != null) return false

        val lifecycleOwner = ServiceOverlayLifecycleOwner()
        overlayLifecycleOwner = lifecycleOwner
        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@AppBlockerOverlayService)
            setContent {
                AppTheme {
                    AppBlockerInterceptScreen(
                        message = message,
                        elapsedState = elapsedState,
                        samples = insightSamples,
                        insightAnchorAt = insightAnchorAt,
                        currentReference = currentEventId?.let {
                            SelfControlInsightCurrentReference(gapMs = currentGapMs, eventId = it)
                        },
                        nowEpochMs = insightAnchorAt,
                        selectedWindow = selectedWindow,
                        onWindowSelected = { selectedWindow = it },
                        source = source,
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
            lifecycleOwner.onViewAdded()
            mounted = true
        }.onFailure { error ->
            view?.let { runCatching { windowManager.removeViewImmediate(it) } }
            lifecycleOwner.onViewRemoved()
            overlayLifecycleOwner = null
            view = null
            LogUtils.d("app blocker overlay mount rejected", error::class.java.simpleName)
            AppBlockerEngine.clearCooldown()
            stopSelf()
        }
        return mounted
    }

    override fun onDestroy() {
        overlayLifecycleOwner?.onViewRemoved()
        overlayLifecycleOwner = null
        super.onDestroy()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun Intent.blockerSource(): InterceptionSourcePresentation? {
        if (!hasExtra(EXTRA_RULE_ID)) return null
        val ruleId = getLongExtra(EXTRA_RULE_ID, -1L)
        val targetType = getIntExtra(EXTRA_RULE_TARGET_TYPE, -1)
        val targetId = getStringExtra(EXTRA_RULE_TARGET_ID).orEmpty()
        val startTime = getStringExtra(EXTRA_RULE_START_TIME).orEmpty()
        val endTime = getStringExtra(EXTRA_RULE_END_TIME).orEmpty()
        val daysOfWeek = getStringExtra(EXTRA_RULE_DAYS).orEmpty()
        if (ruleId < 0L ||
            targetType !in setOf(BlockTimeRule.TARGET_TYPE_APP, BlockTimeRule.TARGET_TYPE_GROUP) ||
            targetId.isBlank() ||
            startTime.isBlank() ||
            endTime.isBlank() ||
            daysOfWeek.isBlank() ||
            !AppBlockerDecisionPolicy.isValidTime(startTime) ||
            !AppBlockerDecisionPolicy.isValidTime(endTime) ||
            !AppBlockerDecisionPolicy.isValidDays(daysOfWeek)
        ) return null
        val rule = BlockTimeRule(
            id = ruleId,
            targetType = targetType,
            targetId = targetId,
            startTime = startTime,
            endTime = endTime,
            daysOfWeek = daysOfWeek,
            isAllowMode = getBooleanExtra(EXTRA_RULE_ALLOW_MODE, false),
        )
        return InterceptionSourcePresentation.appBlocker(rule)
    }
}

@Composable
fun AppBlockerInterceptScreen(
    message: String,
    elapsedState: SelfControlElapsedPolicy.ElapsedState,
    samples: List<SelfControlInsightWindowPolicy.IntervalSample> = emptyList(),
    insightAnchorAt: Long? = null,
    currentReference: SelfControlInsightCurrentReference? = null,
    nowEpochMs: Long? = null,
    selectedWindow: SelfControlInsightWindowPolicy.Window =
        SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
    onWindowSelected: (SelfControlInsightWindowPolicy.Window) -> Unit = {},
    source: InterceptionSourcePresentation = InterceptionSourcePresentation.unknown(),
    onExit: () -> Unit
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
        color = MaterialTheme.colorScheme.background
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

            InterceptionSourceCard(
                presentation = source,
                modifier = Modifier.padding(top = 24.dp),
            )

            SelfControlElapsedCard(
                context = SelfControlElapsedPolicy.Context.APP_OPEN_ATTEMPT,
                state = elapsedState,
                samples = samples,
                insightAnchorAt = insightAnchorAt,
                currentReference = currentReference,
                nowEpochMs = nowEpochMs,
                selectedWindow = selectedWindow,
                onWindowSelected = onWindowSelected,
                modifier = Modifier.padding(top = 16.dp),
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.s_c2768060ab, timeLeft))
            }
        }
    }
}
