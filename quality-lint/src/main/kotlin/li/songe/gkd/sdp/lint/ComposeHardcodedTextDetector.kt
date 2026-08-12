package li.songe.gkd.sdp.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UNamedExpression

/**
 * Rejects hardcoded user-visible strings passed to known UI calls so all
 * product copy lives in string resources (values/zh baseline, values-en).
 *
 * Only the listed call sites are scanned: stable keys, regexes, SQL, routes,
 * error codes and protocol constants never pass through these calls and are
 * therefore exempt by construction.
 */
class ComposeHardcodedTextDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in SCANNED_METHODS && methodName !in UI_CALLS) return
                node.valueArguments.forEachIndexed { index, argument ->
                    if (!isUserTextArgument(methodName, argument, index)) return@forEachIndexed
                    if (argument.isHardcodedText()) {
                        context.report(
                            issue = ISSUE,
                            location = context.getLocation(argument),
                            message = MESSAGE_PREFIX + argument.asSourceString(),
                        )
                    }
                }
            }
        }
    }

    private fun isUserTextArgument(
        methodName: String,
        argument: UExpression,
        index: Int,
    ): Boolean {
        val argName = (argument as? UNamedExpression)?.name
        return when {
            argName != null -> argName in TEXT_ARGUMENT_NAMES && methodName in UI_CALLS
            methodName in UI_CALLS -> true
            methodName == "Text" || methodName == "Button" || methodName == "OutlinedButton" ||
                methodName == "TextButton" || methodName == "FilledTonalButton" ||
                methodName == "Snackbar" || methodName == "toast" ||
                methodName == "setContentTitle" || methodName == "setContentText" -> index == 0
            // Toast.makeText(context, text, duration)
            methodName == "makeText" -> index == 1
            else -> false
        }
    }

    private fun UExpression.isHardcodedText(): Boolean = when (this) {
        is UNamedExpression -> expression.isHardcodedText()
        is ULiteralExpression -> value is String || value is Char
        else -> asSourceString().trimStart('(').trimStart().startsWith("\"")
    }

    companion object {
        private const val MESSAGE_PREFIX =
            "Hardcoded user-visible text; move it to a string resource: "

        private val SCANNED_METHODS = listOf(
            "Text",
            "Button",
            "OutlinedButton",
            "TextButton",
            "FilledTonalButton",
            "TextField",
            "OutlinedTextField",
            "Snackbar",
            "Toast",
            "toast",
            "Icon",
            "setContentTitle",
            "setContentText",
            "AlertDialog",
        )

        private val UI_CALLS = setOf(
            "Text", "Button", "OutlinedButton", "TextButton", "FilledTonalButton",
            "Icon", "Snackbar", "TextSwitch", "SettingItem", "PerfCustomIconButton",
            "TextMenu", "updateDialogOptions", "waitResult", "AppFormField",
            "InlineMessage", "AppConfirmationDialog", "AppMetricCard", "AppActionBar",
            "AppDataChart", "ContentStateBox", "EmptyText", "AppBlockerEmptyText",
            "UrlLockSheet", "TimeRuleEditorSheet", "BrowserListSheet",
            "BrowserEditSheet", "ManualAuthDialog", "ShareLogDlg", "TextDialog",
            "ScaffoldDialog", "FullscreenDialog", "AppPickerDialog", "CopyTextCard",
            "AuthDialog", "AppIcon", "InterceptionSourceCard", "SelfControlElapsedCard",
            "UsageRequestRhythmPresentation", "PreferenceBlock", "SectionCard",
            "CompactInfoRow", "SettingRow", "UrlInGroupRow", "TimeRuleRow",
            "AppGroupCard", "AppGroupCardHeader", "HistoryRow", "DatePickerDialog",
            "PerfTopAppBar", "ClosableTitle", "InputSubsLinkOption",
            "RequiredTextItem", "AuthCard", "RuleItem", "onClick", "onLongClick",
            "PerfSwitch", "SubsItemCard", "SubsSheet", "RuleGroupCard", "RuleGroupState",
            "AnimatedIcon", "TermsAcceptDialog", "UploadOptions", "ShareLogDlg",
            "ReviewSectionCard", "ReviewRankedBarList", "RecentRowsCard", "OverviewCard",
            "MetricBlock", "TrendHeadline", "UsageGuardReviewFilters",
            "SelfControlWindowChart", "UsageGuardRequestContent", "UsageDurationRatioFeedback",
        )

        private val TEXT_ARGUMENT_NAMES = setOf(
            "text",
            "title",
            "subtitle",
            "message",
            "contentDescription",
            "onClickLabel",
            "onLongClickLabel",
            "label",
            "placeholder",
            "supportingText",
            "errorText",
            "stateDescription",
            "windowTitle",
            "titleText",
            "emptyText",
            "confirmText",
            "dismissText",
            "suffix",
            "hint",
            "status",
            "currentLabel",
            "bucketLabel",
            "aggregationLabel",
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "HardcodedText",
            briefDescription = "Hardcoded user-visible text",
            explanation = "User-visible copy must live in string resources so the " +
                "values/zh-CN baseline and values-en stay complete and translatable.",
            category = Category.MESSAGES,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                ComposeHardcodedTextDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
