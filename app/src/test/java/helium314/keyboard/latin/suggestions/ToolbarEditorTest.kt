// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputStyle
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.dictionary.Dictionary
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
    fun draftPreservesOrderAndKeepsUnpinnedToolsInTheGrid() {
        val state = ToolbarEditorState(listOf(UNDO, COPY, PASTE, SETTINGS), listOf(PASTE, UNDO, PASTE, VOICE))
        assertEquals(listOf(COPY, SETTINGS), state.tools)
        assertEquals(listOf(PASTE, UNDO), state.enabled)
        assertFalse(state.isEnabled(VOICE))
    }

    @Test
    fun droppingOrUsingMoveButtonsChangesOnlyEnabledToolOrder() {
        val state = ToolbarEditorState(listOf(COPY, CUT, PASTE, UNDO), listOf(CUT, PASTE, COPY))
        assertTrue(state.dropOnPins(CUT, 2))
        assertEquals(listOf(PASTE, COPY, CUT), state.enabled)
        assertEquals(1, state.neighboringPinSlot(CUT, -1))
        assertNull(state.neighboringPinSlot(CUT, 1))
        assertFalse(state.dropOnPins(COPY, 1))
        assertFalse(state.dropOnPins(PASTE, 0))
        assertFalse(state.dropOnPins(VOICE, 0))
        assertEquals(listOf(PASTE, COPY, CUT), state.enabled)
    }

    @Test
    fun togglingToolsKeepsRemainingOrderAndIgnoresUnavailableTools() {
        val state = ToolbarEditorState(listOf(COPY, CUT, PASTE), listOf(PASTE, COPY))
        assertTrue(state.dropOnTools(PASTE))
        assertTrue(state.dropOnPins(CUT, null))
        assertFalse(state.dropOnPins(CUT, null))
        assertFalse(state.dropOnPins(VOICE, null))
        assertEquals(listOf(COPY, CUT), state.enabled)
        assertEquals(listOf(PASTE), state.tools)
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
        assertEquals(16, pinnedToolbarCapacity(800, 88, 132, 36))
        assertEquals(0, pinnedToolbarCapacity(800, 88, 132, 0))
    }

    @Test
    fun draggingFromGridInsertsBeforeTargetAndCanAppendToEmptyRow() {
        val state = ToolbarEditorState(listOf(COPY, PASTE, UNDO), emptyList())
        assertTrue(state.dropOnPins(COPY, null))
        assertTrue(state.dropOnPins(PASTE, 0))
        assertTrue(state.dropOnPins(UNDO, null))
        assertEquals(listOf(PASTE, COPY, UNDO), state.enabled)
    }

    @Test
    fun draggingPinnedToolsToGridAndBackKeepsOneCopy() {
        val state = ToolbarEditorState(listOf(COPY, PASTE, UNDO), listOf(COPY, PASTE, UNDO))
        assertTrue(state.dropOnPins(COPY, null))
        assertEquals(listOf(PASTE, UNDO, COPY), state.enabled)
        assertTrue(state.dropOnTools(UNDO))
        assertFalse(state.dropOnTools(UNDO))
        assertFalse(state.dropOnPins(VOICE, null))
        assertTrue(state.dropOnPins(UNDO, 1))
        assertEquals(listOf(PASTE, UNDO, COPY), state.enabled)
    }

    @Test
    fun gridToolsMoveInBothDirectionsAndRestoreInTheSavedOrder() {
        val available = listOf(COPY, CUT, PASTE, UNDO, VOICE)
        val state = ToolbarEditorState(available, listOf(VOICE))
        assertTrue(state.dropOnTools(COPY, UNDO))
        assertEquals(listOf(CUT, PASTE, UNDO, COPY), state.tools)
        assertTrue(state.dropOnTools(UNDO, CUT))
        assertTrue(state.dropOnTools(VOICE, PASTE))
        assertEquals(listOf(UNDO, CUT, VOICE, PASTE, COPY), state.tools)
        assertEquals(CUT, state.neighboringTool(VOICE, -1))
        val reopened = ToolbarEditorState(available, state.enabled, state.tools)
        assertEquals(state.tools, reopened.tools)
    }

    @Test
    fun lockedShortcutKeepsItsSlotUntilUnlocked() {
        val available = listOf(PASTE, VOICE, UNDO)
        val state = ToolbarEditorState(available, available)
        assertTrue(state.setLocked(VOICE, true))
        assertFalse(state.dropOnPins(VOICE, 0))
        assertFalse(state.dropOnTools(VOICE))
        assertFalse(state.dropOnPins(PASTE, 1))
        assertTrue(state.dropOnPins(PASTE, 2))
        assertEquals(listOf(UNDO, VOICE, PASTE), state.pinSlots)
        assertTrue(state.dropOnTools(UNDO))
        assertTrue(state.dropOnTools(PASTE))
        assertEquals(listOf(null, VOICE), state.pinSlots)
        assertEquals(mapOf(VOICE to 1), state.pinLocks)
        val reopened = ToolbarEditorState(available, state.enabled, state.tools, state.pinLocks)
        assertEquals(listOf(null, VOICE), reopened.pinSlots)
        assertTrue(reopened.setLocked(VOICE, false))
        assertTrue(reopened.dropOnTools(VOICE, UNDO))
        assertEquals(listOf(VOICE, UNDO, PASTE), reopened.tools)
    }

    @Test
    fun gridPaginatesTwoColumnsWithinAvailableKeyboardHeight() {
        assertEquals(2, toolbarToolsPerPage(0, 54))
        assertEquals(2, toolbarToolsPerPage(100, 54))
        assertEquals(4, toolbarToolsPerPage(108, 54))
        assertEquals(6, toolbarToolsPerPage(216, 54))
        assertEquals(2, toolbarToolsPerPage(300, 0))
    }

    @Test
    fun appearanceShortcutIsBlockedWhenDeviceIsLocked() {
        assertTrue(KeyCode.isIsBlockedWhenLocked(KeyCode.APPEARANCE_SETTINGS))
    }

    @Test
    fun aPinnedMicrophoneDoesNotReplaceIdleToolsOrDuplicateTheVoiceButton() {
        val regular = listOf(VOICE, CLIPBOARD, EMOJI, UNDO, SETTINGS, CLIPBOARD)
        assertEquals(listOf(CLIPBOARD, EMOJI, UNDO, SETTINGS), idleToolbarSlots(listOf(VOICE), mapOf(VOICE to 0), regular))
    }

    @Test
    fun idleToolbarUsesChosenShortcutsInTheirSavedSlots() {
        val pins = listOf(VOICE, PASTE, UNDO)
        assertEquals(listOf(PASTE, null, UNDO), idleToolbarSlots(pins, mapOf(UNDO to 3), listOf(SETTINGS)))
    }

    @Test
    fun idleToolsAppearOnOpeningAndAfterEitherFullStop() {
        val punctuation = PunctuationSuggestions.newPunctuationSuggestions(arrayOf("!", "?"))
        for (beforeCursor in listOf(Constants.NOT_A_CODE, Constants.CODE_ENTER, '.'.code, '۔'.code)) {
            assertTrue(isIdleToolbarBoundary(beforeCursor))
            assertTrue(shouldShowIdleToolbar(punctuation, false, isIdleToolbarBoundary(beforeCursor), false))
        }
        assertTrue(shouldShowIdleToolbar(SuggestedWords.emptyInstance, false, true, false))
        assertTrue(shouldShowIdleToolbar(suggestions(InputStyle.PREDICTION), false, true, false))
        assertTrue(shouldShowIdleToolbar(suggestions(InputStyle.BEGINNING_OF_SENTENCE_PREDICTION), false, false, false))
        assertFalse(isIdleToolbarBoundary(','.code))
        assertFalse(shouldShowIdleToolbar(punctuation, false, false, false))
    }

    @Test
    fun composingAndGenuineSuggestionsKeepTheCenterAvailableForSuggestions() {
        assertFalse(shouldShowIdleToolbar(SuggestedWords.emptyInstance, true, true, false))
        assertFalse(shouldShowIdleToolbar(suggestions(InputStyle.TYPING), true, false, false))
        assertFalse(shouldShowIdleToolbar(suggestions(InputStyle.RECORRECTION), false, false, false))
        assertFalse(shouldShowIdleToolbar(suggestions(InputStyle.PREDICTION), false, false, false))
        assertFalse(shouldShowIdleToolbar(suggestions(InputStyle.APPLICATION_SPECIFIED), false, true, false))
        assertFalse(shouldShowIdleToolbar(SuggestedWords.emptyInstance, false, true, true))
    }

    private fun suggestions(inputStyle: InputStyle): SuggestedWords {
        val word = SuggestedWordInfo(
            "word", null, 0, SuggestedWordInfo.KIND_CORRECTION, Dictionary.DICTIONARY_HARDCODED,
            SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE,
        )
        return SuggestedWords(
            listOf(word), null, null, false, false, false, inputStyle, SuggestedWords.NOT_A_SEQUENCE_NUMBER,
        )
    }

    private fun preference(vararg items: Pair<ToolbarKey, Boolean>) = items.joinToString(Separators.ENTRY) {
        it.first.name + Separators.KV + it.second
    }
}
