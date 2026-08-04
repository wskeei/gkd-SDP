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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.a11y.A11yRuleEngine
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.a11y.UrlBlockerEngine
import li.songe.gkd.sdp.data.SelectorRuleSnapshot
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.ui.component.SelfControlElapsedCard
import li.songe.gkd.sdp.ui.component.SelfControlInsightCurrentReference
import li.songe.gkd.sdp.ui.component.InterceptionSourceCard
import li.songe.gkd.sdp.ui.component.InterceptionSourcePresentation
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.InterceptUtils
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy

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
        const val EXTRA_RECORD_TOKEN = "recordToken"
        const val EXTRA_MATCHED_AT = "matchedAt"
        const val EXTRA_SELECTOR_SUBS_VERSION = "selectorSubsVersion"
        const val EXTRA_SELECTOR_APP_ID = "selectorAppId"
        const val EXTRA_SELECTOR_ACTIVITY_ID = "selectorActivityId"
        const val EXTRA_SELECTOR_GROUP_TYPE = "selectorGroupType"
        const val EXTRA_SELECTOR_GROUP_KEY = "selectorGroupKey"
        const val EXTRA_SELECTOR_RULE_INDEX = "selectorRuleIndex"
        const val EXTRA_SELECTOR_RULE_KEY_PRESENT = "selectorRuleKeyPresent"
        const val EXTRA_SELECTOR_RULE_KEY = "selectorRuleKey"
        const val EXTRA_SELECTOR_RULE_NAME = "selectorRuleName"
        const val EXTRA_SELECTOR_GROUP_NAME = "selectorGroupName"
        const val EXTRA_SELECTOR_SUBS_NAME = "selectorSubsName"
        const val EXTRA_URL_RULE_ID = "urlRuleId"
        const val EXTRA_URL_RULE_NAME = "urlRuleName"
        private const val URL_SUBS_ID = -2L
        private const val URL_GROUP_KEY = 0
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var elapsedState by mutableStateOf<SelfControlElapsedPolicy.ElapsedState>(
        SelfControlElapsedPolicy.ElapsedState.Loading,
    )
    private var insightSamples by mutableStateOf(emptyList<SelfControlInsightWindowPolicy.IntervalSample>())
    private var insightAnchorAt by mutableStateOf<Long?>(null)
    private var currentEventId by mutableStateOf<Long?>(null)
    private var currentGapMs by mutableStateOf<Long?>(null)
    private var selectedWindow by mutableStateOf(SelfControlInsightWindowPolicy.Window.LAST_24_HOURS)
    private val mountedInterceptRecorder by lazy { MountedInterceptRecorder.fromDb() }
    
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
        val matchedAt = intent?.getLongExtra(EXTRA_MATCHED_AT, 0L) ?: 0L
        val source = intent?.interceptionSource(eventKind)
        if (source == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val selectorSnapshot = intent?.selectorSnapshot(eventKind)
        val validIntent = when (eventKind) {
            SelfControlAttempt.KIND_SELECTOR_INTERCEPT ->
                subsId > 0L &&
                    groupKey >= 0 &&
                    selectorSnapshot != null &&
                    eventKey == selectorSnapshot.eventKey() &&
                    subjectId == selectorSnapshot.appId
            SelfControlAttempt.KIND_URL_INTERCEPT -> {
                val ruleId = intent?.getLongExtra(EXTRA_URL_RULE_ID, -1L) ?: -1L
                subsId == URL_SUBS_ID &&
                    groupKey == URL_GROUP_KEY &&
                    ruleId >= 0L &&
                    eventKey == SelfControlElapsedPolicy.urlInterceptEventKey(ruleId) &&
                    subjectId == ruleId.toString()
            }
            else -> false
        }
        if (validIntent) {
            elapsedState = SelfControlElapsedPolicy.ElapsedState.Loading
            insightSamples = emptyList()
            insightAnchorAt = null
            currentEventId = null
            currentGapMs = null
            selectedWindow = SelfControlInsightWindowPolicy.Window.LAST_24_HOURS
            if (showOverlay(subsId, groupKey, message, cooldown, eventKind, source)) {
                val occurredAt = System.currentTimeMillis()
                recordMountedAttempt(
                    intent = intent,
                    eventKey = eventKey,
                    eventKind = eventKind,
                    subjectId = subjectId,
                    subjectLabel = subjectLabel,
                    matchedAt = matchedAt,
                    occurredAt = occurredAt,
                )
            }
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun recordMountedAttempt(
        intent: Intent?,
        eventKey: String,
        eventKind: Int,
        subjectId: String,
        subjectLabel: String,
        matchedAt: Long,
        occurredAt: Long,
    ) {
        val selectorSnapshot = intent?.selectorSnapshot(eventKind)
        val pending = MountedInterceptRecorder.Pending(
            recordToken = intent?.getStringExtra(EXTRA_RECORD_TOKEN).orEmpty()
                .ifBlank { "$eventKey:${matchedAt.takeIf { it > 0L } ?: occurredAt}" },
            eventKey = eventKey,
            eventKind = eventKind,
            subjectId = subjectId,
            subjectLabel = subjectLabel,
            selectorSnapshot = selectorSnapshot,
        )
        // Persist independently of the overlay lifecycle so an immediate exit cannot cancel
        // the event that was already accepted by the window manager.
        appScope.launch(Dispatchers.IO) {
            val result = mountedInterceptRecorder.recordMounted(
                pending = pending,
                mounted = true,
                occurredAt = occurredAt,
            )
            withContext(Dispatchers.Main) {
                val insight = result.intervalInsight
                if (result.intervalSucceeded && insight != null) {
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
                } else {
                    insightSamples = emptyList()
                    currentEventId = null
                    currentGapMs = null
                    elapsedState = SelfControlElapsedPolicy.ElapsedState.Unavailable
                }
            }
        }
    }

    private fun showOverlay(
        subsId: Long,
        groupKey: Int,
        message: String,
        cooldown: Int,
        eventKind: Int,
        source: InterceptionSourcePresentation,
    ): Boolean {
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
                        samples = insightSamples,
                        insightAnchorAt = insightAnchorAt,
                        currentReference = currentEventId?.let {
                            SelfControlInsightCurrentReference(gapMs = currentGapMs, eventId = it)
                        },
                        nowEpochMs = insightAnchorAt,
                        selectedWindow = selectedWindow,
                        onWindowSelected = { selectedWindow = it },
                        source = source,
                        onContinue = {
                            if (eventKind == SelfControlAttempt.KIND_SELECTOR_INTERCEPT) {
                                InterceptUtils.setAllowed(subsId, groupKey, cooldown)
                            }
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
            when (eventKind) {
                SelfControlAttempt.KIND_URL_INTERCEPT -> UrlBlockerEngine.clearCooldown()
                SelfControlAttempt.KIND_SELECTOR_INTERCEPT ->
                    A11yRuleEngine.onInterceptOverlayMountFailed()
            }
            stopSelf()
        }
        return mounted
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun Intent.selectorSnapshot(eventKind: Int): SelectorRuleSnapshot? {
        if (eventKind != SelfControlAttempt.KIND_SELECTOR_INTERCEPT) return null
        val ruleKey = if (getBooleanExtra(EXTRA_SELECTOR_RULE_KEY_PRESENT, false)) {
            getIntExtra(EXTRA_SELECTOR_RULE_KEY, 0)
        } else {
            null
        }
        return SelectorRuleSnapshot(
            subsId = getLongExtra(EXTRA_SUBS_ID, -1L),
            subsVersion = getIntExtra(EXTRA_SELECTOR_SUBS_VERSION, 0),
            appId = getStringExtra(EXTRA_SELECTOR_APP_ID).orEmpty(),
            activityId = getStringExtra(EXTRA_SELECTOR_ACTIVITY_ID),
            groupType = getIntExtra(EXTRA_SELECTOR_GROUP_TYPE, -1),
            groupKey = getIntExtra(EXTRA_SELECTOR_GROUP_KEY, -1),
            ruleIndex = getIntExtra(EXTRA_SELECTOR_RULE_INDEX, -1),
            ruleKey = ruleKey,
            ruleName = getStringExtra(EXTRA_SELECTOR_RULE_NAME),
            groupName = getStringExtra(EXTRA_SELECTOR_GROUP_NAME),
            subscriptionName = getStringExtra(EXTRA_SELECTOR_SUBS_NAME),
            matchedAt = getLongExtra(EXTRA_MATCHED_AT, 0L),
        ).takeIf {
            it.subsId > 0L &&
                it.appId.isNotBlank() &&
                it.groupType in setOf(SubsConfig.AppGroupType, SubsConfig.GlobalGroupType) &&
                it.groupKey >= 0 &&
                it.ruleIndex >= 0
        }
    }

    private fun Intent.interceptionSource(eventKind: Int): InterceptionSourcePresentation? {
        return when (eventKind) {
            SelfControlAttempt.KIND_SELECTOR_INTERCEPT ->
                selectorSnapshot(eventKind)?.let(InterceptionSourcePresentation::selector)
            SelfControlAttempt.KIND_URL_INTERCEPT -> {
                val ruleId = getLongExtra(EXTRA_URL_RULE_ID, -1L)
                if (hasExtra(EXTRA_URL_RULE_ID) &&
                    getLongExtra(EXTRA_SUBS_ID, -1L) == URL_SUBS_ID &&
                    getIntExtra(EXTRA_GROUP_KEY, -1) == URL_GROUP_KEY &&
                    ruleId >= 0L &&
                    getStringExtra(EXTRA_SUBJECT_ID) == ruleId.toString() &&
                    getStringExtra(EXTRA_EVENT_KEY) == SelfControlElapsedPolicy.urlInterceptEventKey(ruleId)
                ) {
                    InterceptionSourcePresentation.url(
                        ruleId = ruleId,
                        ruleName = getStringExtra(EXTRA_URL_RULE_NAME),
                    )
                } else null
            }
            else -> null
        }
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
    samples: List<SelfControlInsightWindowPolicy.IntervalSample> = emptyList(),
    insightAnchorAt: Long? = null,
    currentReference: SelfControlInsightCurrentReference? = null,
    nowEpochMs: Long? = null,
    selectedWindow: SelfControlInsightWindowPolicy.Window =
        SelfControlInsightWindowPolicy.Window.LAST_24_HOURS,
    onWindowSelected: (SelfControlInsightWindowPolicy.Window) -> Unit = {},
    source: InterceptionSourcePresentation = InterceptionSourcePresentation.unknown(),
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

            InterceptionSourceCard(
                presentation = source,
                modifier = Modifier.padding(top = 24.dp),
            )

            SelfControlElapsedCard(
                context = SelfControlElapsedPolicy.Context.RULE_TRIGGER,
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
                Text("算了 (退出) ${timeLeft}s")
            }
        }
    }
}
