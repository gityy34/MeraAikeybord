// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RomanUrduCombinerTest {
    private val combiner = RomanUrduCombiner()
    private fun key(cp: Int) = Event.createSoftwareKeypressEvent(cp, 0, 0, 0, false)
    private fun type(word: String) = word.forEach {
        assertTrue(combiner.processEvent(arrayListOf(), key(it.code)).isConsumed)
    }

    @Test fun keepsRomanVisibleUntilSelectionOrBoundary() {
        type("aap")
        assertEquals("aap", combiner.combiningStateFeedback)
        val space = key(' '.code)
        val commit = combiner.processEvent(arrayListOf(), space)
        assertEquals("aap", commit.textToCommit)
        assertSame(space, commit.nextEvent)
        assertEquals("", combiner.combiningStateFeedback)
    }

    @Test fun backspaceRestoresPreviousRomanSpelling() {
        type("sh")
        assertEquals("sh", combiner.combiningStateFeedback)
        assertTrue(combiner.processEvent(arrayListOf(), key(KeyCode.DELETE)).isConsumed)
        assertEquals("s", combiner.combiningStateFeedback)
        val lastDelete = combiner.processEvent(arrayListOf(), key(KeyCode.DELETE))
        assertEquals(' '.code, lastDelete.codePoint)
        assertEquals(KeyCode.DELETE, lastDelete.nextEvent?.keyCode)
        assertEquals("", combiner.combiningStateFeedback)
        val delete = key(KeyCode.DELETE)
        assertSame(delete, combiner.processEvent(arrayListOf(), delete))
    }

    @Test fun punctuationDigitsEmojiAndEnterFollowTheCommittedWord() {
        for (cp in listOf('.'.code, '1'.code, 0x1F642, '\n'.code, KeyCode.SHIFT_ENTER)) {
            type("hain")
            val separator = key(cp)
            val commit = combiner.processEvent(arrayListOf(), separator)
            assertEquals("hain", commit.textToCommit)
            assertSame(separator, commit.nextEvent)
            assertEquals("", combiner.combiningStateFeedback)
        }
    }

    @Test fun shiftDoesNotFinishWordAndResetClearsSpelling() {
        type("aa")
        val shift = key(KeyCode.SHIFT)
        assertSame(shift, combiner.processEvent(arrayListOf(), shift))
        type("p")
        assertEquals("aap", combiner.combiningStateFeedback)
        combiner.reset()
        type("hai")
        assertEquals("hai", combiner.combiningStateFeedback)
    }
}
