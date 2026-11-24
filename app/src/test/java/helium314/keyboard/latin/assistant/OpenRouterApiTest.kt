// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.app.Application
import android.content.Context
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.R
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OpenRouterApiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val config = AiApiConfig(AiApiConfig.OPENROUTER_ENDPOINT, AiApiConfig.OPENROUTER_MODEL)
    private val probe = AiRequest(null, "")

    @Test
    fun validSettingsPersistEvenWhenTheKeyIsMissing() {
        val settings = AiApiSettings(context)
        val selected = config.copy(model = "another/free-model", timeoutSeconds = 30)
        try {
            assertEquals(AiError.KEY_REQUIRED, settings.save(selected, ""))
            val reopened = AiApiSettings(context).read()
            assertEquals(selected.copy(authentication = AiAuthentication.BEARER), reopened)
            assertNull(settings.key(reopened))
            val form = AiApiForm(context, inline = true, api = settings) {}
            assertEquals(selected.endpoint, field(form, R.string.ai_endpoint).text.toString())
            assertEquals(selected.model, field(form, R.string.ai_model).text.toString())
            val choice = (0 until form.childCount).map(form::getChildAt).filterIsInstance<AiChoice>().single()
            assertEquals(AiAuthentication.BEARER.ordinal, choice.selectedItemPosition)
            assertFalse(choice.getChildAt(1).isEnabled)
        } finally {
            settings.reset()
        }
    }

    @Test
    fun presetFillsTheRequestedConfigurationAndDismissalClearsOnlyTheSecret() {
        val form = AiApiForm(context, inline = true, api = AiApiSettings(context)) {}
        val preset = (0 until form.childCount).map(form::getChildAt).filterIsInstance<Button>()
            .single { it.text == context.getString(R.string.ai_use_openrouter) }
        assertTrue(preset.performClick())
        val endpoint = field(form, R.string.ai_endpoint)
        val model = field(form, R.string.ai_model)
        val key = field(form, R.string.ai_key)
        key.setText("test-only-secret")
        form.cancelWork()
        assertEquals(config.endpoint, endpoint.text.toString())
        assertEquals(config.model, model.text.toString())
        assertTrue(key.text.isEmpty())
    }

    @Test
    fun testConnectionUsesTheExactOpenRouterBodyAndBearerHeader() = runBlocking {
        var connection: FakeConnection? = null
        val provider = CustomApiProvider(AiNetworkManager { url ->
            assertEquals(config.endpoint, url.toString())
            FakeConnection(url, completion("OK")).also { connection = it }
        })
        val key = "test-only-secret"
        assertEquals(AiResponse.Connected, provider.request(probe, config, key))
        val sent = checkNotNull(connection)
        assertEquals("POST", sent.requestMethod)
        assertEquals("Bearer $key", sent.getRequestProperty("Authorization"))
        assertEquals("application/json", sent.getRequestProperty("Content-Type"))
        assertFalse(sent.instanceFollowRedirects)
        val json = JSONObject(sent.payload.toString(Charsets.UTF_8.name()))
        assertEquals(setOf("model", "messages", "temperature", "max_completion_tokens"), json.keys().asSequence().toSet())
        assertEquals("openrouter/free", json.getString("model"))
        assertEquals(0, json.getInt("temperature"))
        assertEquals(16, json.getInt("max_completion_tokens"))
        val messages = json.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("Reply with only: OK", messages.getJSONObject(0).getString("content"))
    }

    @Test
    fun openRouterCannotSendWithoutBearerAndErrorsKeepTheirSpecificStatus(): Unit = runBlocking {
        var sent = false
        val network = AiNetworkManager { url ->
            sent = true
            FakeConnection(url, "", 401)
        }
        val missing = assertIs<AiResponse.Failure>(network.execute(probe, config, null))
        assertEquals(AiError.KEY_REQUIRED, missing.error)
        assertFalse(sent)
        val rejected = assertIs<AiResponse.Failure>(network.execute(probe, config, "test-only-secret"))
        assertEquals(AiError.AUTHENTICATION, rejected.error)
        assertTrue(sent)
        val limited = assertIs<AiResponse.Failure>(OpenRouterChat.parse("""{"error":{"code":429}}""", probe))
        assertEquals(AiError.LIMIT, limited.error)
        assertIs<AiResponse.Failure>(OpenRouterChat.parse(completion("Anything else"), probe))
    }

    @Test
    fun chatRequestsPreserveToolsAndOnlyReturnValidCompletionContent() {
        for (tool in AiTool.entries) {
            val target = if (tool == AiTool.TRANSLATE) AiLanguage.URDU else AiLanguage.AUTO
            val tone = if (tool == AiTool.TONE) AiTone.POLITE else null
            val request = AiRequest(tool, "hello", AiLanguage.ENGLISH, target, tone)
            val messages = JSONObject(OpenRouterChat.encode(request, config.model)).getJSONArray("messages")
            assertEquals("system", messages.getJSONObject(0).getString("role"))
            assertEquals("hello", messages.getJSONObject(1).getString("content"))
            val expected = if (tool == AiTool.TRANSLATE) "سلام" else "Hello"
            val parsed = assertIs<AiResponse.Success>(OpenRouterChat.parse(completion(expected), request))
            assertEquals(expected, parsed.resultText)
            assertEquals(request.feature, parsed.feature)
            assertIs<AiResponse.Failure>(OpenRouterChat.parse(completion(expected, "length"), request))
        }
        val roman = AiRequest(AiTool.TRANSLATE, "hello", AiLanguage.ENGLISH, AiLanguage.ROMAN_URDU)
        assertIs<AiResponse.Failure>(OpenRouterChat.parse(completion("سلام"), roman))
        assertIs<AiResponse.Success>(OpenRouterChat.parse(completion("salaam"), roman))
    }

    @Test
    fun changingTheHostKeepsTheCustomApiContract() = runBlocking {
        val custom = config.copy(endpoint = "https://example.org/api/v1/chat/completions", model = "custom")
        var connection: FakeConnection? = null
        val network = AiNetworkManager { url ->
            FakeConnection(url, """{"feature":"connection_test","connected":true}""").also { connection = it }
        }
        assertEquals(AiResponse.Connected, network.execute(probe, custom, null))
        val sent = checkNotNull(connection)
        assertNull(sent.getRequestProperty("Authorization"))
        assertEquals("connection_test", JSONObject(sent.payload.toString()).getString("feature"))
        assertFalse(config.copy(endpoint = "https://openrouter.ai.example.org/api/v1/chat/completions").isOpenRouter())
        assertFalse(config.copy(endpoint = "http://openrouter.ai/api/v1/chat/completions").isOpenRouter())
    }

    private fun field(form: AiApiForm, label: Int) =
        (0 until form.childCount).map(form::getChildAt).filterIsInstance<EditText>()
            .single { it.contentDescription == context.getString(label) }

    private fun completion(text: String, reason: String = "stop") =
        """{"choices":[{"finish_reason":"$reason","message":{"content":${JSONObject.quote(text)}}}]}"""

    private class FakeConnection(url: URL, private val response: String, private val status: Int = 200) :
        HttpsURLConnection(url) {
        val payload = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getOutputStream() = payload
        override fun getInputStream() = response.byteInputStream()
        override fun getCipherSuite() = ""
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
