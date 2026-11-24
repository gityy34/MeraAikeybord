// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.content.Context
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.dpToPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AiApiForm(
    context: Context,
    private val inline: Boolean,
    private val api: AiApiSettings,
    private val onClear: () -> Unit,
) : LinearLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null
    private var active: EditText? = null
    private val status = TextView(context)
    private val key: EditText

    init {
        orientation = VERTICAL
        setPaddingRelative(6.dpToPx(resources), 0, 6.dpToPx(resources), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        val saved = api.read()
        label(R.string.ai_custom_provider)
        val endpoint = field(R.string.ai_endpoint, saved.endpoint,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, 600)
        val model = field(R.string.ai_model, saved.model, InputType.TYPE_CLASS_TEXT, 120)
        val authentication = addAiChoice(R.string.ai_authentication, listOf(
            R.string.ai_auth_none, R.string.ai_auth_bearer, R.string.ai_auth_header,
        )).apply { setSelection(saved.authentication.ordinal) }
        key = field(R.string.ai_key, "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 4096)
        key.setHint(R.string.ai_key_hint)
        fun updateAuthentication() {
            val openRouter = AiApiConfig(endpoint = endpoint.text.toString().trim()).isOpenRouter()
            if (openRouter) authentication.setSelection(AiAuthentication.BEARER.ordinal)
            authentication.setSelectable(!openRouter)
        }
        endpoint.doAfterTextChanged {
            updateAuthentication()
            key.setHint(R.string.ai_key_hint)
        }
        updateAuthentication()
        addAiButton(R.string.ai_use_openrouter) {
            if (endpoint.text.toString().trim() != AiApiConfig.OPENROUTER_ENDPOINT) key.text.clear()
            endpoint.setText(AiApiConfig.OPENROUTER_ENDPOINT)
            model.setText(AiApiConfig.OPENROUTER_MODEL)
        }
        val timeout = field(R.string.ai_timeout, saved.timeoutSeconds.toString(), InputType.TYPE_CLASS_NUMBER, 2)
        val limit = field(R.string.ai_maximum, saved.maximumLength.toString(), InputType.TYPE_CLASS_NUMBER, 4)
        val selected = CheckBox(context).apply {
            setText(R.string.ai_selected_only)
            isChecked = saved.selectedOnly
        }
        addView(selected)
        addView(CheckBox(context).apply {
            setText(R.string.ai_secure_fields_always)
            isChecked = true
            isEnabled = false
        })
        label(R.string.ai_connection_status)
        status.setText(if (saved.isValid()) R.string.ai_api_saved else R.string.ai_api_disconnected)
        ViewCompat.setAccessibilityLiveRegion(status, View.ACCESSIBILITY_LIVE_REGION_POLITE)
        addView(status)
        fun readForm() = AiApiConfig(
            endpoint.text.toString().trim(), model.text.toString().trim(),
            AiAuthentication.entries[authentication.selectedItemPosition],
            timeout.text.toString().toIntOrNull() ?: 0,
            limit.text.toString().toIntOrNull() ?: 0, selected.isChecked,
        ).withRequiredAuthentication()

        addAiButton(R.string.ai_save_api) {
            if (pending?.isActive == true) return@addAiButton
            val config = readForm()
            val newKey = key.text.toString().trim()
            pending = scope.launch {
                val problem = withContext(Dispatchers.IO) { api.save(config, newKey) }
                status.setText(problem?.message ?: R.string.ai_api_saved)
                if (problem == null && readForm() == config) {
                    if (key.text.toString().trim() == newKey) key.text.clear()
                    key.setHint(if (config.authentication == AiAuthentication.NONE)
                        R.string.ai_key_hint else R.string.ai_key_saved_hint)
                }
            }
        }
        addAiButton(R.string.ai_test_connection) {
            if (pending?.isActive == true) return@addAiButton
            val config = api.read()
            if (readForm() != config || key.text.isNotBlank() && config.authentication != AiAuthentication.NONE) {
                status.setText(R.string.ai_save_before_test)
                return@addAiButton
            }
            status.setText(R.string.ai_connecting)
            pending = scope.launch {
                val secret = if (config.authentication == AiAuthentication.NONE) null
                    else withContext(Dispatchers.IO) { api.key(config) }
                val response = CustomApiProvider().request(AiRequest(null, ""), config, secret)
                status.setText(when (response) {
                    AiResponse.Connected -> R.string.ai_connected
                    is AiResponse.Failure -> response.error.message
                    else -> R.string.ai_invalid_response
                })
            }
        }
        addAiButton(R.string.ai_clear_history) {
            onClear()
            status.setText(R.string.ai_history_cleared)
        }
    }

    fun routeKey(keyCode: Int, codePoint: Int): Boolean {
        if (!inline) return false
        val field = active ?: return true
        if (keyCode == KeyCode.DELETE) {
            val start = maxOf(0, minOf(field.selectionStart, field.selectionEnd))
            val end = maxOf(start, maxOf(field.selectionStart, field.selectionEnd))
            if (start != end) field.text.delete(start, end)
            else if (start > 0) field.text.delete(Character.offsetByCodePoints(field.text, start, -1), start)
        } else if (Character.isValidCodePoint(codePoint) && !Character.isISOControl(codePoint)) {
            routeText(String(Character.toChars(codePoint)))
        }
        return true
    }

    fun routeText(value: String): Boolean {
        if (!inline) return false
        active?.let { field ->
            val start = maxOf(0, minOf(field.selectionStart, field.selectionEnd))
            val end = maxOf(start, maxOf(field.selectionStart, field.selectionEnd))
            field.text.replace(start, end, value)
        }
        return true
    }

    override fun onDetachedFromWindow() {
        cancelWork()
        super.onDetachedFromWindow()
    }

    fun cancelWork() {
        pending?.cancel()
        active = null
        // Do not retain credential text in an abandoned form.
        key.text.clear()
    }

    private fun label(@StringRes title: Int) {
        addView(TextView(context).apply { setText(title) })
    }

    private fun field(@StringRes title: Int, initial: String, input: Int, length: Int): EditText {
        label(title)
        return EditText(context).apply {
            contentDescription = context.getString(title)
            setSingleLine()
            inputType = input or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            if (input and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_PASSWORD)
                transformationMethod = PasswordTransformationMethod.getInstance()
            setText(initial)
            filters = arrayOf(InputFilter.LengthFilter(length))
            isSaveEnabled = false
            if (inline) {
                showSoftInputOnFocus = false
                setOnFocusChangeListener { _, focused -> if (focused) active = this }
                setOnClickListener { active = this }
            }
        }.also { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
    }
}

internal fun LinearLayout.addAiButton(@StringRes title: Int, action: () -> Unit) =
    Button(context).apply {
        setText(title)
        setAllCaps(false)
        setOnClickListener { action() }
    }.also { addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }

internal fun LinearLayout.addAiChoice(@StringRes label: Int, titles: List<Int>) =
    AiChoice(context, label, titles).also { addView(it) }

internal class AiChoice(context: Context, @StringRes label: Int, private val titles: List<Int>) : LinearLayout(context) {
    var selectedItemPosition: Int = 0
        private set
    private val choices = LinearLayout(context).apply { orientation = VERTICAL }
    private val selection = Button(context).apply {
        setAllCaps(false)
        setOnClickListener { choices.visibility = if (choices.visibility == VISIBLE) GONE else VISIBLE }
    }

    init {
        orientation = VERTICAL
        addView(TextView(context).apply { setText(label) })
        addView(selection)
        addView(choices)
        titles.forEachIndexed { index, title ->
            choices.addAiButton(title) {
                setSelection(index)
                selection.requestFocus()
            }
        }
        setSelection(0)
    }

    fun setSelection(index: Int) {
        selectedItemPosition = index
        selection.setText(titles[index])
        choices.visibility = GONE
    }

    fun setSelectable(selectable: Boolean) {
        selection.isEnabled = selectable
        if (!selectable) choices.visibility = GONE
    }
}
