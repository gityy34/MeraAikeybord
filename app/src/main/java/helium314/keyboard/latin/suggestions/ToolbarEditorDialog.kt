// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.app.AlertDialog
import android.os.Build
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.children
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.getPlatformDialogThemeContext
import helium314.keyboard.latin.utils.toolbarKeyLabel

internal fun createToolbarEditorDialog(
    anchor: View,
    pinned: Boolean,
    available: List<ToolbarKey>,
    enabled: List<ToolbarKey>,
    onSave: (List<ToolbarKey>) -> Unit,
): AlertDialog? {
    val token = anchor.windowToken ?: return null
    val context = getPlatformDialogThemeContext(anchor.context)
    val resources = context.resources
    val state = ToolbarEditorState(available, enabled)
    val buttonSize = 48.dpToPx(resources)

    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(12.dpToPx(resources), 0, 12.dpToPx(resources), 0)
    }
    content.addView(TextView(context).apply {
        setText(if (pinned) R.string.toolbar_editor_pinned_help else R.string.toolbar_editor_help)
        setPaddingRelative(8.dpToPx(resources), 0, 8.dpToPx(resources), 8.dpToPx(resources))
    })
    val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val scroll = ScrollView(context).apply { addView(rows) }
    content.addView(scroll, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        0,
        1f
    ))

    fun renderRows() {
        rows.removeAllViews()
        for (key in state.items) {
            val label = key.toolbarKeyLabel(context)
            val row = LinearLayout(context).apply {
                tag = key
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = buttonSize
            }
            val checkbox = CheckBox(context).apply {
                text = label
                isChecked = state.isEnabled(key)
                setOnCheckedChangeListener { _, checked ->
                    state.setEnabled(key, checked)
                    renderRows()
                }
            }
            row.addView(checkbox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            for ((offset, description) in listOf(-1 to R.string.toolbar_move_up, 1 to R.string.toolbar_move_down)) {
                val neighbor = state.neighbor(key, offset)
                val move = ImageButton(context, null, android.R.attr.borderlessButtonStyle).apply {
                    setImageResource(R.drawable.ic_arrow_right)
                    rotation = offset * 90f
                    contentDescription = context.getString(description, label)
                    isEnabled = neighbor != null
                    alpha = if (isEnabled) 1f else 0.3f
                    setOnClickListener {
                        if (neighbor != null && state.move(key, neighbor)) renderRows()
                    }
                }
                row.addView(move, LinearLayout.LayoutParams(buttonSize, buttonSize))
            }
            val handle = ImageButton(context, null, android.R.attr.borderlessButtonStyle).apply {
                setImageResource(R.drawable.ic_drag_indicator)
                contentDescription = context.getString(R.string.toolbar_drag_tool, label)
                isEnabled = state.isEnabled(key)
                alpha = if (isEnabled) 1f else 0.3f
                setOnLongClickListener { view ->
                    val shadow = View.DragShadowBuilder(row)
                    // The dragged key stays in local state. No text or external drag target is needed.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        view.startDragAndDrop(null, shadow, key, 0)
                    else {
                        @Suppress("DEPRECATION")
                        view.startDrag(null, shadow, key, 0)
                    }
                }
            }
            row.addView(handle, LinearLayout.LayoutParams(buttonSize, buttonSize))
            rows.addView(row)
        }
    }

    var dropTarget: View? = null
    fun clearDropTarget() {
        dropTarget?.alpha = 1f
        dropTarget = null
    }
    fun enabledTargetAt(y: Float): View? = rows.children.firstOrNull {
        y >= it.top && y < it.bottom && (it.tag as? ToolbarKey)?.let(state::isEnabled) == true
    }
    rows.setOnDragListener { _, event ->
        val key = event.localState as? ToolbarKey ?: return@setOnDragListener false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> state.isEnabled(key)
            DragEvent.ACTION_DRAG_LOCATION -> {
                val target = enabledTargetAt(event.y)?.takeIf { it.tag != key }
                if (target !== dropTarget) {
                    clearDropTarget()
                    dropTarget = target
                    target?.alpha = 0.65f
                }
                val position = event.y - scroll.scrollY
                if (position < buttonSize) scroll.scrollBy(0, -buttonSize / 3)
                if (position > scroll.height - buttonSize) scroll.scrollBy(0, buttonSize / 3)
                true
            }
            DragEvent.ACTION_DROP -> {
                val target = enabledTargetAt(event.y)?.tag as? ToolbarKey
                clearDropTarget()
                if (target != null && state.move(key, target)) {
                    // Keep the drag recipient attached until the framework ends the drag.
                    rows.post { renderRows() }
                    true
                } else false
            }
            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                clearDropTarget()
                true
            }
            else -> true
        }
    }
    renderRows()

    val dialog = AlertDialog.Builder(context)
        .setTitle(if (pinned) R.string.toolbar_edit_pinned else R.string.toolbar_edit_tools)
        .setView(content)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.toolbar_editor_done) { _, _ -> onSave(state.enabled) }
        .create()
    val window = dialog.window ?: return null
    window.attributes = window.attributes.apply {
        this.token = token
        type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    return dialog
}
