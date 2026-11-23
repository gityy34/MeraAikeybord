// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.toolbarKeyLabel

class ToolbarToolsPanel(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val colors = Settings.getValues().mColors
    private val fg = colors.get(ColorType.KEY_TEXT)
    private val surface = colors.get(ColorType.MAIN_BACKGROUND) or 0xff000000.toInt()
    private val rowHeight = dp(48)
    private val gap = dp(6)
    private val body = FrameLayout(context)
    private val hint = TextView(context)
    private val footer = LinearLayout(context)
    private val previous = footerButton(R.drawable.ic_arrow_left, R.string.toolbar_previous_page)
    private val next = footerButton(R.drawable.ic_arrow_right, R.string.toolbar_next_page)
    private val edit = footerButton(R.drawable.ic_toolbar_edit, R.string.toolbar_edit_pinned)
    private val pageLabel = TextView(context)

    private var browse: Browse? = null
    private var assistantEntry: AiAssistantEntry? = null
    private var pager: ViewPager2? = null
    private var pageSize = 0
    private var editScroll: ScrollView? = null

    private class Browse(val tools: List<ToolbarKey>, val activate: (ToolbarKey) -> Unit)

    init {
        orientation = VERTICAL
        isClickable = true
        ViewCompat.setAccessibilityPaneTitle(this, context.getString(R.string.toolbar_panel_title))
        setBackgroundColor(surface)
        setPadding(dp(8), dp(4), dp(8), 0)
        hint.text = context.getString(R.string.toolbar_editor_pinned_help)
        hint.setTextColor(fg)
        hint.textSize = 12f
        hint.setPaddingRelative(dp(6), 0, dp(6), dp(4))
        addView(hint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        footer.gravity = Gravity.CENTER_VERTICAL
        pageLabel.gravity = Gravity.CENTER
        pageLabel.setTextColor(fg)
        footer.addView(previous, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
        footer.addView(pageLabel, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        footer.addView(next, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
        footer.addView(edit, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)).apply { bottomMargin = gap })
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        previous.setOnClickListener { pager?.let { it.setCurrentItem(it.currentItem - 1, true) } }
        next.setOnClickListener { pager?.let { it.setCurrentItem(it.currentItem + 1, true) } }
        body.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop && browse != null) body.post { rebuildPages() }
        }
    }

    internal fun showTools(
        tools: List<ToolbarKey>,
        onTool: (ToolbarKey) -> Unit,
        assistant: AiAssistantEntry?,
        onEdit: () -> Unit,
    ) {
        clearContents()
        browse = Browse(tools, onTool)
        assistantEntry = assistant
        edit.setOnClickListener { onEdit() }
        hint.isVisible = false
        footer.isVisible = true
        rebuildPages()
    }

    internal fun updateAssistantEntry(entry: AiAssistantEntry?) {
        if (browse == null) return
        val changed = assistantEntry?.pinned != entry?.pinned
        assistantEntry = entry
        if (changed) pager?.adapter?.notifyItemChanged(0)
    }

    internal fun showEditor(
        draft: ToolbarEditorState,
        onPin: (ToolbarKey) -> Unit,
        onDrop: (ToolbarKey, ToolbarKey?) -> Boolean,
    ) {
        val scrollY = editScroll?.scrollY ?: 0
        clearContents()
        hint.isVisible = true
        footer.isVisible = false
        val tiles = grid(draft.tools, onPin, draft, onDrop)
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(tiles)
            setOnDragListener { _, event ->
                val drag = event.localState as? ToolbarToolDrag ?: return@setOnDragListener false
                when (event.action) {
                    DragEvent.ACTION_DROP -> {
                        val x = event.x + scrollX - tiles.left
                        val y = event.y + scrollY - tiles.top
                        val row = tiles.children.firstOrNull { y >= it.top && y < it.bottom + gap } as? ViewGroup
                        val target = row?.let { hitRow ->
                            hitRow.children.firstOrNull { x >= hitRow.left + it.left && x < hitRow.left + it.right }
                        }?.tag as? ToolbarKey
                        onDrop(drag.key, target)
                    }
                    DragEvent.ACTION_DRAG_LOCATION -> {
                        val edge = dp(28)
                        if (event.y < edge) scrollBy(0, -gap)
                        else if (event.y > height - edge) scrollBy(0, gap)
                        true
                    }
                    else -> true
                }
            }
        }
        body.addView(scroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        editScroll = scroll
        if (scrollY > 0) scroll.post { scroll.scrollTo(0, scrollY) }
    }

    internal fun clearContents() {
        browse = null
        assistantEntry = null
        pager?.adapter = null
        pager = null
        editScroll = null
        pageSize = 0
        body.removeAllViews()
        edit.setOnClickListener(null)
    }

    private fun rebuildPages() {
        val current = browse ?: return
        val size = toolbarToolsPerPage(body.height, rowHeight + gap)
        if (size == pageSize && pager != null) return
        val firstTool = (pager?.currentItem ?: 0) * pageSize
        pageSize = size
        val pages = current.tools.chunked(size).ifEmpty { listOf(emptyList()) }
        pager?.adapter = null
        body.removeAllViews()
        val view = ViewPager2(context)
        view.adapter = object : RecyclerView.Adapter<Page>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                Page(ScrollView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                })

            override fun onBindViewHolder(holder: Page, position: Int) {
                holder.scroll.removeAllViews()
                val tiles = grid(pages[position], current.activate)
                if (position == 0) assistantEntry?.let { entry ->
                    tiles.addView(assistantTile(entry), 0, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight).apply {
                        bottomMargin = gap
                    })
                }
                holder.scroll.addView(tiles)
            }

            override fun getItemCount() = pages.size
        }
        pager = view
        body.addView(view, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        fun showPage(position: Int) {
            pageLabel.text = context.getString(R.string.toolbar_page, position + 1, pages.size)
            val arrowDirection = if (layoutDirection == LAYOUT_DIRECTION_RTL) -1f else 1f
            previous.scaleX = arrowDirection
            next.scaleX = arrowDirection
            previous.isEnabled = position > 0
            next.isEnabled = position < pages.lastIndex
            previous.alpha = if (previous.isEnabled) 1f else 0.36f
            next.alpha = if (next.isEnabled) 1f else 0.36f
        }
        val page = (firstTool / size).coerceAtMost(pages.lastIndex)
        view.setCurrentItem(page, false)
        showPage(page)
        view.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (pager === view) showPage(position)
            }
        })
    }

    private class Page(val scroll: ScrollView) : RecyclerView.ViewHolder(scroll)

    private fun assistantTile(entry: AiAssistantEntry) = LinearLayout(context).apply {
        background = tileBackground()
        val icon = context.getDrawable(R.drawable.ic_ai_assistant)?.mutate()?.apply {
            setBounds(0, 0, dp(21), dp(21))
            setTint(fg)
        }
        val label = TextView(context).apply {
            setText(R.string.ai_assistant)
            setTextColor(fg)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPaddingRelative(dp(8), 0, dp(4), 0)
            compoundDrawablePadding = dp(8)
            setCompoundDrawablesRelative(icon, null, null, null)
            isFocusable = true
            setOnClickListener { entry.open() }
        }
        addView(label, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        val pin = footerButton(
            R.drawable.ic_clipboard_pin_rounded,
            if (entry.pinned) R.string.ai_unpin_from_toolbar else R.string.ai_pin_to_toolbar,
        )
        pin.alpha = if (entry.pinned) 1f else 0.68f
        pin.isSelected = entry.pinned
        pin.setOnClickListener { entry.togglePin() }
        addView(pin, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
    }

    private fun grid(
        tools: List<ToolbarKey>,
        onTool: (ToolbarKey) -> Unit,
        draft: ToolbarEditorState? = null,
        onMove: ((ToolbarKey, ToolbarKey) -> Boolean)? = null,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = VERTICAL
            for (pair in tools.chunked(2)) {
                val row = LinearLayout(context)
                for (key in pair) {
                    val label = key.toolbarPanelLabel(context)
                    val drawable = KeyboardIconsSet.instance.getNewDrawable(key.name, context)?.mutate()?.apply {
                        setBounds(0, 0, dp(21), dp(21))
                        setTint(fg)
                    }
                    val tile = TextView(context).apply {
                        tag = key
                        this.text = label
                        gravity = Gravity.CENTER_VERTICAL
                        setTextColor(fg)
                        textSize = 14f
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        setPaddingRelative(dp(8), 0, dp(6), 0)
                        compoundDrawablePadding = dp(8)
                        setCompoundDrawablesRelative(drawable, null, null, null)
                        background = tileBackground()
                        isFocusable = true
                        setOnClickListener { onTool(key) }
                        if (draft != null && onMove != null) {
                            contentDescription = context.getString(R.string.toolbar_pin_tool, label)
                            setOnLongClickListener { startToolbarToolDrag(it, key) }
                            for ((offset, action) in toolbarMoveActions) {
                                val target = draft.neighboringTool(key, offset) ?: continue
                                ViewCompat.addAccessibilityAction(this, context.getString(action, label)) { _, _ ->
                                    onMove(key, target)
                                }
                            }
                        }
                    }
                    row.addView(tile, LayoutParams(0, rowHeight, 1f).apply {
                        if (pair.first() != key) marginStart = gap
                    })
                }
                if (pair.size == 1) row.addView(View(context), LayoutParams(0, 0, 1f).apply { marginStart = gap })
                addView(row, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight).apply { bottomMargin = gap })
            }
        }

    private fun footerButton(icon: Int, description: Int) = ImageButton(context).apply {
        contentDescription = context.getString(description)
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(fg)
        background = tileBackground()
        scaleType = android.widget.ImageView.ScaleType.CENTER
    }

    private fun tileBackground() = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(fg, 38)),
        GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            setColor(ColorUtils.blendARGB(surface, fg, 0.09f))
        },
        null
    )

    private fun dp(value: Int) = value.dpToPx(resources)
}

internal data class ToolbarToolDrag(val key: ToolbarKey)

internal data class AiAssistantEntry(val pinned: Boolean, val open: () -> Unit, val togglePin: () -> Unit)

internal val toolbarMoveActions = listOf(-1 to R.string.toolbar_move_earlier, 1 to R.string.toolbar_move_later)

internal fun ToolbarKey.toolbarPanelLabel(context: Context): String =
    if (this == ToolbarKey.FLOATING && Settings.getValues().mIsFloatingKeyboard)
        context.getString(R.string.toolbar_dock_keyboard)
    else toolbarKeyLabel(context)

internal fun startToolbarToolDrag(view: View, key: ToolbarKey): Boolean {
    val shadow = View.DragShadowBuilder(view)
    val drag = ToolbarToolDrag(key)
    // No ClipData and no global drag flag. The only payload is an in-process tool identifier.
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) view.startDragAndDrop(null, shadow, drag, 0)
    else {
        @Suppress("DEPRECATION")
        view.startDrag(null, shadow, drag, 0)
    }
}
