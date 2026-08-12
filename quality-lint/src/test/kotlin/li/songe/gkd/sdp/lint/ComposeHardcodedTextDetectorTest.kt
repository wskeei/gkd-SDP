package li.songe.gkd.sdp.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import org.junit.Test

class ComposeHardcodedTextDetectorTest : LintDetectorTest() {
    override fun getDetector() = ComposeHardcodedTextDetector()

    override fun getIssues() = listOf(ComposeHardcodedTextDetector.ISSUE)

    private fun stub(): TestFile = kotlin(
        """
        package androidx.compose.material3
        fun Text(text: String, modifier: Any? = null, style: Any? = null, maxLines: Int = 1)
        fun Button(onClick: () -> Unit, modifier: Any? = null, content: () -> Unit)
        fun OutlinedButton(onClick: () -> Unit, modifier: Any? = null, content: () -> Unit)
        fun TextButton(onClick: () -> Unit, modifier: Any? = null, content: () -> Unit)
        fun Icon(imageVector: Any? = null, contentDescription: String? = null)
        fun OutlinedTextField(value: String, onValueChange: (String) -> Unit, label: (() -> Unit)? = null, supportingText: (() -> Unit)? = null, isError: Boolean = false)
        fun Snackbar(message: String)
        fun AlertDialog(onDismissRequest: () -> Unit, title: (() -> Unit)? = null, text: (() -> Unit)? = null, confirmButton: () -> Unit = {})
        """.trimIndent(),
    )

    private fun componentStub(): TestFile = kotlin(
        """
        package li.songe.gkd.sdp.ui.component
        fun SettingItem(title: String, subtitle: String? = null)
        fun InlineMessage(text: String)
        fun PerfCustomIconButton(contentDescription: String? = null, onClick: () -> Unit = {})
        """.trimIndent(),
    )

    @Test
    fun testFlagsTextLiteral() {
        lint().files(
            stub(),
            kotlin(
                """
                import androidx.compose.material3.Text
                fun Greeting() {
                    Text("你好世界")
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }

    @Test
    fun testFlagsContentDescriptionLiteral() {
        lint().files(
            stub(),
            kotlin(
                """
                import androidx.compose.material3.Icon
                fun IconRow() {
                    Icon(contentDescription = "关闭")
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }

    @Test
    fun testFlagsCustomComponentTextArguments() {
        lint().files(
            stub(),
            componentStub(),
            kotlin(
                """
                import li.songe.gkd.sdp.ui.component.SettingItem
                import li.songe.gkd.sdp.ui.component.InlineMessage
                import li.songe.gkd.sdp.ui.component.PerfCustomIconButton
                fun Settings() {
                    SettingItem(title = "运行状态", subtitle = "显示当前服务")
                    InlineMessage(text = "请先开启权限")
                    PerfCustomIconButton(contentDescription = "前往设置")
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }

    @Test
    fun testFlagsInterpolatedTemplate() {
        lint().files(
            stub(),
            kotlin(
                """
                import androidx.compose.material3.Text
                fun Count(count: Int) {
                    Text("共 ${'$'}count 条")
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }

    @Test
    fun testFlagsConcatenation() {
        lint().files(
            stub(),
            kotlin(
                """
                import androidx.compose.material3.Text
                fun Label(name: String) {
                    Text("应用 " + name)
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }

    @Test
    fun testAcceptsResourceCalls() {
        lint().files(
            stub(),
            kotlin(
                """
                import androidx.compose.material3.Text
                fun Greeting() {
                    Text(stringResource(1))
                }
                """.trimIndent(),
            ),
        ).run().expectClean()
    }

    @Test
    fun testFlagsNamedTextArgumentAndIgnoresLocalStrings() {
        lint().files(
            stub(),
            kotlin(
                """
                import androidx.compose.material3.Text
                fun Keys() {
                    Text(text = "匹配规则")
                    val key = "status_section"
                    val regex = "[a-z]+"
                    val route = "gkd://settings"
                    val code = "db_001"
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }

    @Test
    fun testFlagsToastAndNotificationText() {
        lint().files(
            kotlin(
                """
                import android.widget.Toast
                fun show(c: android.content.Context) {
                    Toast.makeText(c, "保存失败", Toast.LENGTH_SHORT).show()
                }
                """.trimIndent(),
            ),
            kotlin(
                """
                import android.app.Notification
                fun build(b: Notification.Builder): Notification.Builder {
                    return b.setContentTitle("使用提醒").setContentText("还有 5 分钟")
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }

    @Test
    fun testFlagsDialogTitleAndMessage() {
        lint().files(
            stub(),
            kotlin(
                """
                import androidx.compose.material3.AlertDialog
                import androidx.compose.material3.Text
                fun Confirm() {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("删除确认") },
                        text = { Text("此操作不可恢复") },
                        confirmButton = {},
                    )
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }

    @Test
    fun testFlagsCustomListAndSemanticsLabels() {
        lint().files(
            componentStub(),
            kotlin(
                """
                package li.songe.gkd.sdp.ui
                import li.songe.gkd.sdp.ui.component.SettingItem
                fun RequiredTextItem(text: String)
                fun ListItems() {
                    RequiredTextItem(text = "切换服务会有短暂卡顿")
                    SettingItem(title = "运行状态", onClickLabel = "进入运行状态")
                }
                """.trimIndent(),
            ),
        ).run().expectContains("Hardcoded user-visible text")
    }
}
