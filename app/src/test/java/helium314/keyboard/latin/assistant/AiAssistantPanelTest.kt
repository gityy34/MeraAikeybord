// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.awaitCancellation
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AiAssistantPanelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val panel = AiAssistantPanel(context, null, KeyboardTheme.getColorsForCurrentTheme(context))
        .apply { visibility = View.GONE }
    private val scroller get() = panel.getChildAt(0) as HorizontalScrollView
    private val row get() = scroller.getChildAt(0) as LinearLayout

    @Test
    fun enabledToolButtonsDispatchOnlyTheirToolAndClosePreservesTheLayoutState() {
        val selections = mutableListOf<AiTool>()
        panel.bind(listOf(AiTool.GRAMMAR, AiTool.TRANSLATE), { selections.add(it) },
            { panel.dismiss(animated = false) {} })
        panel.reveal()
        assertTrue(panel.visibility == View.VISIBLE)
        assertEquals(2, row.childCount)
        assertEquals(context.getString(R.string.ai_grammar), (row.getChildAt(0) as Button).text.toString())
        assertEquals(context.getString(R.string.ai_translate), (row.getChildAt(1) as Button).text.toString())
        assertTrue(row.getChildAt(1).performClick())
        assertEquals(listOf(AiTool.TRANSLATE), selections)
        assertTrue(panel.getChildAt(1).performClick())
        assertEquals(View.GONE, panel.visibility)
    }

    @Test
    fun narrowAndWideViewportsKeepAllToolsAndTheCloseButtonReachable() {
        panel.bind(AiTool.entries, {}, {})
        panel.reveal()
        val height = 54.dpToPx(context.resources)
        for (width in listOf(220, 640)) {
            val pixels = width.dpToPx(context.resources)
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
            )
            panel.layout(0, 0, pixels, panel.measuredHeight)
            assertEquals(height, panel.measuredHeight)
            assertEquals(AiTool.entries.size, row.childCount)
            val close = panel.getChildAt(1)
            assertTrue(close.left >= scroller.right)
            assertTrue(close.right <= pixels)
            if (width == 220) assertTrue(row.width > scroller.width)
        }
    }

    @Test
    fun turningOffEveryToolLeavesAnExplanationAndAWorkingCloseButton() {
        panel.bind(emptyList(), {}, { panel.dismiss(animated = false) {} })
        panel.reveal()
        assertEquals(1, row.childCount)
        val message = row.getChildAt(0) as TextView
        assertFalse(message.isClickable)
        assertEquals(context.getString(R.string.ai_no_tools), message.text.toString())
        assertTrue(panel.getChildAt(1).performClick())
        assertEquals(View.GONE, panel.visibility)
    }

    @Test
    fun sendRetryCopyApplyUndoAndCancelStayInsideThePanel() {
        val original = "I has a meeting."
        val corrected = "I have a meeting."
        val text = SpannableStringBuilder(original)
        val connection = object : BaseInputConnection(panel, true) {
            override fun getEditable() = text
            override fun beginBatchEdit() = true
            override fun endBatchEdit() = true
        }
        val info = EditorInfo()
        val api = AiApiSettings(context)
        assertNull(api.save(AiApiConfig("https://ai.example.test/keyboard", "fake"), ""))
        val preferences = AiAssistantSettings(context.prefs())
        context.prefs().edit().putBoolean(AiOption.ENABLED.key, true).apply()
        Selection.setSelection(text, 0, text.length)
        var requests = 0
        var stall = false
        var cancelled = false
        lateinit var assistant: AiFeatureManager
        assistant = AiFeatureManager(
            panel, preferences,
            activeEditor = {
                AiEditor(connection, info, Selection.getSelectionStart(text), Selection.getSelectionEnd(text), 0)
            },
            onApplied = { _, _ -> },
            onClose = {
                assistant.clear()
                panel.dismiss(animated = false) {}
            },
            onResize = {},
            onConfigurationChanged = {},
            provider = { request, _, _ ->
                requests++
                if (stall) try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
                AiResponse.Success(corrected, "en", "en", request.feature, emptyList())
            },
        )
        try {
            assistant.showTools()
            panel.reveal()
            button(R.string.ai_grammar).performClick()
            assertEquals(0, requests)
            button(R.string.ai_send).performClick()
            ShadowLooper.idleMainLooper()
            assertEquals(1, requests)
            assertEquals(original, text.toString())
            button(R.string.ai_retry).performClick()
            ShadowLooper.idleMainLooper()
            assertEquals(2, requests)
            button(R.string.ai_copy).performClick()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(corrected, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            assertEquals(original, text.toString())
            button(R.string.ai_apply).performClick()
            assertEquals(corrected, text.toString())
            button(R.string.ai_undo).performClick()
            assertEquals(original, text.toString())
            assertEquals(0, Selection.getSelectionStart(text))
            assertEquals(original.length, Selection.getSelectionEnd(text))
            stall = true
            button(R.string.ai_back_to_tools).performClick()
            button(R.string.ai_grammar).performClick()
            button(R.string.ai_send).performClick()
            ShadowLooper.idleMainLooper()
            assertEquals(3, requests)
            button(android.R.string.cancel).performClick()
            ShadowLooper.idleMainLooper()
            assertTrue(cancelled)
            assertEquals(original, text.toString())
            assertEquals(View.GONE, panel.visibility)
        } finally {
            assistant.clear()
            preferences.reset()
            api.reset()
        }
    }

    private fun button(title: Int): Button {
        val text = context.getString(title)
        fun find(view: View): Button? {
            if (view is Button && view.text.toString() == text) return view
            if (view is ViewGroup && view.visibility == View.VISIBLE) {
                for (index in 0 until view.childCount) {
                    find(view.getChildAt(index))?.let { return it }
                }
            }
            return null
        }
        return requireNotNull(find(panel)) { "Missing panel button: $text" }
    }
}
