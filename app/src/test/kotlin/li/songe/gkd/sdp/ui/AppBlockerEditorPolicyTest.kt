package li.songe.gkd.sdp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockerEditorPolicyTest {
    @Test
    fun appendOnlyMergeKeepsExistingAppsAndAddsOnlyNewOnes() {
        val resolved = AppBlockerEditorPolicy.resolveGroupApps(
            existingApps = listOf("video.app", "chat.app"),
            pickedApps = listOf("chat.app", "music.app", "reader.app"),
            appendOnly = true,
        )

        assertEquals(
            listOf("video.app", "chat.app", "music.app", "reader.app"),
            resolved,
        )
    }

    @Test
    fun replaceModeUsesOnlyPickedAppsForNewGroupCreation() {
        val resolved = AppBlockerEditorPolicy.resolveGroupApps(
            existingApps = listOf("old.app"),
            pickedApps = listOf("new.app", "new.app", "reader.app"),
            appendOnly = false,
        )

        assertEquals(listOf("new.app", "reader.app"), resolved)
    }

    @Test
    fun sheetDragIsAllowedOnlyWhenScrollableContentIsAtTop() {
        assertTrue(
            AppBlockerEditorPolicy.canDragSheet(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            )
        )
        assertFalse(
            AppBlockerEditorPolicy.canDragSheet(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 12,
            )
        )
        assertFalse(
            AppBlockerEditorPolicy.canDragSheet(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
            )
        )
    }
}
