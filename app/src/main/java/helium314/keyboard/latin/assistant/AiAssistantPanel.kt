// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.dpToPx

class AiAssistantPanel internal constructor(context: Context, attrs: AttributeSet?, colors: Colors) :
    LinearLayout(context, attrs) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, Settings.getValues().mColors)

    private val panelTextColor = colors.get(ColorType.KEY_TEXT)
    internal val contentColor get() = panelTextColor
    private val surface = colors.get(ColorType.MAIN_BACKGROUND) or 0xff000000.toInt()
    private val row = LinearLayout(context)
    private val closeButton = ImageButton(context)
    private val scroller: HorizontalScrollView
    private var details: View? = null

    init {
        ViewCompat.setAccessibilityPaneTitle(this, context.getString(R.string.ai_assistant))
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(surface)
        setPaddingRelative(dp(6), dp(3), dp(6), dp(3))
        row.gravity = Gravity.CENTER_VERTICAL
        scroller = HorizontalScrollView(context).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            addView(row, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        addView(scroller, LayoutParams(0, dp(48), 1f))

        closeButton.contentDescription = context.getString(R.string.ai_close)
        closeButton.setImageResource(R.drawable.ic_close)
        closeButton.imageTintList = ColorStateList.valueOf(panelTextColor)
        closeButton.scaleType = android.widget.ImageView.ScaleType.CENTER
        closeButton.background = tileBackground()
        addView(closeButton, LayoutParams(dp(48), dp(48)))
    }

    internal fun bind(tools: List<AiTool>, onSelect: (AiTool) -> Unit, onClose: () -> Unit) {
        removeDetails()
        row.removeAllViews()
        closeButton.setOnClickListener { onClose() }
        if (tools.isEmpty()) {
            val message = TextView(context).apply {
                setText(R.string.ai_no_tools)
                setTextColor(panelTextColor)
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(dp(8), 0, dp(8), 0)
            }
            row.addView(message, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        for (tool in tools) {
            val button = Button(context).apply {
                setText(tool.option.title)
                setTextColor(panelTextColor)
                textSize = 14f
                setAllCaps(false)
                setSingleLine()
                setPaddingRelative(dp(12), 0, dp(12), 0)
                backgroundTintList = null
                background = tileBackground()
                setOnClickListener { onSelect(tool) }
            }
            row.addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                marginEnd = dp(6)
            })
        }
    }

    internal fun addSettings(onClick: () -> Unit) {
        val button = Button(context).apply {
            setText(R.string.ai_api_settings)
            setAllCaps(false)
            setTextColor(panelTextColor)
            backgroundTintList = null
            background = tileBackground()
            setOnClickListener { onClick() }
        }
        row.addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    internal fun showDetails(content: LinearLayout, footer: View? = null) {
        removeDetails()
        scroller.isVisible = false
        val height = if (resources.configuration.screenHeightDp < 480) 104 else 172
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            if (footer != null) addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        styleControls(container)
        details = container
        addView(container, 0, LayoutParams(0, dp(height), 1f))
    }

    private fun styleControls(view: View) {
        if (view is TextView) view.setTextColor(panelTextColor)
        when (view) {
            is EditText -> {
                view.setHintTextColor(ColorUtils.setAlphaComponent(panelTextColor, 174))
                view.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(panelTextColor, 110))
            }
            is CheckBox -> view.buttonTintList = ColorStateList.valueOf(panelTextColor)
            is Button -> {
                view.backgroundTintList = null
                view.background = tileBackground()
                view.setPaddingRelative(dp(8), 0, dp(8), 0)
                (view.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = dp(4)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) styleControls(view.getChildAt(index))
        }
    }

    private fun removeDetails() {
        details?.let(::removeView)
        details = null
        scroller.isVisible = true
    }

    internal fun reveal() {
        animate().cancel()
        alpha = 0f
        isVisible = true
        animate().alpha(1f).setDuration(120).start()
    }

    internal fun dismiss(animated: Boolean, onHidden: () -> Unit) {
        if (!isVisible) return
        animate().cancel()
        removeDetails()
        row.removeAllViews()
        val hide = {
            isVisible = false
            alpha = 1f
            onHidden()
        }
        if (animated) animate().alpha(0f).setDuration(100).withEndAction { hide() }.start()
        else hide()
    }

    private fun tileBackground() = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(panelTextColor, 38)),
        GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            setColor(ColorUtils.blendARGB(surface, panelTextColor, 0.09f))
        },
        null,
    )

    private fun dp(value: Int) = value.dpToPx(resources)
}
