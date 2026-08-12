package li.songe.gkd.sdp.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        // Keep the size gate comparable to the release baseline: baseline.prof still
        // ships, while R8 startup-dex partitioning stays disabled.
        includeInStartupProfile = false,
    ) {
        val visitedSteps = mutableSetOf<String>()
        pressHome()
        preAcceptTermsIfNeeded()
        startActivityAndWait()
        waitForAppToBeVisible("li.songe.gkd.sdp")
        waitForStableInActiveWindow()
        dismissOnboardingIfNeeded()
        clickRequiredText("自律", "Self-control", visitedSteps = visitedSteps)
        clickRequiredText("数字自律复盘", "Self-control review", visitedSteps = visitedSteps)
        pressBack()
        clickRequiredText("规则", "Rules", visitedSteps = visitedSteps)
        clickRequiredText("应用规则", "App rules", visitedSteps = visitedSteps)
        clickRequiredText("设置", "Settings", visitedSteps = visitedSteps)
        pressHome()

        val requiredSteps = setOf("自律", "数字自律复盘", "规则", "应用规则", "设置")
        check(requiredSteps.all { it in visitedSteps }) {
            "Baseline profile did not complete required pages: ${requiredSteps - visitedSteps}"
        }
    }

    private fun MacrobenchmarkScope.clickRequiredText(
        vararg labels: String,
        visitedSteps: MutableSet<String>,
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
        visitedSteps += labels.first()
    }

    private fun MacrobenchmarkScope.dismissOnboardingIfNeeded() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(5) {
            val button = device.findObject(By.text("Agree"))
                ?: device.findObject(By.text("I agree"))
                ?: device.findObject(By.text("同意"))
                ?: device.findObject(By.text("同意并开启"))
                ?: return
            val bounds = button.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            waitForStableInActiveWindow()
        }
    }

    private fun MacrobenchmarkScope.preAcceptTermsIfNeeded() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand(
            "run-as li.songe.gkd.sdp sh -c 'mkdir -p files/store && echo true > files/store/terms_accepted.txt'",
        )
    }

}
