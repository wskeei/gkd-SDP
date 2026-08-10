package li.songe.gkd.sdp.ui.share

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceOverlayLifecycleOwnerTest {
    @Test
    fun `overlay owner follows mount and removal states`() {
        val owner = ServiceOverlayLifecycleOwner()
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)

        owner.onViewAdded()
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        owner.onViewRemoved()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun `mount failure destroys an owner that never reached create`() {
        val owner = ServiceOverlayLifecycleOwner()

        owner.onViewRemoved()
        owner.onViewRemoved()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }
}
