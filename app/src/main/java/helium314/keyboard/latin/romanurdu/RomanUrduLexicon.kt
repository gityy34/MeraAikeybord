// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.romanurdu

import java.io.Reader

data class RomanUrduEntry(
    val urdu: String,
    val english: String?,
    val related: List<String>,
    val forms: Set<String>,
    // Editorial frequency prior until an independently evaluated corpus is imported.
    val frequency: Int,
    val confidence: Double,
    val previous: Set<String>,
    val next: Set<String>,
    val category: String
)

/** Strict, bounded parser for bundled data. Imported dictionaries must be vetted first. */
class RomanUrduLexicon private constructor(val entries: List<RomanUrduEntry>) {
    val exact = index { it.forms }
    val normalized = index { entry -> entry.forms.map(RomanUrduNormalizer::normalize).toSet() }
    val phonetic = index { entry -> entry.forms.map {
        RomanUrduNormalizer.phoneticKey(RomanUrduNormalizer.normalize(it))
    }.toSet() }
    val fuzzyBuckets = normalized.entries.sortedBy { it.key }.groupBy { it.key.first() to it.key.length }

    private fun index(keys: (RomanUrduEntry) -> Set<String>): Map<String, List<RomanUrduEntry>> {
        val result = linkedMapOf<String, MutableList<RomanUrduEntry>>()
        for (entry in entries) for (key in keys(entry)) {
            if (key.isNotEmpty()) result.getOrPut(key) { arrayListOf() }.add(entry)
        }
        return result.mapValues { it.value.toList() }
    }

    companion object {
        const val MAX_ENTRIES = 20_000
        private const val MAX_LINE_LENGTH = 2048
        private fun list(value: String) = value.split('|').filter { it.isNotEmpty() }
        private fun safe(value: String) = value.none {
            Character.isISOControl(it) || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069'
        }
        private fun urdu(value: String) = value.isNotEmpty() && safe(value) && value.all {
            it == ' ' || it in '\u0600'..'\u06FF'
        }

        fun load(reader: Reader): RomanUrduLexicon {
            val entries = arrayListOf<RomanUrduEntry>()
            // Reader.readLine() would allocate an unbounded string on malformed input.
            val line = StringBuilder()
            fun parse() {
                val raw = line.toString().removeSuffix("\r")
                line.setLength(0)
                if (raw.isBlank() || raw.startsWith('#')) return
                require(entries.size < MAX_ENTRIES) { "Roman Urdu dictionary is too large" }
                val fields = raw.split('\t')
                require(fields.size == 9) { "Roman Urdu dictionary requires nine fields" }
                val (word, english, alternatives, aliases, frequency) = fields
                require(urdu(word) && list(alternatives).all(::urdu)) { "Invalid Urdu entry" }
                require(english.length <= 48 && english.all { it in ' '..'~' }) { "Invalid English meaning" }
                val forms = list(aliases).toSet()
                require(forms.isNotEmpty() && forms.size <= 32 && forms.all {
                    it.isNotEmpty() && it.length <= RomanUrduNormalizer.MAX_WORD_LENGTH && it.all { c -> c in 'a'..'z' }
                }) { "Invalid Roman forms" }
                val confidence = fields[5].toDouble()
                require(confidence.isFinite() && confidence in 0.0..1.0)
                val prior = frequency.toInt()
                require(prior in 0..100)
                require(fields.drop(6).all { it.length <= 256 && safe(it) }) { "Invalid context metadata" }
                entries.add(RomanUrduEntry(word, english.ifEmpty { null }, list(alternatives).distinct(),
                    forms, prior, confidence, list(fields[6]).toSet(), list(fields[7]).toSet(), fields[8]))
            }
            val buffered = reader.buffered()
            while (true) {
                val c = buffered.read()
                if (c < 0) { if (line.isNotEmpty()) parse(); break }
                if (c == '\n'.code) parse()
                else {
                    require(line.length < MAX_LINE_LENGTH) { "Roman Urdu dictionary line is too long" }
                    line.append(c.toChar())
                }
            }
            require(entries.isNotEmpty()) { "Empty Roman Urdu dictionary" }
            return RomanUrduLexicon(entries.toList())
        }
    }
}
