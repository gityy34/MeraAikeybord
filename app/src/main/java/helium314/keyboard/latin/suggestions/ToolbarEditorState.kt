// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.utils.ToolbarKey

/** Unsaved toolbar order. Disabled tools follow the enabled tools in the editor. */
internal class ToolbarEditorState(
    private val available: List<ToolbarKey>,
    enabled: List<ToolbarKey>,
) {
    private val selection = enabled.filter { it in available }.distinct().toMutableList()
    val enabled: List<ToolbarKey> get() = selection.toList()
    val items: List<ToolbarKey> get() = selection + available.filterNot { it in selection }

    fun isEnabled(key: ToolbarKey) = key in selection

    fun setEnabled(key: ToolbarKey, enabled: Boolean) {
        if (enabled && key in available && key !in selection) selection.add(key)
        if (!enabled) selection.remove(key)
    }

    fun move(key: ToolbarKey, target: ToolbarKey): Boolean {
        val from = selection.indexOf(key)
        val to = selection.indexOf(target)
        if (from < 0 || to < 0 || from == to) return false
        selection.add(to, selection.removeAt(from))
        return true
    }

    fun neighbor(key: ToolbarKey, offset: Int): ToolbarKey? {
        val index = selection.indexOf(key)
        return if (index < 0) null else selection.getOrNull(index + offset)
    }
}

internal fun pinnedToolbarCapacity(width: Int, controlsWidth: Int, suggestionWidth: Int, keyWidth: Int): Int =
    if (keyWidth <= 0) 0 else ((width - controlsWidth - suggestionWidth) / keyWidth).coerceIn(0, 4)
