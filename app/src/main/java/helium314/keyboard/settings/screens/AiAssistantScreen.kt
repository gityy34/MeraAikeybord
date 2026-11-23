// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.widget.ScrollView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import helium314.keyboard.latin.R
import helium314.keyboard.latin.assistant.AiAssistantSettings
import helium314.keyboard.latin.assistant.AiApiForm
import helium314.keyboard.latin.assistant.AiApiSettings
import helium314.keyboard.latin.assistant.AiOption
import helium314.keyboard.latin.assistant.AiTool
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val API_SETTINGS = "ai_assistant_api_settings"
private const val RESET = "ai_assistant_reset"

@Composable
fun AiAssistantScreen(onClickBack: () -> Unit) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ai_assistant),
        settings = listOf(
            AiOption.ENABLED.key,
            AiOption.SHOW_IN_TOOLS.key,
            AiOption.PINNED.key,
            AiOption.SHOW_PANEL.key,
            R.string.ai_tools,
        ) + AiTool.entries.map { it.option.key } + listOf(API_SETTINGS, RESET),
    )
}

fun createAiAssistantSettings(context: Context): List<Setting> =
    AiOption.entries.map { option ->
        Setting(
            context, option.key, option.title,
            if (option == AiOption.ENABLED) R.string.ai_enable_summary else null,
        ) { setting -> SwitchPreference(setting, option.default) }
    } + listOf(
        Setting(context, API_SETTINGS, R.string.ai_api_settings) { setting ->
            var open by rememberSaveable { mutableStateOf(false) }
            Preference(name = setting.title, onClick = { open = true })
            if (open) ApiSettingsDialog { open = false }
        },
        Setting(context, RESET, R.string.ai_reset, R.string.ai_reset_summary) { setting ->
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var confirming by rememberSaveable { mutableStateOf(false) }
            Preference(name = setting.title, onClick = { confirming = true })
            if (confirming) {
                ConfirmationDialog(
                    title = { Text(setting.title) },
                    content = { Text(stringResource(R.string.ai_reset_summary)) },
                    onDismissRequest = { confirming = false },
                    onConfirmed = {
                        AiAssistantSettings(context.prefs()).reset()
                        scope.launch { withContext(Dispatchers.IO) { AiApiSettings(context).reset() } }
                    },
                )
            }
        },
    )

@Composable
private fun ApiSettingsDialog(close: () -> Unit) {
    val maximumHeight = (LocalConfiguration.current.screenHeightDp * 0.64f).dp
    Dialog(onDismissRequest = close) {
        Surface {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                AndroidView(
                    factory = { context ->
                        ScrollView(context).apply {
                            addView(AiApiForm(context, inline = false, api = AiApiSettings(context)) {})
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(max = maximumHeight),
                )
                TextButton(onClick = close) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}
