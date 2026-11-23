// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import helium314.keyboard.latin.R
import java.util.Locale

internal enum class AiOption(@StringRes val title: Int, val default: Boolean = true) {
    ENABLED(R.string.ai_enable, false),
    SHOW_IN_TOOLS(R.string.ai_show_in_tools),
    PINNED(R.string.ai_pin_to_toolbar, false),
    SHOW_PANEL(R.string.ai_show_panel),
    GRAMMAR(R.string.ai_grammar),
    REWRITE(R.string.ai_rewrite),
    TONE(R.string.ai_tone),
    TRANSLATE(R.string.ai_translate);

    val key = "ai_assistant_" + name.lowercase(Locale.ROOT)
}

internal enum class AiTool(val option: AiOption) {
    GRAMMAR(AiOption.GRAMMAR),
    REWRITE(AiOption.REWRITE),
    TONE(AiOption.TONE),
    TRANSLATE(AiOption.TRANSLATE),
}

internal data class AiAssistantState(
    val enabled: Boolean,
    val showInTools: Boolean,
    val pinned: Boolean,
    val showPanel: Boolean,
    val tools: List<AiTool>,
) {
    val canOpenPanel get() = enabled && showPanel
    val showToolsEntry get() = canOpenPanel && showInTools
    val showToolbarIcon get() = canOpenPanel && pinned
}

internal class AiAssistantSettings(private val prefs: SharedPreferences) {
    fun read() = AiAssistantState(
        enabled = read(AiOption.ENABLED),
        showInTools = read(AiOption.SHOW_IN_TOOLS),
        pinned = read(AiOption.PINNED),
        showPanel = read(AiOption.SHOW_PANEL),
        tools = AiTool.entries.filter { read(it.option) },
    )

    fun setPinned(value: Boolean) {
        prefs.edit { putBoolean(AiOption.PINNED.key, value) }
    }

    fun reset() {
        prefs.edit { AiOption.entries.forEach { remove(it.key) } }
    }

    private fun read(option: AiOption) = prefs.getBoolean(option.key, option.default)

    companion object {
        @JvmStatic
        fun handles(key: String?) = AiOption.entries.any { it.key == key }
    }
}
