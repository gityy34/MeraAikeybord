// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.romanurdu

import android.content.Context
import helium314.keyboard.latin.InputStyle
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.suggestions.SuggestedWords
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.TextAtCursor

/** Owns immutable bundled data only. Never stores editor text or correction history. */
object RomanUrduAdapter {
    @Volatile private var engine: RomanUrduEngine? = null
    @Volatile private var loading = false

    @JvmStatic fun isRoman(spec: String?) = spec == "roman_urdu" || spec == "roman_urdu_auto"
    @JvmStatic fun isAutomatic(spec: String?) = spec == "roman_urdu_auto"

    @JvmStatic fun preload(context: Context) {
        if (engine != null || loading) return
        loading = true
        val app = context.applicationContext
        try {
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                try { load(app) } finally { loading = false }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            loading = false
        }
    }

    /** Invoke on the suggestion worker, not the input thread. A failed load stays literal. */
    @JvmStatic @Synchronized fun load(context: Context): RomanUrduEngine? {
        engine?.let { return it }
        return try {
            val lexicon = context.assets.open("roman_urdu/lexicon.tsv").bufferedReader().use(RomanUrduLexicon::load)
            val english = context.assets.open("roman_urdu/english_protected.txt").bufferedReader().use { reader ->
                reader.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.toSet()
            }
            RomanUrduEngine(lexicon, english).also { engine = it }
        } catch (error: Exception) {
            // Fail closed: no fallback character conversion and no exception containing text.
            Log.e("RomanUrdu", "Unable to load bundled Roman Urdu data", error)
            null
        }
    }

    private fun surrounding(typed: String, snapshot: TextAtCursor?): Pair<String, String>? {
        if (snapshot == null) return null
        val cursor = snapshot.cursorPosition
        if (cursor < typed.length || cursor > snapshot.text.length) return null
        if (snapshot.text.subSequence(cursor - typed.length, cursor).toString() != typed) return null
        val start = cursor - typed.length
        val before = snapshot.text.subSequence(maxOf(0, start - 256), start).toString()
        val after = snapshot.text.subSequence(cursor, minOf(snapshot.text.length, cursor + 256)).toString()
        if (before.lastOrNull()?.let { it.isLetterOrDigit() || it in "_/@#." } == true) return null
        if (after.firstOrNull()?.let { it in "_/@#" } == true) return null
        if (after.startsWith('.') && after.getOrNull(1)?.isLetterOrDigit() == true) return null
        // Cursor inside an existing word must never replace a partial word automatically.
        if (after.firstOrNull()?.isLetterOrDigit() == true) return null
        return before to after
    }

    @JvmStatic fun suggestions(context: Context, typed: String, snapshot: TextAtCursor?, sequence: Int,
                               allowed: Boolean): RomanUrduSuggestedWords {
        val surrounding = if (allowed) surrounding(typed, snapshot) else null
        val choices = if (allowed && surrounding != null)
            load(context)?.suggest(typed, surrounding.first, surrounding.second)?.choices.orEmpty()
        else emptyList()
        return RomanUrduSuggestedWords(typed, choices, sequence)
    }

    /** Does not load data or block the input thread. No ready engine means no replacement. */
    @JvmStatic fun automaticReplacement(typed: String, snapshot: TextAtCursor?): String? {
        val surrounding = surrounding(typed, snapshot) ?: return null
        return engine?.suggest(typed, surrounding.first, surrounding.second)?.automaticReplacement
    }
}

/** Explicit positions avoid placeholders or shared-strip reordering of missing choices. */
class RomanUrduSuggestedWords private constructor(
    typed: SuggestedWordInfo,
    private val choices: List<RomanUrduChoice>,
    sequence: Int
) : SuggestedWords(listOf(typed) + choices.map { info(it.text) }, null, typed,
    true, false, false, InputStyle.TYPING, sequence) {
    constructor(typed: String, choices: List<RomanUrduChoice>, sequence: Int) :
        this(info(typed, SuggestedWordInfo.KIND_TYPED), choices.sortedBy {
            when (it.kind) {
                RomanUrduChoiceKind.URDU -> 0
                RomanUrduChoiceKind.ENGLISH -> 1
                RomanUrduChoiceKind.RELATED -> 2
            }
        }, sequence)

    fun positionOffset(index: Int): Int = when (choices.getOrNull(index - 1)?.kind) {
        RomanUrduChoiceKind.ENGLISH -> -1
        RomanUrduChoiceKind.URDU -> 0
        RomanUrduChoiceKind.RELATED -> 1
        null -> -100
    }

    companion object {
        private fun info(text: String, kind: Int = SuggestedWordInfo.KIND_COMPLETION) = SuggestedWordInfo(
            text, "", SuggestedWordInfo.MAX_SCORE, kind, Dictionary.DICTIONARY_USER_TYPED,
            SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE
        )
    }
}
