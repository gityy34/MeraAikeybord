// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.annotation.StringRes
import helium314.keyboard.latin.R

internal enum class AiTextMode(@StringRes val title: Int) {
    SELECTED(R.string.ai_scope_selected),
    WORD(R.string.ai_scope_word),
    SENTENCE(R.string.ai_scope_sentence),
    BEFORE_CURSOR(R.string.ai_scope_before),
    DRAFT(R.string.ai_scope_draft),
}

internal object AiPrivacyManager {
    const val AI_CLIP_MIME = "application/vnd.meraaikeybord.ai-result"

    @JvmStatic
    fun permits(inputType: Int, imeOptions: Int, incognito: Boolean, locked: Boolean): Boolean {
        if (incognito || locked || imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            return false
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT ||
            inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_NORMAL, InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> true
            else -> false
        }
    }

    private val digitsWithSeparators = Regex("""(?<!\p{Nd})\p{Nd}(?:[ -]?\p{Nd}){12,18}(?!\p{Nd})""")
    private val pin = Regex("""\p{Nd}{4,8}""")
    private val credentials = Regex("""(?i)\b(?:password|passcode|authorization|api[ _-]?key|cvv|cvc|pin)\s*[:=]\s*\S+""")
    fun looksSensitive(text: String): Boolean = pin.matches(text.trim()) ||
        digitsWithSeparators.containsMatchIn(text) || credentials.containsMatchIn(text)
}

internal class AiEditor(
    val connection: InputConnection,
    val info: EditorInfo,
    val start: Int,
    val end: Int,
    val revision: Long,
) {
    fun isSameField(other: AiEditor) = connection === other.connection && info === other.info
}

/** Source text is held only in the open panel and never included in a generated toString. */
internal class AiTextSnapshot(
    val editor: AiEditor,
    val mode: AiTextMode,
    val rangeStart: Int,
    val rangeEnd: Int,
    val text: String,
)

internal sealed interface AiCapture {
    class Ready(val snapshot: AiTextSnapshot) : AiCapture
    class Failure(val error: AiError) : AiCapture
}

