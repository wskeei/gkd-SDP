package li.songe.gkd.sdp.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "li.songe.gkd.sdp",
        includeInStartupProfile = false,
    ) {
        val visitedTabs = mutableSetOf<String>()
        pressHome()
        startActivityAndWait()
        waitForAppToBeVisible("li.songe.gkd.sdp")
        waitForStableInActiveWindow()
        acceptTermsIfNeeded()
        clickRequiredText("首页", "Home", visitedTabs = visitedTabs)
        clickRequiredText("订阅", "Subscriptions", visitedTabs = visitedTabs)
        clickRequiredText("应用", "Apps", visitedTabs = visitedTabs)
        clickRequiredText("设置", "Settings", visitedTabs = visitedTabs)
        pressHome()

        val requiredTabs = setOf("首页", "订阅", "应用", "设置")
        check(requiredTabs.all { it in visitedTabs }) {
            "Baseline profile did not visit v2.1.0 tabs: ${requiredTabs - visitedTabs}"
        }
    }

    private fun MacrobenchmarkScope.clickRequiredText(
        vararg labels: String,
        visitedTabs: MutableSet<String>,
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val element = labels.firstNotNullOfOrNull { label ->
            device.findObject(By.text(label))
                ?: device.findObject(By.textContains(label))
                ?: device.findObject(By.desc(label))
                ?: device.findObject(By.descContains(label))
        }
        checkNotNull(element) {
            "Baseline profile target not found: ${labels.joinToString(" / ")}"
        }
        val bounds = element.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        waitForStableInActiveWindow()
        visitedTabs += labels.first()
    }

    private fun MacrobenchmarkScope.acceptTermsIfNeeded() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(5) {
            val button = device.findObject(By.text("同意"))
                ?: device.findObject(By.text("Agree"))
                ?: device.findObject(By.text("I agree"))
                ?: return
            val bounds = button.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            waitForStableInActiveWindow()
        }
    }
}
