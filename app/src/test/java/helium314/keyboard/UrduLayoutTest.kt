// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyLabel
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.POPUP_KEYS_LAYOUT
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val required = "ا ب پ ت ٹ ث ج چ ح خ د ڈ ذ ر ڑ ز ژ س ش ص ض ط ظ ع غ ف ق ک گ ل م ن ں و ہ ھ ء ی ے آ".split(" ")
        assertEquals(required.toSet(), keys.flatten().toSet())
        assertEquals("قوعرتےءیہپ", keys[0].joinToString(""))
        assertEquals("اسدفگحجکل", keys[1].take(9).joinToString(""))
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
    fun urduFullStopUsesReadableLabelsAndSingleCodePointDiacritics() {
        val (key, params) = periodKey("urdu", "ur-PK")
        val fullStop = key.toKeyParams(params)
        assertEquals('۔'.code, fullStop.mCode)
        val popups = fullStop.mPopupKeys.orEmpty().associateBy { it.mCode }
        for (mark in "ًٌٍَُِّْٰ") {
            assertEquals("${mark}◌", popups[mark.code]?.mLabel)
        }
        assertTrue("ء،؟۔".all { it.code in popups })
    }

    @Test
    fun otherLayoutsKeepTheirPreviousPeriodPopups() {
        for ((layout, language) in listOf(
            "roman_urdu" to "ur-PK", "roman_urdu_auto" to "ur-PK", "qwerty" to "en-US"
        )) {
            val (key, params) = periodKey(layout, language)
            val codes = key.toKeyParams(params).mPopupKeys.orEmpty().map { it.mCode }
            assertTrue(codes.isNotEmpty(), layout)
            assertTrue("ًٌٍَُِّْٰ".none { it.code in codes }, layout)
        }
    }

    private fun periodKey(layout: String, language: String): Pair<KeyData, KeyboardParams> {
        val subtype = InputMethodSubtypeBuilder()
            .setSubtypeLocale(language.replace('-', '_'))
            .setLanguageTag(language)
            .setSubtypeMode("keyboard")
            .setSubtypeExtraValue("KeyboardLayoutSet=MAIN:$layout")
            .build()
        val layoutParams = KeyboardLayoutSet.Params().apply {
            editorInfo = EditorInfo()
            this.subtype = RichInputMethodSubtype.get(subtype)
        }
        val params = KeyboardParams().apply {
            mId = KeyboardId(KeyboardElement.ALPHABET, layoutParams)
            mPopupKeyOrder.add(POPUP_KEYS_LAYOUT)
        }
        LocaleKeyboardInfos.addLocaleKeyTextsToParams(latinIME, params, LocaleKeyboardInfos.POPUP_KEYS_NORMAL)
        val period = LayoutParser.parseLayout(LayoutType.FUNCTIONAL, params, latinIME)
            .flatten().single { it.label == KeyLabel.PERIOD }
        return period to params
    }
}
