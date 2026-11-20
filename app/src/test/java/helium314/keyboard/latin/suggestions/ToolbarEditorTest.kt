// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarKey.*
import helium314.keyboard.latin.utils.toolbarSelectionPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolbarEditorTest {
    @Test
    fun draftPreservesOrderAndDisplaysAvailableToolsAfterSelection() {
        val state = ToolbarEditorState(listOf(UNDO, COPY, PASTE, SETTINGS), listOf(PASTE, UNDO, PASTE, VOICE))
        assertEquals(listOf(PASTE, UNDO, COPY, SETTINGS), state.items)
        assertEquals(listOf(PASTE, UNDO), state.enabled)
        assertFalse(state.isEnabled(VOICE))
    }

    @Test
    fun droppingOrUsingMoveButtonsChangesOnlyEnabledToolOrder() {
        val state = ToolbarEditorState(listOf(COPY, CUT, PASTE, UNDO), listOf(CUT, PASTE, COPY))
        assertTrue(state.move(CUT, COPY))
        assertEquals(listOf(PASTE, COPY, CUT), state.enabled)
        assertEquals(COPY, state.neighbor(CUT, -1))
        assertNull(state.neighbor(CUT, 1))
        assertFalse(state.move(COPY, UNDO))
        assertFalse(state.move(UNDO, PASTE))
        assertFalse(state.move(PASTE, PASTE))
        assertEquals(listOf(PASTE, COPY, CUT), state.enabled)
    }

    @Test
    fun togglingToolsKeepsRemainingOrderAndIgnoresUnavailableTools() {
        val state = ToolbarEditorState(listOf(COPY, CUT, PASTE), listOf(PASTE, COPY))
        state.setEnabled(PASTE, false)
        state.setEnabled(CUT, true)
        state.setEnabled(CUT, true)
        state.setEnabled(VOICE, true)
        assertEquals(listOf(COPY, CUT), state.enabled)
        assertEquals(listOf(COPY, CUT, PASTE), state.items)
    }

    @Test
    fun preferenceRetainsHiddenToolStateAndWritesReorderedSelection() {
        val before = preference(COPY to true, BACKGROUND_GATHERING to true, PASTE to true, UNDO to false)
        val after = toolbarSelectionPreference(
            before,
            listOf(COPY, BACKGROUND_GATHERING, PASTE),
            listOf(COPY, PASTE, UNDO),
            listOf(UNDO, COPY),
        )
        assertEquals(preference(UNDO to true, BACKGROUND_GATHERING to true, COPY to true, PASTE to false), after)
    }

    @Test
    fun unchangedSelectionDoesNotRewritePreferences() {
        val before = preference(UNDO to false, COPY to true, BACKGROUND_GATHERING to true)
        assertEquals(before, toolbarSelectionPreference(before, listOf(COPY, BACKGROUND_GATHERING),
            listOf(UNDO, COPY), listOf(COPY)))
    }

    @Test
    fun pinCapacityMakesRoomForControlsAndSuggestions() {
        assertEquals(0, pinnedToolbarCapacity(180, 88, 132, 36))
        assertEquals(2, pinnedToolbarCapacity(320, 88, 132, 36))
        assertEquals(3, pinnedToolbarCapacity(360, 88, 132, 36))
        assertEquals(4, pinnedToolbarCapacity(800, 88, 132, 36))
        assertEquals(0, pinnedToolbarCapacity(800, 88, 132, 0))
    }

    private fun preference(vararg items: Pair<ToolbarKey, Boolean>) = items.joinToString(Separators.ENTRY) {
        it.first.name + Separators.KV + it.second
    }
}
