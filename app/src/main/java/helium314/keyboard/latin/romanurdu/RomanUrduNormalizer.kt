// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.romanurdu

import java.text.Normalizer
import java.util.Locale

/** Matching keys only; never replaces the spelling displayed in the editor. */
object RomanUrduNormalizer {
    const val MAX_WORD_LENGTH = 32
    private val repeated = Regex("([a-z])\\1{2,}")

    fun normalize(value: String): String {
        if (value.isEmpty() || value.length > MAX_WORD_LENGTH) return ""
        val lower = Normalizer.normalize(value, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        if (lower.any { it !in 'a'..'z' }) return ""
        return repeated.replace(lower, "$1$1")
    }

    /** A lossy retrieval key. A match is a suggestion, not permission to auto-convert. */
    fun phoneticKey(normalized: String): String = normalized
        .replace("chh", "ch")
        .replace("th", "t").replace("dh", "d")
        .replace("ee", "i").replace("ey", "e").replace("ay", "e")
        .replace("aa", "a").replace("oo", "u").replace("ou", "u")
        .replace("q", "k")
        .filterIndexed { index, c -> index == 0 || c !in "aeiou" }
}
