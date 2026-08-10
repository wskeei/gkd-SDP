package li.songe.gkd.sdp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Semantic-node smoke test for the core accessibility presentation.
 *
 * Full per-page semantic assertions run on managed devices (Task 28); this
 * suite keeps the shared semantic contracts executable in any environment.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilitySmokeTest {
    @Test
    fun sharedSemanticContractsAreDeclared() {
        check(li.songe.gkd.sdp.ui.style.DimensionTokens.MinTouchTarget.value >= 48f)
        check(li.songe.gkd.sdp.ui.component.InlineMessageKind.entries.size == 4)
        check(
            li.songe.gkd.sdp.ui.component.ContentStatePolicy.LoadingDelayMs == 400,
        )
        check(
            li.songe.gkd.sdp.ui.component.FullDeletionPolicy.isConfirmed("删除全部数据"),
        )
    }
}
