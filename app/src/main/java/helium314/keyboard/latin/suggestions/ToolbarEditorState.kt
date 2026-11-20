// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.utils.ToolbarKey

/** Unsaved shortcut slots and tool order. */
internal class ToolbarEditorState(
    available: List<ToolbarKey>,
    enabled: List<ToolbarKey>,
    gridOrder: List<ToolbarKey> = emptyList(),
    lockedPositions: Map<ToolbarKey, Int> = emptyMap(),
) {
    private val selection = toolbarPinSlots(enabled.filter { it in available }, lockedPositions).toMutableList()
    private val locked = lockedPositions.keys.filterTo(mutableSetOf()) { it in selection }
    private val grid = (gridOrder + available).distinct().filter { it in available && it !in selection }.toMutableList()
    val enabled: List<ToolbarKey> get() = selection.filterNotNull()
    val pinSlots: List<ToolbarKey?> get() = selection.toList()
    val tools: List<ToolbarKey> get() = grid.toList()
    val pinLocks: Map<ToolbarKey, Int>
        get() = selection.mapIndexedNotNull { index, key -> key?.takeIf(::isLocked)?.let { it to index } }.toMap()

    fun isEnabled(key: ToolbarKey) = key in selection
    fun isLocked(key: ToolbarKey) = key in locked

    fun setLocked(key: ToolbarKey, lock: Boolean): Boolean {
        if (!isEnabled(key) || (lock && selection.indexOf(key) >= ToolbarKey.entries.size)) return false
        val changed = if (lock) locked.add(key) else locked.remove(key)
        if (changed && !lock) replaceMovablePins(selection.filterNotNull().filterNot(::isLocked))
        return changed
    }

    fun dropOnPins(key: ToolbarKey, targetSlot: Int?): Boolean {
        if (isLocked(key) || (!isEnabled(key) && key !in grid)) return false
        if (targetSlot != null && targetSlot < 0) return false
        val target = targetSlot?.let(selection::getOrNull)
        if (target == key || target?.let(::isLocked) == true) return false
        val movable = selection.filterNotNull().filterNot(::isLocked).toMutableList()
        val targetIndex = if (targetSlot == null) movable.size else selection.take(targetSlot)
            .count { it != null && !isLocked(it) }
        val previous = movable.toList()
        movable.remove(key)
        movable.add(targetIndex.coerceAtMost(movable.size), key)
        if (previous == movable) return false
        grid.remove(key)
        replaceMovablePins(movable)
        return true
    }

    fun dropOnTools(key: ToolbarKey, target: ToolbarKey? = null): Boolean {
        if (isLocked(key) || target == key || (target != null && target !in grid)) return false
        val oldIndex = grid.indexOf(key)
        if (oldIndex < 0 && !isEnabled(key)) return false
        val targetIndex = target?.let(grid::indexOf) ?: grid.size
        if (oldIndex >= 0) grid.removeAt(oldIndex)
        val newIndex = targetIndex.coerceAtMost(grid.size)
        grid.add(newIndex, key)
        if (oldIndex < 0) replaceMovablePins(selection.filterNotNull().filter { it != key && !isLocked(it) })
        return oldIndex != newIndex
    }

    fun neighboringPinSlot(key: ToolbarKey, offset: Int): Int? {
        val slots = selection.indices.filter { selection[it]?.let { pin -> !isLocked(pin) } == true }
        val index = slots.indexOf(selection.indexOf(key))
        return if (index < 0) null else slots.getOrNull(index + offset)
    }

    fun neighboringTool(key: ToolbarKey, offset: Int): ToolbarKey? {
        val index = grid.indexOf(key)
        return if (index < 0) null else grid.getOrNull(index + offset)
    }

    private fun replaceMovablePins(keys: List<ToolbarKey>) {
        val locks = pinLocks
        selection.clear()
        selection.addAll(toolbarPinSlots(keys + locks.keys, locks))
    }
}

internal fun toolbarPinSlots(pins: List<ToolbarKey>, locks: Map<ToolbarKey, Int>): List<ToolbarKey?> {
    val unique = pins.distinct()
    val slots = MutableList<ToolbarKey?>(unique.size) { null }
    val placed = mutableSetOf<ToolbarKey>()
    for (key in unique) {
        val index = locks[key]?.takeIf { it in ToolbarKey.entries.indices } ?: continue
        while (slots.size <= index) slots.add(null)
        if (slots[index] == null) {
            slots[index] = key
            placed.add(key)
        }
    }
    val remaining = unique.filterNot(placed::contains).iterator()
    for (index in slots.indices) {
        if (!remaining.hasNext()) break
        if (slots[index] == null) slots[index] = remaining.next()
    }
    return slots
}

internal fun pinnedToolbarCapacity(width: Int, controlsWidth: Int, suggestionWidth: Int, keyWidth: Int): Int =
    if (keyWidth <= 0) 0 else ((width - controlsWidth - suggestionWidth) / keyWidth).coerceAtLeast(0)

internal fun toolbarToolsPerPage(height: Int, rowHeight: Int): Int =
    if (rowHeight <= 0) 2 else (height / rowHeight).coerceIn(1, 3) * 2
