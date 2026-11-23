// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.dpToPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AiFeatureManager(
    private val panel: AiAssistantPanel,
    private val preferences: AiAssistantSettings,
    activeEditor: () -> AiEditor?,
    onApplied: (Int, Int) -> Unit,
    private val onClose: () -> Unit,
    private val onResize: () -> Unit,
    private val onConfigurationChanged: (Boolean) -> Unit,
    private val provider: AiProvider = CustomApiProvider(),
) {
    private val context get() = panel.context
    private val api = AiApiSettings(panel.context)
    private val editor = AiTextReplacementManager(activeEditor, onApplied)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null
    private var configForm: AiApiForm? = null

    fun showTools() {
        clear()
        panel.bind(preferences.read().tools, ::options, onClose)
        panel.addSettings(::settings)
        onResize()
    }

    fun clear() {
        pending?.cancel()
        pending = null
        configForm?.let {
            it.cancelWork()
            onConfigurationChanged(false)
        }
        configForm = null
        editor.clear()
    }

    fun isConfiguring() = configForm != null
    fun routeKey(keyCode: Int, codePoint: Int) = configForm?.routeKey(keyCode, codePoint) == true
    fun routeText(text: String) = configForm?.routeText(text) == true

    private fun settings() {
        clear()
        val form = AiApiForm(context, inline = true, api = api) { editor.clear() }
        configForm = form
        onConfigurationChanged(true)
        val body = content(R.string.ai_api_settings)
        body.addAiButton(R.string.ai_back_to_tools, ::showTools)
        body.addView(form)
        display(body)
    }

    private fun options(tool: AiTool) {
        clear()
        val config = api.read()
        if (!config.isValid()) {
            error(AiError.NOT_CONFIGURED) { options(tool) }
            return
        }
        val body = content(tool.option.title)
        val modes = if (config.selectedOnly) listOf(AiTextMode.SELECTED) else AiTextMode.entries
        val mode = body.addAiChoice(R.string.ai_text_scope, modes.map(AiTextMode::title))
        val source = body.addAiChoice(R.string.ai_source_language, AiLanguage.entries.map(AiLanguage::title))
        val targets = listOf(AiLanguage.ENGLISH, AiLanguage.URDU, AiLanguage.ROMAN_URDU)
        val target = if (tool == AiTool.TRANSLATE)
            body.addAiChoice(R.string.ai_target_language, targets.map(AiLanguage::title)) else null
        val tone = if (tool == AiTool.TONE)
            body.addAiChoice(R.string.ai_tone, AiTone.entries.map(AiTone::title)) else null
        body.addView(label(context.getString(R.string.ai_send_disclosure, config.uri()?.host.orEmpty())))
        body.actions(
            R.string.ai_send to {
                val choice = Choice(
                    tool, modes[mode.selectedItemPosition], AiLanguage.entries[source.selectedItemPosition],
                    target?.let { targets[it.selectedItemPosition] } ?: AiLanguage.AUTO,
                    tone?.let { AiTone.entries[it.selectedItemPosition] },
                )
                send(choice, config)
            },
            android.R.string.cancel to onClose,
        )
        display(body)
    }

    private class Choice(
        val tool: AiTool,
        val mode: AiTextMode,
        val source: AiLanguage,
        val target: AiLanguage,
        val tone: AiTone?,
    )

    private fun send(choice: Choice, confirmedConfig: AiApiConfig) {
        if (pending?.isActive == true) return
        val config = api.read()
        val state = preferences.read()
        if (!state.canOpenPanel || choice.tool !in state.tools) {
            showTools()
            return
        }
        if (config != confirmedConfig || !config.isValid() || config.selectedOnly && choice.mode != AiTextMode.SELECTED) {
            options(choice.tool)
            return
        }
        val snapshot = when (val captured = editor.capture(choice.mode, config.maximumLength)) {
            is AiCapture.Ready -> captured.snapshot
            is AiCapture.Failure -> {
                error(captured.error) { options(choice.tool) }
                return
            }
        }
        val request = AiRequest(choice.tool, snapshot.text, choice.source, choice.target, choice.tone)
        val loading = content(R.string.ai_processing)
        loading.addAiButton(android.R.string.cancel, onClose)
        display(loading)
        pending = scope.launch {
            val secret = if (config.authentication == AiAuthentication.NONE) null
                else withContext(Dispatchers.IO) { api.key(config) }
            if (api.read() != config || !editor.isCurrent(snapshot, config.maximumLength)) {
                error(AiError.STALE) { options(choice.tool) }
                return@launch
            }
            val response = provider.request(request, config, secret)
            if (api.read() != config || !editor.isCurrent(snapshot, config.maximumLength)) {
                error(AiError.STALE) { options(choice.tool) }
                return@launch
            }
            when (response) {
                is AiResponse.Success -> preview(snapshot, response, choice, config)
                is AiResponse.Failure -> error(response.error) {
                    if (editor.isCurrent(snapshot, config.maximumLength)) send(choice, config)
                    else options(choice.tool)
                }
                AiResponse.Connected -> error(AiError.INVALID_RESPONSE) { options(choice.tool) }
            }
        }
    }

    private fun preview(snapshot: AiTextSnapshot, result: AiResponse.Success, choice: Choice, config: AiApiConfig) {
        val body = content(R.string.ai_result_preview)
        var chosen = result.resultText
        val alternativeLabel = label(chosen)
        val footer = actionRow(
            R.string.ai_apply to {
                val problem = if (api.read() == config) editor.apply(snapshot, chosen, config.maximumLength)
                    else AiError.STALE
                if (problem == null) applied(choice.tool)
                else error(problem) { options(choice.tool) }
            },
            R.string.ai_copy to { copy(chosen) },
            R.string.ai_retry to {
                if (editor.isCurrent(snapshot, config.maximumLength)) send(choice, config)
                else error(AiError.STALE) { options(choice.tool) }
            },
            android.R.string.cancel to onClose,
        )
        body.addView(label(context.getString(R.string.ai_before)))
        body.addView(label(snapshot.text))
        body.addView(label(context.getString(R.string.ai_after)))
        body.addView(alternativeLabel)
        val alternatives = (listOf(result.resultText) + result.alternatives).distinct()
        if (alternatives.size > 1) {
            var index = 0
            body.addAiButton(R.string.ai_next_alternative) {
                index = (index + 1) % alternatives.size
                chosen = alternatives[index]
                alternativeLabel.text = chosen
            }
        }
        display(body, footer)
    }

    private fun applied(tool: AiTool) {
        val body = content(R.string.ai_applied)
        body.actions(R.string.ai_undo to {
            val failure = editor.undo()
            if (failure == null) {
                val undone = content(R.string.ai_undone)
                undone.addAiButton(R.string.ai_back_to_tools, ::showTools)
                display(undone)
            } else error(failure) { options(tool) }
        }, android.R.string.cancel to onClose)
        display(body)
    }

    private fun error(problem: AiError, retry: () -> Unit) {
        val body = content(problem.message)
        body.actions(R.string.ai_retry to retry, R.string.ai_api_settings to ::settings, android.R.string.cancel to onClose)
        display(body)
    }

    private fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val description = ClipDescription(
            context.getString(R.string.ai_assistant),
            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN, AiPrivacyManager.AI_CLIP_MIME),
        )
        val clip = ClipData(description, ClipData.Item(text))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        if (runCatching { clipboard.setPrimaryClip(clip) }.isFailure) {
            Toast.makeText(context, R.string.ai_copy_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun content(@StringRes heading: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(6.dpToPx(resources), 0, 6.dpToPx(resources), 0)
        addView(label(context.getString(heading)).apply {
            ViewCompat.setAccessibilityLiveRegion(this, View.ACCESSIBILITY_LIVE_REGION_POLITE)
        })
    }

    private fun label(value: String) = TextView(context).apply {
        text = value
        setTextColor(panel.contentColor)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        setPadding(0, 3.dpToPx(resources), 0, 3.dpToPx(resources))
    }

    private fun LinearLayout.actions(vararg actions: Pair<Int, () -> Unit>) {
        addView(actionRow(*actions))
    }

    private fun actionRow(vararg actions: Pair<Int, () -> Unit>): HorizontalScrollView {
        val row = LinearLayout(context)
        for ((name, action) in actions) row.addAiButton(name, action)
        return HorizontalScrollView(context).apply { addView(row) }
    }

    private fun display(body: LinearLayout, footer: View? = null) {
        panel.showDetails(body, footer)
        onResize()
    }
}
