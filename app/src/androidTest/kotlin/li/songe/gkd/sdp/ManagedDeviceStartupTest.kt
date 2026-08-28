package li.songe.gkd.sdp

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagedDeviceStartupTest {
    @Test
    fun launcherActivityStartsOnSupportedApiLevels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val intent = targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)
            ?: error("No launcher activity registered for ${targetContext.packageName}")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        targetContext.startActivity(intent)
        assertTrue(
            "Launcher activity did not become visible",
            device.wait(Until.hasObject(By.pkg(targetContext.packageName)), 10_000L),
        )

        assertEquals(targetContext.packageName, device.currentPackageName)
    }
}
