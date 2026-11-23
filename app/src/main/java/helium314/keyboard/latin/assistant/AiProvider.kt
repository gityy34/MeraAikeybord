// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import androidx.annotation.StringRes
import helium314.keyboard.latin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume

internal enum class AiError(@StringRes val message: Int) {
    NOT_CONFIGURED(R.string.ai_provider_not_connected),
    OFFLINE(R.string.ai_offline),
    TIMED_OUT(R.string.ai_timed_out),
    INVALID_RESPONSE(R.string.ai_invalid_response),
    AUTHENTICATION(R.string.ai_auth_failed),
    LIMIT(R.string.ai_api_limit),
    CONNECTION(R.string.ai_connection_failed),
    CREDENTIAL_STORAGE(R.string.ai_credential_storage),
    EMPTY(R.string.ai_empty_text),
    TOO_LONG(R.string.ai_text_too_long),
    PRIVATE_FIELD(R.string.ai_private_field),
    STALE(R.string.ai_text_changed),
    EDITOR(R.string.ai_editor_unavailable),
}

internal enum class AiLanguage(val tag: String, @StringRes val title: Int) {
    AUTO("auto", R.string.ai_language_auto),
    ENGLISH("en", R.string.ai_language_english),
    URDU("ur", R.string.ai_language_urdu),
    ROMAN_URDU("ur-Latn", R.string.ai_language_roman_urdu),
    MIXED("mixed", R.string.ai_language_mixed),
}

internal enum class AiTone(@StringRes val title: Int) {
    PROFESSIONAL(R.string.ai_tone_professional), FORMAL(R.string.ai_tone_formal),
    CASUAL(R.string.ai_tone_casual), POLITE(R.string.ai_tone_polite),
    FRIENDLY(R.string.ai_tone_friendly), SHORT(R.string.ai_tone_short), DETAILED(R.string.ai_tone_detailed),
}

internal class AiRequest(
    val tool: AiTool?,
    val inputText: String,
    val source: AiLanguage = AiLanguage.AUTO,
    val target: AiLanguage = AiLanguage.AUTO,
    val tone: AiTone? = null,
) {
    val feature get() = when (tool) {
        AiTool.GRAMMAR -> "grammar_fix"
        AiTool.REWRITE -> "rewrite"
        AiTool.TONE -> "tone_change"
        AiTool.TRANSLATE -> "translate"
        null -> "connection_test"
    }

    val outputLanguage get() = if (tool == AiTool.TRANSLATE) target else source

    fun json(model: String): String = JSONObject().apply {
        put("model", model)
        put("feature", feature)
        put("inputText", inputText)
        put("sourceLanguage", source.tag)
        put("targetLanguage", outputLanguage.tag)
        if (tool == AiTool.TONE && tone != null) put("tone", tone.name.lowercase())
        put("outputFormat", "plain_text")
    }.toString()
}

internal sealed interface AiResponse {
    class Success(
        val resultText: String,
        val detectedLanguage: String,
        val outputLanguage: String,
        val feature: String,
        val alternatives: List<String>,
    ) : AiResponse
    data object Connected : AiResponse
    class Failure(val error: AiError) : AiResponse
}

internal fun interface AiProvider {
    suspend fun request(request: AiRequest, config: AiApiConfig, key: String?): AiResponse
}

internal class CustomApiProvider(private val network: AiNetworkManager = AiNetworkManager()) : AiProvider {
    override suspend fun request(request: AiRequest, config: AiApiConfig, key: String?): AiResponse =
        network.execute(request, config, key)
}

