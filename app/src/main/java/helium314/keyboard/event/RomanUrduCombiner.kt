// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import java.util.ArrayList

/** Keeps the Latin spelling until a word boundary, allowing digraphs and backspace. */
class RomanUrduCombiner : Combiner {
    private val roman = StringBuilder()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.isConsumed || event.keyCode == KeyCode.SHIFT || event.keyCode == KeyCode.CAPS_LOCK)
            return event

        if (event.keyCode == KeyCode.DELETE) {
            if (roman.isEmpty()) return event
            roman.deleteCharAt(roman.lastIndex)
            if (roman.isNotEmpty()) return Event.createConsumedEvent(event)
            // Use the existing Hangul empty-composition path: replace with a space and
            // delete that space, without deleting any text before the composing word.
            return Event.createHardwareKeypressEvent(
                Constants.CODE_SPACE, Constants.CODE_SPACE, 0, event, event.isKeyRepeat
            )
        }

        val cp = event.codePoint
        if (!event.isFunctionalKeyEvent && (cp in 'a'.code..'z'.code || cp in 'A'.code..'Z'.code)) {
            // Bound per-keystroke work even when someone types without spaces.
            if (roman.length == MAX_ROMAN_LENGTH) {
                val text = combiningStateFeedback
                roman.setLength(0)
                roman.append(cp.toChar())
                return Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, null)
            }
            roman.append(cp.toChar())
            return Event.createConsumedEvent(event)
        }

        if (roman.isEmpty()) return event
        val text = combiningStateFeedback
        reset()
        return Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, event)
    }

    override val combiningStateFeedback: CharSequence
        get() = RomanUrduTransliterator.convert(roman.toString())

    override fun reset() { roman.setLength(0) }

    private companion object {
        const val MAX_ROMAN_LENGTH = 48
    }
}
