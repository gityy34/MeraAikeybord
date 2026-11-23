// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AiAssistantTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("assistant-test", Context.MODE_PRIVATE)
    private val settings get() = AiAssistantSettings(prefs)

    @Test
    fun previewStartsDisabledWithNoToolbarPin() {
        val state = settings.read()
        assertFalse(state.canOpenPanel)
        assertFalse(state.showToolsEntry)
        assertFalse(state.showToolbarIcon)
        assertFalse(state.pinned)
        assertEquals(AiTool.entries.toList(), state.tools)
    }

    @Test
    fun pinAndUnpinSurviveAReopenedSettingsManager() {
        set(AiOption.ENABLED, true)
        assertTrue(settings.read().showToolsEntry)
        assertFalse(settings.read().showToolbarIcon)
        settings.setPinned(true)
        settings.setPinned(true)
        assertTrue(settings.read().showToolbarIcon)
        settings.setPinned(false)
        assertFalse(settings.read().showToolbarIcon)
        assertTrue(settings.read().showToolsEntry)
    }

    @Test
    fun hidingTheToolsEntryKeepsTheSelectedToolbarPin() {
        set(AiOption.ENABLED, true)
        settings.setPinned(true)
        set(AiOption.SHOW_IN_TOOLS, false)
        assertFalse(settings.read().showToolsEntry)
        assertTrue(settings.read().showToolbarIcon)
        set(AiOption.SHOW_IN_TOOLS, true)
        assertTrue(settings.read().showToolsEntry)
    }

    @Test
    fun masterAndPanelSwitchesGateThePreviewAndKeepSavedChoices() {
        set(AiOption.ENABLED, true)
        settings.setPinned(true)
        set(AiOption.SHOW_PANEL, false)
        assertFalse(settings.read().showToolbarIcon)
        assertFalse(settings.read().showToolsEntry)
        set(AiOption.SHOW_PANEL, true)
        set(AiOption.ENABLED, false)
        assertFalse(settings.read().showToolbarIcon)
        assertFalse(settings.read().showToolsEntry)
        assertTrue(settings.read().pinned)
        set(AiOption.ENABLED, true)
        assertTrue(settings.read().showToolbarIcon)
        assertTrue(settings.read().showToolsEntry)
    }

    @Test
    fun perToolSettingsControlWhichActionsAreAvailable() {
        set(AiOption.ENABLED, true)
        for (tool in AiTool.entries) {
            set(tool.option, false)
            assertFalse(tool in settings.read().tools)
            set(tool.option, true)
            assertTrue(tool in settings.read().tools)
        }
    }

    @Test
    fun resettingTheAssistantOnlyResetsItsOwnOptions() {
        prefs.edit { putString("keyboard_preference", "keep") }
        for (option in AiOption.entries) set(option, !option.default)
        settings.reset()
        assertFalse(settings.read().enabled)
        assertFalse(settings.read().pinned)
        assertTrue(settings.read().showInTools)
        assertTrue(settings.read().showPanel)
        assertEquals(AiTool.entries.toList(), settings.read().tools)
        assertEquals(mapOf("keyboard_preference" to "keep"), prefs.all)
    }

    private fun set(option: AiOption, value: Boolean) {
        prefs.edit { putBoolean(option.key, value) }
    }
}
