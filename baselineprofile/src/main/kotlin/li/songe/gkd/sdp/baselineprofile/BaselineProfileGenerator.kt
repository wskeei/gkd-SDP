package li.songe.gkd.sdp.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
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
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        waitForAppToBeVisible("li.songe.gkd.sdp")
        waitForStableInActiveWindow()
        clickText("自律")
        clickText("数字自律复盘")
        pressBack()
        clickText("规则")
        clickText("应用规则")
        clickText("设置")
        clickText("运行能力")
        pressBack()
        clickText("隐私与数据")
        pressHome()
    }

    private fun MacrobenchmarkScope.clickText(label: String) {
        onElementOrNull(5_000) {
            text == label || contentDescription == label
        }?.click()
    }
}