internal class AiNetworkManager {
    suspend fun execute(request: AiRequest, config: AiApiConfig, key: String?): AiResponse {
        val uri = config.uri()
        if (uri == null || !config.isValid()) return AiResponse.Failure(AiError.NOT_CONFIGURED)
        if (request.inputText.length > config.maximumLength) return AiResponse.Failure(AiError.TOO_LONG)
        if (config.authentication != AiAuthentication.NONE && key.isNullOrBlank())
            return AiResponse.Failure(AiError.AUTHENTICATION)

        val timeout = config.timeoutSeconds * 1000
        return withTimeoutOrNull(timeout.toLong()) {
            suspendCancellableCoroutine { continuation ->
                val active = AtomicReference<HttpsURLConnection?>()
                val worker = CoroutineScope(Dispatchers.IO).launch {
                    val response = try {
                        val connection = uri.toURL().openConnection() as HttpsURLConnection
                        active.set(connection)
                        if (!continuation.isActive) return@launch
                        connection.instanceFollowRedirects = false
                        connection.useCaches = false
                        connection.connectTimeout = timeout
                        connection.readTimeout = timeout
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.setRequestProperty("Accept", "application/json")
                        connection.setRequestProperty("Accept-Encoding", "identity")
                        when (config.authentication) {
                            AiAuthentication.NONE -> Unit
                            AiAuthentication.BEARER -> connection.setRequestProperty("Authorization", "Bearer $key")
                            AiAuthentication.API_KEY_HEADER -> connection.setRequestProperty("X-API-Key", key)
                        }
                        val bytes = request.json(config.model).encodeToByteArray()
                        connection.setFixedLengthStreamingMode(bytes.size)
                        connection.outputStream.use { it.write(bytes) }
                        val status = connection.responseCode
                        if (status !in 200..299) AiResponse.Failure(AiErrorHandler.http(status))
                        else connection.inputStream.use { stream ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (continuation.isActive) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > RESPONSE_LIMIT)
                                    throw InvalidAiResponse()
                                output.write(buffer, 0, count)
                            }
                            AiErrorHandler.parse(output.toByteArray().decodeToString(throwOnInvalidSequence = true), request)
                        }
                    } catch (exception: Exception) {
                        AiResponse.Failure(AiErrorHandler.exception(exception))
                    } finally {
                        active.getAndSet(null)?.disconnect()
                    }
                    if (continuation.isActive) continuation.resume(response)
                }
                continuation.invokeOnCancellation {
                    worker.cancel()
                    // Disconnect off the UI thread, including cancellation from the IME.
                    CoroutineScope(Dispatchers.IO).launch { active.getAndSet(null)?.disconnect() }
                }
            }
        } ?: AiResponse.Failure(AiError.TIMED_OUT)
    }

    companion object {
        private const val RESPONSE_LIMIT = 64 * 1024
    }
}

private class InvalidAiResponse : IOException()

internal object AiErrorHandler {
    fun http(status: Int) = when (status) {
        401, 403 -> AiError.AUTHENTICATION
        408, 504 -> AiError.TIMED_OUT
        429 -> AiError.LIMIT
        else -> AiError.CONNECTION
    }

    fun exception(exception: Exception) = when (exception) {
        is UnknownHostException -> AiError.OFFLINE
        is SocketTimeoutException -> AiError.TIMED_OUT
        is InvalidAiResponse, is CharacterCodingException -> AiError.INVALID_RESPONSE
        else -> AiError.CONNECTION
    }

    fun parse(body: String, request: AiRequest): AiResponse = runCatching {
        val feature = request.feature
        val json = JSONObject(body)
        if (json.has("error") && !json.isNull("error")) {
            val code = json.optJSONObject("error")?.optString("code")
            return AiResponse.Failure(when (code) {
                "authentication_failed", "invalid_key" -> AiError.AUTHENTICATION
                "rate_limit", "quota_exceeded" -> AiError.LIMIT
                "timeout" -> AiError.TIMED_OUT
                else -> AiError.CONNECTION
            })
        }
        require(json.opt("feature") == feature)
        if (feature == "connection_test") {
            require(json.opt("connected") == true)
            return AiResponse.Connected
        }
        val text = json.opt("resultText") as? String ?: throw InvalidAiResponse()
        val detected = json.opt("detectedLanguage") as? String ?: throw InvalidAiResponse()
        val output = json.opt("outputLanguage") as? String ?: throw InvalidAiResponse()
        require(languageTag.matches(detected) && languageTag.matches(output))
        val expected = request.outputLanguage.takeUnless { it == AiLanguage.AUTO }?.tag ?: detected
        require(output.equals(expected, ignoreCase = true))
        val roman = expected.equals(AiLanguage.ROMAN_URDU.tag, ignoreCase = true)
        require(isSafeResult(text, roman))
        val alternatives = json.optJSONArray("alternatives")
        AiResponse.Success(
            text,
            detected, output,
            feature,
            (0 until minOf(3, alternatives?.length() ?: 0)).mapNotNull {
                (alternatives?.opt(it) as? String)?.takeIf { isSafeResult(it, roman) }
            },
        )
    }.getOrElse { AiResponse.Failure(AiError.INVALID_RESPONSE) }

    private val languageTag = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8}){0,2}")
    private val arabicLetters = Regex("[\\p{IsArabic}&&\\p{L}]")

    private fun isSafeResult(text: String, roman: Boolean) = text.isNotBlank() &&
        text.length <= AiApiConfig.MAX_LENGTH && (!roman || !arabicLetters.containsMatchIn(text)) &&
        text.none { (it.isISOControl() && it !in "\n\r\t") || it in '\u202a'..'\u202e' || it == '\ufffd' }
}
