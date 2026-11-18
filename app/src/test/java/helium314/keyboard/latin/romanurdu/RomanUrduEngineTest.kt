// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.romanurdu

import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RomanUrduEngineTest {
    private val data = listOf(File("src/main/assets/roman_urdu"), File("app/src/main/assets/roman_urdu"))
        .first { it.isDirectory }
    private val lexicon = File(data, "lexicon.tsv").reader(Charsets.UTF_8).use(RomanUrduLexicon::load)
    private val english = File(data, "english_protected.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith('#') }.toSet()
    private val engine = RomanUrduEngine(lexicon, english)

    @Test fun requiredSpellingVariations() {
        val groups = mapOf(
            "ٹھیک" to listOf("thik", "thek", "theek", "teek", "tik"),
            "ہے" to listOf("hai", "hey", "hay"),
            "کیا" to listOf("kya", "kia"),
            "میں" to listOf("main", "mein", "mayn"),
            "اچھا" to listOf("acha", "achha", "accha"),
            "کھانا" to listOf("khana", "khaana")
        )
        for ((urdu, forms) in groups) for (form in forms) {
            assertEquals(urdu, engine.suggest(form, "aap ", " hoon").matches.first().entry.urdu, form)
        }
    }

    @Test fun requiredSentencesInExplicitAutomaticMode() {
        val sentences = mapOf(
            "main theek hoon" to "میں ٹھیک ہوں",
            "main ghar ja raha hoon" to "میں گھر جا رہا ہوں",
            "aap kaisay hain" to "آپ کیسے ہیں",
            "mujhe acha lagta hai" to "مجھے اچھا لگتا ہے",
            "kya aap ghar par hain" to "کیا آپ گھر پر ہیں"
        )
        for ((roman, urdu) in sentences) assertEquals(urdu, engine.convertSentence(roman, true), roman)
    }

    @Test fun manualModeIsTheDefaultAndPreservesOriginal() {
        val original = "main  ghar ja raha hoon! 123 🙂"
        assertEquals(original, engine.convertSentence(original))
        assertEquals("ThEeK", engine.suggest("ThEeK").original)
    }

    @Test fun threeChoicesHaveStableSemanticOrder() {
        for ((roman, expected) in mapOf(
            "thik" to listOf("okay", "ٹھیک", "درست"),
            "acha" to listOf("good", "اچھا", "بہتر"),
            "ghar" to listOf("home", "گھر", "مکان")
        )) {
            val result = engine.suggest(roman)
            assertEquals(expected, result.choices.map { it.text })
            assertEquals(listOf(RomanUrduChoiceKind.ENGLISH, RomanUrduChoiceKind.URDU,
                RomanUrduChoiceKind.RELATED), result.choices.map { it.kind })
            assertEquals(result, engine.suggest(roman))
        }
    }

    @Test fun noInventedEnglishMeaningOrRelatedWord() {
        val choices = engine.suggest("aap").choices
        assertEquals(listOf("آپ"), choices.map { it.text })
    }

    @Test fun contextRanksAmbiguousWords() {
        assertEquals("بس", engine.suggest("bas", after = " karo").matches.first().entry.urdu)
        assertEquals("باس", engine.suggest("bas", before = "yeh buri ").matches.first().entry.urdu)
        assertNull(engine.suggest("bas").automaticReplacement)
        assertTrue(engine.suggest("bas").choices.none { it.kind == RomanUrduChoiceKind.ENGLISH })
    }

    @Test fun ambiguousEnglishWordsNeedRomanContext() {
        assertTrue(engine.suggest("main", "this is my ", " road").choices.isEmpty())
        assertEquals("میں", engine.suggest("main", after = " ghar ja raha hoon").automaticReplacement)
        assertEquals("میں", engine.suggest("main", after = " گھر جا رہا ہوں").automaticReplacement)
        assertTrue(engine.suggest("main", "aap ghar. this is my ", " road").choices.isEmpty())
        assertNull(engine.suggest("mein").automaticReplacement)
    }

    @Test fun englishOnlyAndTechnicalSentencesStayUnchanged() {
        for (sentence in listOf("hello world", "this is my main road", "please send your file",
            "public static void main", "OpenAI Google GitHub Android Kotlin Java Python API")) {
            assertEquals(sentence, engine.convertSentence(sentence, true))
        }
    }

    @Test fun mixedTextPreservesEnglishAndBrandNames() {
        assertEquals("میں گھر جا رہا ہوں OpenAI", engine.convertSentence("main ghar ja raha hoon OpenAI", true))
        assertEquals("Google", engine.convertSentence("Google", true))
        assertTrue(engine.suggest("keyboard").choices.isEmpty())
    }

    @Test fun unknownWordsNamesAndLowConfidenceAreNotAutoConverted() {
        for (word in listOf("zxqv", "Ali", "Zainab", "Rafay", "Fabrikam")) {
            assertNull(engine.suggest(word).automaticReplacement, word)
            assertEquals(word, engine.convertSentence(word, true))
        }
        val fuzzy = engine.suggest("mujhhe")
        assertNotNull(fuzzy.matches.firstOrNull())
        assertNull(fuzzy.automaticReplacement)
        assertTrue(fuzzy.choices.none { it.kind == RomanUrduChoiceKind.ENGLISH })
    }

    @Test fun punctuationNumbersMultipleSpacesAndEmojiArePreserved() {
        assertEquals("میں  ٹھیک ہوں! 123 🙂", engine.convertSentence("main  theek hoon! 123 🙂", true))
        for (text in listOf("aap123", "user@aap.com", "https://aap.pk", "aap_ghar", "#aap"))
            assertEquals(text, engine.convertSentence(text, true))
        assertTrue(engine.suggest("123").choices.isEmpty())
        assertTrue(engine.suggest("aap@ghar").choices.isEmpty())
    }

    @Test fun normalizationHandlesRepeatedLettersAndLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("tik", RomanUrduNormalizer.normalize("TIK"))
            assertEquals("ٹھیک", engine.suggest("THEEK").matches.first().entry.urdu)
            assertEquals("khana", RomanUrduNormalizer.normalize("khana"))
            assertEquals("khaana", RomanUrduNormalizer.normalize("khaaana"))
        } finally { Locale.setDefault(old) }
        assertEquals("", RomanUrduNormalizer.normalize("a".repeat(33)))
        assertEquals("", RomanUrduNormalizer.normalize("a\u202Eb"))
    }

    @Test fun longSentencesPreserveSpacingInManualMode() {
        val sentence = "main theek hoon  ".repeat(500)
        assertEquals(sentence, engine.convertSentence(sentence))
        assertTrue(engine.suggest("z".repeat(100_000)).choices.isEmpty())
    }

    @Test fun queriesDoNotAccumulatePersonalHistory() {
        val initial = engine.suggest("ghar")
        repeat(20) { engine.suggest("bas", "yeh buri ") }
        assertEquals(initial, engine.suggest("ghar"))
    }

    @Test fun dictionaryRejectsMalformedAndUnsafeInput() {
        for (line in listOf("wrong", "آپ\t\t\taap\t50\tNaN\t\t\tword",
            "آپ\t\t\taap\t200\t0.9\t\t\tword",
            "آپ\u202E\t\t\taap\t50\t0.9\t\t\tword", "x".repeat(2049))) {
            assertFailsWith<IllegalArgumentException> { RomanUrduLexicon.load(line.reader()) }
        }
    }
}
