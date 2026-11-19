// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos
import helium314.keyboard.latin.LatinIME
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class UrduLayoutTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)

    @Test
    fun normalLayerContainsUrduAlphabetOnFourRows() {
        val layout = latinIME.assets.open("layouts/main/urdu.json").bufferedReader().use { it.readText() }
        val params = KeyboardParams().apply {
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET)
        }
        val keys = LayoutParser.parseJsonString(layout)
            .map { row -> row.mapNotNull { it.compute(params)?.label } }

        assertEquals(4, keys.size)
        assertTrue(keys.all { it.size == 10 })
        val labels = keys.flatten().toSet()
        val required = "ا ب پ ت ٹ ث ج چ ح خ د ڈ ذ ر ڑ ز ژ س ش ص ض ط ظ ع غ ف ق ک گ ل م ن ں و ہ ھ ء ی ے آ".split(" ")
        assertTrue(required.all { it in labels }, "Missing Urdu keys: ${required.filterNot { it in labels }}")
    }

    @Test
    fun manualShiftLayerContainsAdditionalUrduForms() {
        val layout = latinIME.assets.open("layouts/main/urdu.json").bufferedReader().use { it.readText() }
        val params = KeyboardParams().apply {
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET_MANUAL_SHIFTED)
        }
        val labels = LayoutParser.parseJsonString(layout)
            .flatMap { row -> row.mapNotNull { it.compute(params)?.label } }
            .toSet()

        val shiftForms = "أ ؤ ئ ۓ ۂ ۃ ة ٰ".split(" ")
        assertTrue(shiftForms.all { it in labels }, "Missing shifted Urdu forms: ${shiftForms.filterNot { it in labels }}")
    }

    @Test
    fun urduPunctuationPopupContainsDiacriticsAndMarks() {
        val infos = LocaleKeyboardInfos(
            latinIME.assets.open("locale_key_texts/ur.txt"),
            Locale("ur", "PK")
        )
        val popup = infos.getPopupKeys("punctuation").orEmpty().toSet()
        val required = "َ ِ ُ ْ ّ ٰ ً ٍ ٌ ء ، ؟ ۔".split(" ")
        assertTrue(required.all { it in popup }, "Missing Urdu punctuation popup entries: ${required.filterNot { it in popup }}")
    }
}

