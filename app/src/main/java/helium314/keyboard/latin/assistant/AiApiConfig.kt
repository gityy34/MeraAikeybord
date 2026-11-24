// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.assistant

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.core.content.edit
import helium314.keyboard.compat.isDeviceLocked
import helium314.keyboard.compat.isUserLocked
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal enum class AiAuthentication {
    NONE, BEARER, API_KEY_HEADER
}

internal data class AiApiConfig(
    val endpoint: String = "",
    val model: String = "",
    val authentication: AiAuthentication = AiAuthentication.NONE,
    val timeoutSeconds: Int = 20,
    val maximumLength: Int = 2_000,
    val selectedOnly: Boolean = true,
) {
    fun uri(): URI? = runCatching { URI(endpoint) }.getOrNull()?.takeIf {
        it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() &&
            it.userInfo == null && it.fragment == null && it.rawQuery == null &&
            (it.port == -1 || it.port in 1..65535)
    }

    fun isValid() = uri() != null && model.isNotBlank() && model.length <= 120 &&
        timeoutSeconds in 3..60 && maximumLength in 1..MAX_LENGTH

    fun isOpenRouter() = usesOpenRouter(uri())

    fun withRequiredAuthentication() = if (isOpenRouter() && authentication != AiAuthentication.BEARER)
        copy(authentication = AiAuthentication.BEARER) else this

    companion object {
        const val MAX_LENGTH = 8_000
        const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        const val OPENROUTER_MODEL = "openrouter/free"

        fun usesOpenRouter(uri: URI?) = uri != null &&
            uri.host.equals("openrouter.ai", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) && uri.rawPath == "/api/v1/chat/completions"
    }
}

internal class AiApiSettings(context: Context) {
    private val prefs = context.prefs()
    private val credentials = AiCredentialStore(context.applicationContext)

    fun read() = AiApiConfig(
        endpoint = prefs.getString(ENDPOINT, "").orEmpty(),
        model = prefs.getString(MODEL, "").orEmpty(),
        authentication = AiAuthentication.entries.firstOrNull { it.name == prefs.getString(AUTH, null) }
            ?: AiAuthentication.NONE,
        timeoutSeconds = prefs.getInt(TIMEOUT, 20),
        maximumLength = prefs.getInt(LIMIT, 2_000),
        selectedOnly = prefs.getBoolean(SELECTED, true),
    ).withRequiredAuthentication()

    fun key(config: AiApiConfig): String? = synchronized(credentialLock) {
        credentials.read(config.withRequiredAuthentication())
    }

    fun save(draft: AiApiConfig, newKey: String): AiError? = synchronized(credentialLock) {
        val config = draft.withRequiredAuthentication()
        if (!config.isValid()) return AiError.NOT_CONFIGURED
        // An unavailable API key must not discard the endpoint, model, or authentication choice.
        val saved = prefs.edit()
            .putString(ENDPOINT, config.endpoint)
            .putString(MODEL, config.model)
            .putString(AUTH, config.authentication.name)
            .putInt(TIMEOUT, config.timeoutSeconds)
            .putInt(LIMIT, config.maximumLength)
            .putBoolean(SELECTED, config.selectedOnly)
            .commit()
        if (!saved) return AiError.SETTINGS_STORAGE

        when {
            config.authentication == AiAuthentication.NONE ->
                if (credentials.clear()) null else AiError.CREDENTIAL_STORAGE
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> AiError.CREDENTIAL_STORAGE
            newKey.isBlank() -> if (credentials.read(config) == null) AiError.KEY_REQUIRED else null
            newKey.length > 4096 || newKey.any { it.code !in 33..126 } -> AiError.AUTHENTICATION
            !credentials.write(config, newKey) -> AiError.CREDENTIAL_STORAGE
            else -> null
        }
    }

    fun reset(): Boolean = synchronized(credentialLock) {
        if (!credentials.clear()) return false
        prefs.edit { keys.forEach { remove(it) } }
        true
    }

    companion object {
        private const val ENDPOINT = "ai_api_endpoint"
        private const val MODEL = "ai_api_model"
        private const val AUTH = "ai_api_authentication"
        private const val TIMEOUT = "ai_api_timeout"
        private const val LIMIT = "ai_api_limit"
        private const val SELECTED = "ai_api_selected_only"
        private val keys = setOf(ENDPOINT, MODEL, AUTH, TIMEOUT, LIMIT, SELECTED)
        private val credentialLock = Any()

        @JvmStatic
        fun handles(key: String?) = key in keys
    }
}

/** Only authenticated ciphertext is stored in the app's backup-excluded directory. */
private class AiCredentialStore(private val context: Context) {
    private val file get() = AtomicFile(File(context.noBackupFilesDir, "assistant-api-key"))
    private val keystore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun clear(): Boolean = runCatching {
        val target = file
        target.delete()
        !target.baseFile.exists()
    }.getOrDefault(false)

    fun read(config: AiApiConfig): String? {
        if (!canUseKey()) return null
        return runCatching {
            val bytes = file.openRead().use { stream ->
                if (stream.channel.size() !in 29L..8192L) return null
                stream.readBytes()
            }
            val key = keystore.getKey(ALIAS, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(binding(config))
            cipher.doFinal(bytes, 12, bytes.size - 12).decodeToString()
        }.getOrNull()
    }

    fun write(config: AiApiConfig, secret: String): Boolean {
        if (!canUseKey()) return false
        return runCatching {
            val key = keystore.getKey(ALIAS, null) as? SecretKey
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build())
                }.generateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
                updateAAD(binding(config))
            }
            check(cipher.iv.size == 12)
            val encrypted = cipher.iv + cipher.doFinal(secret.encodeToByteArray())
            val target = file
            val stream = target.startWrite()
            try {
                stream.write(encrypted)
                target.finishWrite(stream)
            } catch (exception: Exception) {
                target.failWrite(stream)
                throw exception
            }
        }.isSuccess
    }

    private fun canUseKey() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !isUserLocked(context) && !isDeviceLocked(context)

    private fun binding(config: AiApiConfig) = "${config.endpoint}\n${config.authentication.name}".encodeToByteArray()

    companion object {
        private const val ALIAS = "keyboard-ai-assistant-key"
    }
}
