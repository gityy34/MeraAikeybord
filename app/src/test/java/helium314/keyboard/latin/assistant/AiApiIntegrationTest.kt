// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.app.Application
import android.content.Context
import android.text.InputType
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AiApiIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val config = AiApiConfig("https://ai.example.test/keyboard", "keyboard-test-model")

    @Test
    fun configurationRequiresHttpsAndRejectsCredentialsInAddresses() {
        assertTrue(config.isValid())
        for (endpoint in listOf("http://ai.example.test", "https://name:secret@ai.example.test/api",
            "https://ai.example.test?key=abc", "https://ai.example.test#fragment", "https:///api")) {
            assertFalse(config.copy(endpoint = endpoint).isValid(), endpoint)
        }
        assertFalse(config.copy(timeoutSeconds = 2).isValid())
        assertFalse(config.copy(maximumLength = AiApiConfig.MAX_LENGTH + 1).isValid())
    }

    @Test
    fun configurationPersistsNonsecretPreferencesAndResetRestoresSelectedOnly() {
        val edited = config.copy(selectedOnly = false, maximumLength = 1800, timeoutSeconds = 8)
        val settings = AiApiSettings(context)
        assertNull(settings.save(edited, ""))
        assertEquals(edited, AiApiSettings(context).read())
        assertNull(AiApiSettings(context).key(edited))
        assertTrue(settings.reset())
        assertTrue(settings.read().selectedOnly)
        assertFalse(settings.read().isValid())
    }

    @Test
    fun requestOnlyIncludesTextAndTheChosenToolOptions() {
        val request = AiRequest(AiTool.TONE, "kal miltay hain", AiLanguage.ROMAN_URDU, tone = AiTone.POLITE)
        val json = JSONObject(request.json(config.model))
        assertEquals(
            setOf("feature", "inputText", "model", "sourceLanguage", "targetLanguage", "tone", "outputFormat"),
            json.keys().asSequence().toSet(),
        )
        assertEquals("tone_change", json.getString("feature"))
        assertEquals("ur-Latn", json.getString("sourceLanguage"))
        assertEquals("ur-Latn", json.getString("targetLanguage"))
        assertEquals("polite", json.getString("tone"))
        assertEquals("kal miltay hain", json.getString("inputText"))

        val probe = AiRequest(null, "")
        assertEquals("", JSONObject(probe.json(config.model)).getString("inputText"))
        assertIs<AiResponse.Connected>(AiErrorHandler.parse("""{"feature":"connection_test","connected":true}""", probe))
    }

    @Test
    fun responseValidationRejectsMalformedEmptyAndUnsafeResults() {
        val request = AiRequest(AiTool.GRAMMAR, "I has a meeting", AiLanguage.ENGLISH)
        val inputs = listOf(
            "{",
            mockJson("rewrite", "I have a meeting.", "en", "en"),
            mockJson(request.feature, "", "en", "en"),
            mockJson(request.feature, "I have a meeting.\u202e", "en", "en"),
            mockJson(request.feature, "a".repeat(8001), "en", "en"),
            mockJson(request.feature, "I have a meeting.", "", "en"),
            mockJson(request.feature, "میری میٹنگ ہے۔", "en", "ur"),
        )
        for (invalid in inputs) {
            assertEquals(AiError.INVALID_RESPONSE, assertIs<AiResponse.Failure>(AiErrorHandler.parse(invalid, request)).error)
        }
        val roman = AiRequest(AiTool.REWRITE, "mera naam", AiLanguage.ROMAN_URDU)
        val wrongScript = mockJson(roman.feature, "میرا نام", "ur-Latn", "ur-Latn")
        assertEquals(AiError.INVALID_RESPONSE, assertIs<AiResponse.Failure>(AiErrorHandler.parse(wrongScript, roman)).error)
        assertFalse(request.toString().contains(request.inputText))
    }

    @Test
    fun authenticationTimeoutOfflineAndQuotaErrorsHaveSafeStatuses() {
        assertEquals(AiError.AUTHENTICATION, AiErrorHandler.http(401))
        assertEquals(AiError.LIMIT, AiErrorHandler.http(429))
        assertEquals(AiError.TIMED_OUT, AiErrorHandler.exception(SocketTimeoutException()))
        assertEquals(AiError.OFFLINE, AiErrorHandler.exception(UnknownHostException()))
        val request = AiRequest(null, "")
        val failure = assertIs<AiResponse.Failure>(AiErrorHandler.parse("""{"error":{"code":"invalid_key"}}""", request))
        assertEquals(AiError.AUTHENTICATION, failure.error)
    }

    @Test
    fun mockProviderUsesTheSameProtocolForEnglishUrduRomanUrduAndMixedTools() = runBlocking {
        val examples = listOf(
            Example(AiTool.GRAMMAR, AiLanguage.ENGLISH, "I has a meeting.", "I have a meeting."),
            Example(AiTool.GRAMMAR, AiLanguage.URDU, "ہم گھر جاتا ہیں۔", "ہم گھر جاتے ہیں۔"),
            Example(AiTool.GRAMMAR, AiLanguage.ROMAN_URDU, "hum ghar jata hain", "Hum ghar jatay hain."),
            Example(AiTool.REWRITE, AiLanguage.ENGLISH, "I want the report soon.", "Please send the report soon."),
            Example(AiTool.REWRITE, AiLanguage.URDU, "رپورٹ جلد چاہیے۔", "براہ کرم رپورٹ جلد بھیج دیں۔"),
            Example(AiTool.REWRITE, AiLanguage.ROMAN_URDU, "report jaldi", "Report jaldi bhej dein."),
            Example(AiTool.TONE, AiLanguage.MIXED, "Meeting پر آنا۔", "Please meeting پر آ جائیں۔"),
            Example(AiTool.TRANSLATE, AiLanguage.ENGLISH, "Where is the house?", "گھر کہاں ہے؟", AiLanguage.URDU),
            Example(AiTool.TRANSLATE, AiLanguage.URDU, "گھر کہاں ہے؟", "Where is the house?", AiLanguage.ENGLISH),
            Example(AiTool.TRANSLATE, AiLanguage.URDU, "گھر کہاں ہے؟", "Ghar kahan hai?", AiLanguage.ROMAN_URDU),
        )
        for (case in examples) {
            val request = AiRequest(case.tool, case.input, case.source, case.target, AiTone.POLITE)
            val result = assertIs<AiResponse.Success>(MockAiProvider(case.result).request(request, config, null))
            assertEquals(case.result, result.resultText)
            assertEquals(request.outputLanguage.tag, result.outputLanguage)
        }
    }

    @Test
    fun privacyBlocksAndroidSecureTypesAndLikelyCredentialsWithoutReadingTheHost() {
        val text = InputType.TYPE_CLASS_TEXT
        for (type in listOf(
            text or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_NUMBER,
        )) assertFalse(AiPrivacyManager.permits(type, 0, false, false))
        assertTrue(AiPrivacyManager.permits(text, 0, false, false))
        assertFalse(AiPrivacyManager.permits(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false, false))
        assertFalse(AiPrivacyManager.permits(text, 0, true, false))
        assertFalse(AiPrivacyManager.permits(text, 0, false, true))
        for (private in listOf("1234", "Card: 0000-0000-0000-0000", "api key: fake")) {
            val fixture = EditorFixture(private, 0, private.length)
            assertEquals(AiError.PRIVATE_FIELD, fixture.error(AiTextMode.SELECTED))
        }
        val incognito = EditorFixture("private text", 0, 12).apply { active = false }
        assertEquals(AiError.PRIVATE_FIELD, incognito.error(AiTextMode.SELECTED))
        assertEquals(0, incognito.connection.selectedReads)
    }

    @Test
    fun selectedModeNeverReadsSurroundingsAndRejectsOversizedSelectionBeforeReading() {
        val fixture = EditorFixture("Unselected. منتخب حصہ۔ More.", 12, 22)
        val selected = fixture.snapshot(AiTextMode.SELECTED)
        assertEquals(12, selected.rangeStart)
        assertEquals("منتخب حصہ۔", selected.text)
        assertEquals(0, fixture.connection.surroundingReads)
        val large = EditorFixture("abcdefghijklmnopqrstuvwxyz", 0, 26)
        assertEquals(AiError.TOO_LONG, large.error(AiTextMode.SELECTED, maximum = 10))
        assertEquals(0, large.connection.selectedReads)
    }

    @Test
    fun wordSentenceAndExplicitDraftScopesRespectBoundaries() {
        val english = EditorFixture("Hello world! Next.", 8)
        assertEquals("world", english.snapshot(AiTextMode.WORD).text)
        assertEquals("Hello world!", english.snapshot(AiTextMode.SENTENCE).text)
        assertEquals("Hello wo", english.snapshot(AiTextMode.BEFORE_CURSOR).text)
        assertEquals("Hello world! Next.", english.snapshot(AiTextMode.DRAFT).text)

        val urdu = "پہلا جملہ۔ کیا حال ہے؟  اگلا۔"
        val fixture = EditorFixture(urdu, urdu.indexOf("اگلا"))
        assertEquals("کیا حال ہے؟", fixture.snapshot(AiTextMode.SENTENCE).text)
        assertEquals(AiError.EMPTY, fixture.error(AiTextMode.SELECTED))
        val selected = EditorFixture("abc def ghi", 4, 7)
        assertEquals("abc def ghi", selected.snapshot(AiTextMode.DRAFT).text)
        assertEquals(AiError.TOO_LONG, selected.error(AiTextMode.DRAFT, maximum = 8))
        assertEquals(AiError.EMPTY, EditorFixture("").error(AiTextMode.DRAFT))
    }

    @Test
    fun applyAndUndoReplaceOnlyTheRequestedPartAndRestoreTheCursor() {
        val examples = listOf("The house" to "A home", "کیا حال ہیں" to "کیا حال ہے", "mera ghar" to "Mera ghar")
        for ((input, output) in examples) {
            val fixture = EditorFixture("A $input B", 2, 2 + input.length)
            val original = fixture.snapshot(AiTextMode.SELECTED)
            assertEquals("A $input B", fixture.text)
            assertNull(fixture.manager.apply(original, output, 500))
            assertEquals("A $output B", fixture.text)
            assertEquals(2 + output.length, fixture.start)
            assertEquals(fixture.start, fixture.end)
            assertNull(fixture.manager.undo())
            assertEquals("A $input B", fixture.text)
            assertEquals(2, fixture.start)
            assertEquals(2 + input.length, fixture.end)
            assertEquals(AiError.STALE, fixture.manager.undo())
        }
    }

    @Test
    fun changingTextFieldOrSelectionRejectsStaleApplyAndMovingCursorRejectsUndo() {
        val fixture = EditorFixture("The house", 0, 9)
        val original = fixture.snapshot(AiTextMode.SELECTED)
        fixture.connection.content.replace(0, 9, "The world")
        fixture.select(0, 9, invalidate = false)
        assertEquals(AiError.STALE, fixture.manager.apply(original, "A home", 500))
        assertEquals("The world", fixture.text)
        val fresh = fixture.snapshot(AiTextMode.SELECTED)
        fixture.revision++
        assertEquals(AiError.STALE, fixture.manager.apply(fresh, "A world", 500))
        val newer = fixture.snapshot(AiTextMode.SELECTED)
        fixture.active = false
        assertEquals(AiError.STALE, fixture.manager.apply(newer, "A world", 500))
        fixture.active = true
        assertNull(fixture.manager.apply(newer, "A world", 500))
        fixture.select(0)
        assertEquals(AiError.STALE, fixture.manager.undo())
        assertEquals("A world", fixture.text)
    }

    private inner class EditorFixture(value: String, from: Int = 0, to: Int = from) {
        val connection = RecordingEditor(View(context), value)
        private val info = EditorInfo()
        val start get() = Selection.getSelectionStart(connection.content)
        val end get() = Selection.getSelectionEnd(connection.content)
        var revision = 0L
        var active = true
        val text get() = connection.content.toString()
        val manager = AiTextReplacementManager(
            activeEditor = { if (active) AiEditor(connection, info, start, end, revision) else null },
            onApplied = { _, _ -> revision++ },
        )

        init { select(from, to, invalidate = false) }

        fun select(start: Int, end: Int = start, invalidate: Boolean = true) {
            connection.setSelection(start, end)
            if (invalidate) revision++
        }

        fun snapshot(mode: AiTextMode) = assertIs<AiCapture.Ready>(manager.capture(mode, 500)).snapshot
        fun error(mode: AiTextMode, maximum: Int = 500) =
            assertIs<AiCapture.Failure>(manager.capture(mode, maximum)).error
    }

    private class RecordingEditor(view: View, value: String) : BaseInputConnection(view, true) {
        val content = SpannableStringBuilder(value)
        var selectedReads = 0
        var surroundingReads = 0
        override fun getEditable() = content
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true
        override fun getSelectedText(flags: Int): CharSequence? {
            selectedReads++
            return super.getSelectedText(flags)
        }
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
            surroundingReads++
            return super.getTextBeforeCursor(n, flags)
        }
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
            surroundingReads++
            return super.getTextAfterCursor(n, flags)
        }
    }

    private data class Example(
        val tool: AiTool,
        val source: AiLanguage,
        val input: String,
        val result: String,
        val target: AiLanguage = AiLanguage.AUTO,
    )
}

private fun mockJson(feature: String, result: String, detected: String, output: String) = JSONObject()
    .put("feature", feature).put("resultText", result).put("detectedLanguage", detected)
    .put("outputLanguage", output).toString()

private class MockAiProvider(private val fixture: String) : AiProvider {
    override suspend fun request(request: AiRequest, config: AiApiConfig, key: String?) = AiErrorHandler.parse(
        mockJson(request.feature, fixture, request.source.tag, request.outputLanguage.tag), request,
    )
}
