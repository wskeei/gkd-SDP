package li.songe.gkd.sdp.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Issue

class SdpIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        ComposeHardcodedTextDetector.ISSUE,
    )
}
