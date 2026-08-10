package li.songe.gkd.sdp.ui.share

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Lifecycle owner for a Compose tree mounted directly in a Service window.
 * The owner is deliberately tied to the window, not to the Service process:
 * removing one overlay stops its collectors immediately while the Service can
 * continue serving another overlay.
 */
class ServiceOverlayLifecycleOwner : LifecycleOwner {
    // Service callbacks are serialized by the WindowManager/main service thread;
    // createUnsafe also keeps the owner testable in the JVM environment where
    // android.os.Looper is not present.
    private val registry = LifecycleRegistry.createUnsafe(this)

    init {
        registry.currentState = Lifecycle.State.INITIALIZED
    }

    override val lifecycle: Lifecycle
        get() = registry

    fun onViewAdded() {
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        if (registry.currentState == Lifecycle.State.CREATED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
    }

    fun onViewRemoved() {
        when (registry.currentState) {
            Lifecycle.State.STARTED, Lifecycle.State.RESUMED -> {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }

            Lifecycle.State.CREATED -> registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            Lifecycle.State.INITIALIZED -> {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
            else -> Unit
        }
    }
}