internal class AiTextReplacementManager(
    private val activeEditor: () -> AiEditor?,
    private val onApplied: (Int, Int) -> Unit,
) {
    private class Undo(val snapshot: AiTextSnapshot, val appliedText: String)
    private var undo: Undo? = null

    fun capture(mode: AiTextMode, maximum: Int): AiCapture {
        val editor = activeEditor() ?: return AiCapture.Failure(AiError.PRIVATE_FIELD)
        return runCatching { capture(editor, mode, maximum) }.getOrElse { AiCapture.Failure(AiError.EDITOR) }
    }

    private fun capture(editor: AiEditor, mode: AiTextMode, maximum: Int): AiCapture {
        val start = minOf(editor.start, editor.end)
        val end = maxOf(editor.start, editor.end)
        if (start < 0 || maximum !in 1..AiApiConfig.MAX_LENGTH) return AiCapture.Failure(AiError.EDITOR)
        if (end - start > maximum) return AiCapture.Failure(AiError.TOO_LONG)
        val connection = editor.connection

        val selected = if (start != end) connection.getSelectedText(0)?.toString()
            ?: return AiCapture.Failure(AiError.STALE) else ""
        if (selected.length != end - start) return AiCapture.Failure(AiError.STALE)
        if (mode == AiTextMode.SELECTED) return snapshot(editor, mode, start, selected, maximum)
        if (start != end && mode != AiTextMode.DRAFT) return AiCapture.Failure(AiError.EDITOR)

        val count = maximum + 1
        val before = connection.getTextBeforeCursor(count, 0)?.toString()
            ?: return AiCapture.Failure(AiError.EDITOR)
        if (before.length > start || before.length > count ||
            (start <= count && before.length != start)) return AiCapture.Failure(AiError.STALE)
        if (mode == AiTextMode.BEFORE_CURSOR || mode == AiTextMode.DRAFT) {
            if (before.length != start || before.length + selected.length > maximum)
                return AiCapture.Failure(AiError.TOO_LONG)
            if (mode == AiTextMode.BEFORE_CURSOR) return snapshot(editor, mode, 0, before, maximum)
        }
        val afterCount = if (mode == AiTextMode.DRAFT) count - before.length - selected.length else count
        val after = connection.getTextAfterCursor(afterCount, 0)?.toString()
            ?: return AiCapture.Failure(AiError.EDITOR)
        if (after.length > afterCount) return AiCapture.Failure(AiError.TOO_LONG)
        if (mode == AiTextMode.DRAFT) return snapshot(editor, mode, 0, before + selected + after, maximum)

        val text = before + after
        val cursor = before.length
        val range = when (mode) {
            AiTextMode.WORD -> wordRange(text, cursor)
            AiTextMode.SENTENCE -> sentenceRange(text, cursor)
            else -> null
        } ?: return AiCapture.Failure(AiError.EMPTY)
        if (range.first == 0 && start > before.length || range.last == text.lastIndex && after.length == count)
            return AiCapture.Failure(AiError.TOO_LONG)
        return snapshot(editor, mode, start - before.length + range.first, text.substring(range), maximum)
    }

    fun isCurrent(snapshot: AiTextSnapshot, maximum: Int): Boolean = runCatching {
        val editor = activeEditor() ?: return false
        if (!editor.isSameField(snapshot.editor) || editor.revision != snapshot.editor.revision ||
            editor.start != snapshot.editor.start || editor.end != snapshot.editor.end) return false
        val current = (capture(editor, snapshot.mode, maximum) as? AiCapture.Ready)?.snapshot ?: return false
        current.rangeStart == snapshot.rangeStart && current.rangeEnd == snapshot.rangeEnd &&
            current.text == snapshot.text
    }.getOrDefault(false)

    fun apply(snapshot: AiTextSnapshot, replacement: String, maximum: Int): AiError? {
        if (!isCurrent(snapshot, maximum)) return AiError.STALE
        val editor = activeEditor() ?: return AiError.STALE
        if (!replace(editor, snapshot.rangeStart, snapshot.rangeEnd, snapshot.text, replacement))
            return AiError.EDITOR
        undo = Undo(snapshot, replacement)
        val cursor = snapshot.rangeStart + replacement.length
        onApplied(cursor, cursor)
        return null
    }

    fun undo(): AiError? {
        val saved = undo ?: return AiError.STALE
        val editor = activeEditor() ?: return AiError.STALE
        val start = saved.snapshot.rangeStart
        val end = start + saved.appliedText.length
        if (!editor.isSameField(saved.snapshot.editor) || editor.start != end || editor.end != end ||
            !runCatching { editor.connection.getTextBeforeCursor(saved.appliedText.length, 0)?.toString() == saved.appliedText }
                .getOrDefault(false)) return AiError.STALE
        if (!replace(editor, start, end, saved.appliedText, saved.snapshot.text)) return AiError.EDITOR
        undo = null
        // The previous selection is restored only after a successful, verified undo.
        val restored = runCatching {
            editor.connection.setSelection(saved.snapshot.editor.start, saved.snapshot.editor.end)
        }.getOrDefault(false)
        val originalEnd = start + saved.snapshot.text.length
        if (restored) onApplied(saved.snapshot.editor.start, saved.snapshot.editor.end)
        else onApplied(originalEnd, originalEnd)
        return null
    }

    fun clear() { undo = null }

    private fun replace(editor: AiEditor, start: Int, end: Int, expected: String, replacement: String): Boolean =
        runCatching {
            val connection = editor.connection
            if (!connection.beginBatchEdit()) return false
            try {
                if (!connection.finishComposingText()) return false
                if (!connection.setSelection(start, end)) return false
                // Verify the selected range on editors with absolute surrounding offsets.
                if (!selectionMatches(connection, start, end, expected)) {
                    connection.setSelection(editor.start, editor.end)
                    return false
                }
                val committed = connection.commitText(replacement, 1)
                if (!committed) connection.setSelection(editor.start, editor.end)
                committed
            } finally {
                connection.endBatchEdit()
            }
        }.getOrDefault(false)

    private fun selectionMatches(connection: InputConnection, start: Int, end: Int, expected: String): Boolean {
        if (connection.getSelectedText(0)?.toString() != expected) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val surrounding = connection.getSurroundingText(0, 0, 0) ?: return true
        if (surrounding.offset == -1) return true
        return surrounding.offset + surrounding.selectionStart == start &&
            surrounding.offset + surrounding.selectionEnd == end
    }

    private fun snapshot(editor: AiEditor, mode: AiTextMode, start: Int, text: String, maximum: Int): AiCapture =
        when {
            text.isBlank() -> AiCapture.Failure(AiError.EMPTY)
            text.length > maximum -> AiCapture.Failure(AiError.TOO_LONG)
            text.indices.any { index ->
                val char = text[index]
                char.isHighSurrogate() && text.getOrNull(index + 1)?.isLowSurrogate() != true ||
                    char.isLowSurrogate() && text.getOrNull(index - 1)?.isHighSurrogate() != true
            } -> AiCapture.Failure(AiError.EDITOR)
            AiPrivacyManager.looksSensitive(text) -> AiCapture.Failure(AiError.PRIVATE_FIELD)
            else -> AiCapture.Ready(AiTextSnapshot(editor, mode, start, start + text.length, text))
        }

    companion object {
        internal fun wordRange(text: String, cursor: Int): IntRange? {
            fun word(char: Char): Boolean {
                val type = Character.getType(char)
                return char.isLetterOrDigit() || type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() || char == '\u200c' || char == '\u200d'
            }
            var start = cursor
            var end = cursor
            while (start > 0 && word(text[start - 1])) start--
            while (end < text.length && word(text[end])) end++
            return (start until end).takeUnless { it.isEmpty() }
        }

        internal fun sentenceRange(text: String, cursor: Int): IntRange? {
            val boundaries = ".!?۔؟\n\r"
            var start = cursor
            while (start > 0 && text[start - 1] in " \t") start--
            // A cursor after the full stop or its trailing spaces belongs to the completed sentence.
            if (start > 0 && text[start - 1] in boundaries) start--
            var end = start
            while (start > 0 && text[start - 1] !in boundaries) start--
            while (end < text.length && text[end] !in boundaries) end++
            if (end < text.length && text[end] !in "\n\r") end++
            while (start < end && text[start].isWhitespace()) start++
            while (end > start && text[end - 1].isWhitespace()) end--
            return (start until end).takeUnless { it.isEmpty() }
        }
    }
}
