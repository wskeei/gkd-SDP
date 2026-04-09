package li.songe.gkd.sdp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppBlockerVmGroupEditorTest {
    @Test
    fun appendAppsModeStartsWithEmptyPickerSelectionAndExcludesExistingApps() {
        val config = AppBlockerVm.buildGroupPickerConfig(
            currentApps = listOf("video.app", "chat.app"),
            mode = AppBlockerVm.GroupEditorMode.AppendApps,
        )

        assertEquals(emptyList<String>(), config.initialSelection)
        assertEquals(setOf("video.app", "chat.app"), config.excludedApps)
    }

    @Test
    fun editModeKeepsCurrentSelectionEditable() {
        val config = AppBlockerVm.buildGroupPickerConfig(
            currentApps = listOf("video.app", "chat.app"),
            mode = AppBlockerVm.GroupEditorMode.Edit,
        )

        assertEquals(listOf("video.app", "chat.app"), config.initialSelection)
        assertEquals(emptySet<String>(), config.excludedApps)
    }

    @Test
    fun createModeKeepsSelectionEditable() {
        val config = AppBlockerVm.buildGroupPickerConfig(
            currentApps = listOf("video.app"),
            mode = AppBlockerVm.GroupEditorMode.Create,
        )

        assertEquals(listOf("video.app"), config.initialSelection)
        assertEquals(emptySet<String>(), config.excludedApps)
    }
}
