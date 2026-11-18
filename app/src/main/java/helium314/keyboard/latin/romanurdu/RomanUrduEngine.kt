// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.romanurdu

import java.util.Locale
import kotlin.math.abs

enum class RomanUrduChoiceKind { ENGLISH, URDU, RELATED }
data class RomanUrduChoice(val text: String, val kind: RomanUrduChoiceKind)
data class RomanUrduMatch(val entry: RomanUrduEntry, val confidence: Double, val score: Double)
data class RomanUrduResult(
    val original: String,
    val matches: List<RomanUrduMatch>,
    val choices: List<RomanUrduChoice>,
    val automaticReplacement: String?
)

/** Pure offline candidate engine. It performs no editor operations, persistence, or logging. */
class RomanUrduEngine(
    private val lexicon: RomanUrduLexicon,
    private val protectedEnglish: Set<String>
) {
    private val token = Regex("[\\p{L}]+")
    private val sentenceBoundary = Regex("[.!?؟۔\\n]")
    private val urduVocabulary = lexicon.entries.map { it.urdu }.toSet()
    private val ambiguous = setOf("main", "mein", "me", "is", "in", "to", "or", "the", "do", "par", "ham",
        "bad", "sun", "us", "char")

    fun suggest(roman: String, before: String = "", after: String = ""): RomanUrduResult {
        fun empty() = RomanUrduResult(roman, emptyList(), emptyList(), null)
        val normalized = RomanUrduNormalizer.normalize(roman)
        if (normalized.isEmpty()) return empty()
        val lower = roman.lowercase(Locale.ROOT)
        // Keep sentence text transient and bound context processing per query.
        val left = words(sentenceBoundary.split(before.takeLast(256)).last()).takeLast(8)
        val right = words(sentenceBoundary.split(after.take(256)).first()).take(8)
        val context = left + right
        val romanContext = context.count {
            it !in ambiguous && (lexicon.exact.containsKey(it) || it in urduVocabulary)
        }
        val englishContext = context.count { it !in ambiguous && it in protectedEnglish }
        if (lower in protectedEnglish && lower !in ambiguous) return empty()
        if (englishContext >= 2 && romanContext == 0) return empty()
        // Treat unknown title-cased words as possible names, not spelling mistakes.
        if (roman.any(Char::isUpperCase) && lexicon.exact[lower].isNullOrEmpty()) return empty()

        val candidates = linkedMapOf<RomanUrduEntry, Double>()
        fun add(entries: List<RomanUrduEntry>?, confidence: Double) {
            for (entry in entries.orEmpty().take(64)) {
                candidates[entry] = maxOf(candidates[entry] ?: 0.0, confidence)
            }
        }
        add(lexicon.exact[lower], 0.99)
        add(lexicon.normalized[normalized], 0.91)
        add(lexicon.phonetic[RomanUrduNormalizer.phoneticKey(normalized)], 0.73)
        // A bounded edit-distance search is only needed if indexed retrieval found nothing.
        if (candidates.isEmpty() && normalized.length >= 5) {
            var inspected = 0
            search@ for (length in normalized.length - 1..normalized.length + 1) {
                for ((key, entries) in lexicon.fuzzyBuckets[normalized.first() to length].orEmpty()) {
                    if (++inspected > 256 || candidates.size >= 64) break@search
                    if (oneEditAway(key, normalized)) add(entries, 0.64)
                }
            }
        }
        val ranked = candidates.map { (entry, matchConfidence) ->
            val contextual = (if (left.any { it in entry.previous }) 0.06 else 0.0) +
                (if (right.any { it in entry.next }) 0.06 else 0.0)
            val confidence = minOf(matchConfidence, entry.confidence)
            RomanUrduMatch(entry, confidence, confidence + contextual + entry.frequency * 0.0003)
        }.sortedWith(compareByDescending<RomanUrduMatch> { it.score }.thenBy { it.entry.urdu })
            .distinctBy { it.entry.urdu }.take(5)
        val best = ranked.firstOrNull() ?: return empty()
        val margin = best.score - (ranked.getOrNull(1)?.score ?: 0.0)
        val choices = arrayListOf<RomanUrduChoice>()
        if (best.confidence >= 0.90 && margin >= 0.05) best.entry.english?.let {
            choices.add(RomanUrduChoice(it, RomanUrduChoiceKind.ENGLISH))
        }
        choices.add(RomanUrduChoice(best.entry.urdu, RomanUrduChoiceKind.URDU))
        if (best.confidence >= 0.90) {
            best.entry.related.firstOrNull { it != best.entry.urdu }?.let {
                choices.add(RomanUrduChoice(it, RomanUrduChoiceKind.RELATED))
            }
        }
        // A confidence score here is an editorial heuristic, not a calibrated probability.
        val auto = if (best.confidence >= 0.90 && margin >= 0.05 &&
            (lower !in ambiguous || romanContext > 0) &&
            (englishContext == 0 || romanContext >= 2)) best.entry.urdu else null
        return RomanUrduResult(roman, ranked, choices.distinctBy { it.text }, auto)
    }

    /** Optional conversion helper; manual mode returns the input byte-for-byte. */
    fun convertSentence(sentence: String, automatic: Boolean = false): String {
        if (!automatic) return sentence
        val out = StringBuilder()
        var end = 0
        for (match in token.findAll(sentence)) {
            out.append(sentence, end, match.range.first)
            val previous = sentence.getOrNull(match.range.first - 1)
            val following = sentence.getOrNull(match.range.last + 1)
            // Preserve identifiers, URLs, email, and digit-bearing tokens as a whole.
            val embedded = (previous != null && (previous.isLetterOrDigit() || previous in "_/@#")) ||
                (following != null && (following.isLetterOrDigit() || following in "_/@#")) ||
                (previous == '.' && sentence.getOrNull(match.range.first - 2)?.isLetterOrDigit() == true) ||
                (following == '.' && sentence.getOrNull(match.range.last + 2)?.isLetterOrDigit() == true)
            val before = sentence.substring(maxOf(0, match.range.first - 256), match.range.first)
            val after = sentence.substring(match.range.last + 1, minOf(sentence.length, match.range.last + 257))
            val candidate = if (embedded) null else suggest(match.value, before, after)
            out.append(candidate?.automaticReplacement ?: match.value)
            end = match.range.last + 1
        }
        return out.append(sentence, end, sentence.length).toString()
    }

    private fun words(text: String) = token.findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList()

    private fun oneEditAway(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (a.length >= b.length) i++
            if (b.length >= a.length) j++
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
