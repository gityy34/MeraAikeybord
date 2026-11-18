// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.romanurdu

import helium314.keyboard.latin.utils.TextAtCursor
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RomanUrduAdapterTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun suggestions(word: String, allowed: Boolean = true) =
        RomanUrduAdapter.suggestions(context, word, TextAtCursor(word, word.length), -1, allowed)

    @Test fun missingEnglishDoesNotMoveRelatedWordToWrongSlot() {
        val choices = suggestions("khana")
        assertEquals(listOf("khana", "کھانا", "غذا"), choices.map { it.word })
        assertEquals(0, choices.positionOffset(1))
        assertEquals(1, choices.positionOffset(2))
        assertTrue(choices.positionOffset(0) < -1)
    }

    @Test fun restrictedFieldGetsNoDictionaryChoicesOrAutomaticCorrection() {
        val result = suggestions("thik", false)
        assertEquals(listOf("thik"), result.map { it.word })
        assertEquals(false, result.willAutoCorrect)
    }

    @Test fun mismatchedSnapshotAndCursorInsideWordCannotReplaceText() {
        RomanUrduAdapter.load(context)
        assertNull(RomanUrduAdapter.automaticReplacement("thik", TextAtCursor("other", 5)))
        assertNull(RomanUrduAdapter.automaticReplacement("thik", TextAtCursor("thikworld", 4)))
        assertNull(RomanUrduAdapter.automaticReplacement("thik", null))
    }

    @Test fun identifiersAndUrlFragmentsArePreserved() {
        RomanUrduAdapter.load(context)
        for (text in listOf("#thik", "https://thik", "aap@thik", "aap_thik")) {
            assertNull(RomanUrduAdapter.automaticReplacement("thik", TextAtCursor(text, text.length)), text)
        }
        assertNull(RomanUrduAdapter.automaticReplacement("aap", TextAtCursor("aap.com", 3)))
        assertEquals("ٹھیک", RomanUrduAdapter.automaticReplacement("thik", TextAtCursor("thik", 4)))
    }
}
