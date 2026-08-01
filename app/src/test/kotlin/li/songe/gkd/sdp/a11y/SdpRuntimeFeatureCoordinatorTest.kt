package li.songe.gkd.sdp.a11y

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking
import li.songe.gkd.sdp.util.AutomatorModeOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SdpRuntimeFeatureCoordinatorTest {
    private val jobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        jobs.forEach(Job::cancel)
    }

    @Test
    fun a11yOwnerReceivesCurrentForegroundAppOnce() = runBlocking {
        val foreground = MutableStateFlow("com.example.reader")
        val seen = mutableListOf<String>()
        val coordinator = coordinator(foreground) {
            onAppChanged = { appId, _ -> seen += appId }
        }

        coordinator.attach("a11y", AutomatorModeOption.A11yMode)
        yield()

        assertEquals(listOf("com.example.reader"), seen)
    }

    @Test
    fun automationOwnerReceivesCurrentForegroundAppOnce() = runBlocking {
        val foreground = MutableStateFlow("com.example.video")
        val seen = mutableListOf<String>()
        val coordinator = coordinator(foreground) {
            onAppChanged = { appId, _ -> seen += appId }
        }

        coordinator.attach("automation", AutomatorModeOption.AutomationMode)
        yield()

        assertEquals(listOf("com.example.video"), seen)
    }

    @Test
    fun latestAttachedOwnerWinsDuringModeHandoff() = runBlocking {
        val foreground = MutableStateFlow("com.example.reader")
        val seen = mutableListOf<String>()
        val coordinator = coordinator(foreground) {
            onAppChanged = { appId, owner -> seen += "${owner.mode}:$appId" }
        }

        val a11y = coordinator.attach("a11y", AutomatorModeOption.A11yMode)
        val automation = coordinator.attach("automation", AutomatorModeOption.AutomationMode)
        foreground.value = "com.example.video"
        yield()

        assertEquals(
            listOf(
                "无障碍:com.example.reader",
                "自动化:com.example.reader",
                "自动化:com.example.video",
            ),
            seen,
        )
        assertTrue(coordinator.isCurrent(automation))
        assertEquals(false, coordinator.isCurrent(a11y))
    }

    @Test
    fun staleOwnerRawEventsAreIgnored() = runBlocking {
        val foreground = MutableStateFlow("com.example.reader")
        val events = AtomicInteger(0)
        val coordinator = coordinator(foreground) {
            onAccessibilityEvent = { _, _ -> events.incrementAndGet() }
        }

        val oldOwner = coordinator.attach("a11y", AutomatorModeOption.A11yMode)
        coordinator.attach("automation", AutomatorModeOption.AutomationMode)
        coordinator.onAccessibilityEvent(oldOwner, AccessibilityEvent.obtain())
        yield()

        assertEquals(0, events.get())
    }

    @Test
    fun detachingOldOwnerDoesNotClearNewOwner() = runBlocking {
        val foreground = MutableStateFlow("com.example.reader")
        val seen = mutableListOf<String>()
        val coordinator = coordinator(foreground) {
            onAppChanged = { appId, owner -> seen += "${owner.mode}:$appId" }
        }

        val oldOwner = coordinator.attach("a11y", AutomatorModeOption.A11yMode)
        val newOwner = coordinator.attach("automation", AutomatorModeOption.AutomationMode)
        coordinator.detach(oldOwner)
        foreground.value = "com.example.video"
        yield()

        assertTrue(coordinator.isCurrent(newOwner))
        assertEquals("自动化:com.example.video", seen.last())
    }

    @Test
    fun attachingNewOwnerReconcilesCurrentAppWithoutDuplicateOldDispatch() = runBlocking {
        val foreground = MutableStateFlow("com.example.reader")
        val seen = mutableListOf<String>()
        val coordinator = coordinator(foreground) {
            onAppChanged = { appId, owner -> seen += "${owner.mode}:$appId" }
        }

        coordinator.attach("a11y", AutomatorModeOption.A11yMode)
        coordinator.attach("automation", AutomatorModeOption.AutomationMode)
        yield()

        assertEquals(2, seen.size)
        assertEquals("自动化:com.example.reader", seen.last())
    }

    @Test
    fun handlerFailureDoesNotCancelFollowingHandlersOrFutureAppChanges() = runBlocking {
        val foreground = MutableStateFlow("com.example.reader")
        val healthy = mutableListOf<String>()
        val failures = AtomicInteger(0)
        val coordinator = SdpRuntimeFeatureCoordinator(
            foregroundApps = foreground,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()).also { jobs += it.coroutineContext[Job]!! },
            handlers = listOf(
                SdpRuntimeFeatureCoordinator.Handler(
                    name = "broken",
                    onAppChanged = { _, _ -> throw IllegalStateException("test") },
                ),
                SdpRuntimeFeatureCoordinator.Handler(
                    name = "healthy",
                    onAppChanged = { appId, _ -> healthy += appId },
                ),
            ),
            onHandlerFailure = { _, _ -> failures.incrementAndGet() },
        )

        coordinator.attach("a11y", AutomatorModeOption.A11yMode)
        foreground.value = "com.example.video"
        yield()

        assertEquals(listOf("com.example.reader", "com.example.video"), healthy)
        assertEquals(2, failures.get())
    }

    private fun coordinator(
        foreground: MutableStateFlow<String>,
        configure: HandlerConfig.() -> Unit,
    ): SdpRuntimeFeatureCoordinator {
        val config = HandlerConfig().apply(configure)
        return SdpRuntimeFeatureCoordinator(
            foregroundApps = foreground,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()).also { jobs += it.coroutineContext[Job]!! },
            handlers = listOf(
                SdpRuntimeFeatureCoordinator.Handler(
                    name = "test",
                    onAppChanged = config.onAppChanged,
                    onAccessibilityEvent = config.onAccessibilityEvent,
                )
            ),
        )
    }

    private class HandlerConfig {
        var onAppChanged: (String, SdpRuntimeFeatureCoordinator.RuntimeOwner) -> Unit = { _, _ -> }
        var onAccessibilityEvent: (AccessibilityEvent, SdpRuntimeFeatureCoordinator.RuntimeOwner) -> Unit = { _, _ -> }
    }
}
