package li.songe.gkd.sdp.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class SdpIssueRegistry : IssueRegistry() {
    override val api: Int = CURRENT_API

    override val issues: List<Issue> = listOf(
        ComposeHardcodedTextDetector.ISSUE,
    )
}
