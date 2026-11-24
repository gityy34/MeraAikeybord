# Keyboard AI Assistant Phase 2

## Setup

Enable AI Assistant, open it from Tools or its pinned toolbar icon, and open **API and privacy settings** in the keyboard panel. Enter the HTTPS endpoint and model, choose an authentication method, then save. Settings are also available from the app’s existing AI Assistant settings.

Custom endpoints can use no authentication, an `Authorization: Bearer` token, or an `X-API-Key` header. Android 6 and newer can store keys with Android Keystore AES-GCM. Android 5 and 5.1 support endpoints without authentication. The encrypted file is excluded from Android backups and bound to the exact endpoint and authentication method. The app only reads or writes keys while the user and device are unlocked. The normal keyboard works offline even when the AI backend is unreachable.

Save keeps a valid endpoint, model and authentication setting even if the key needs to be entered again. After saving a key, the masked entry field clears and displays a saved-key hint. **Test Connection** checks the saved configuration and requires saving any edits first. Opening the keyboard or saving settings does not send a request.

## OpenRouter

Choose **Use OpenRouter settings**, paste the API key itself, select **Save API settings**, then select **Test Connection**. The preset uses `https://openrouter.ai/api/v1/chat/completions` and `openrouter/free`. The app requires Bearer authentication for this endpoint. A model explicitly entered in the form is saved as entered.

The connection test sends a JSON POST with `Authorization: Bearer <API_KEY>` and `Content-Type: application/json`. For the preset model, the request is

```json
{
  "model": "openrouter/free",
  "messages": [{"role": "user", "content": "Reply with only: OK"}],
  "temperature": 0,
  "max_completion_tokens": 16
}
```

The app expects `OK` in `choices[0].message.content`. The probe sends no editor text. Transformations send the chosen source text as the user message and use the returned content for the preview. The app only applies the result after confirmation. [OpenRouter chat completion reference](https://openrouter.ai/docs/api/api-reference/chat/create-a-chat-completion)

## Other custom endpoints

The custom provider sends a JSON POST to the configured HTTPS URL. A provider-specific API needs an adapter if it uses a different schema. The keyboard follows standard Android certificate validation and rejects redirects. An example transformation request is

```json
{
  "model": "keyboard-model",
  "feature": "grammar_fix",
  "inputText": "I has a meeting tomorrow.",
  "sourceLanguage": "en",
  "targetLanguage": "en",
  "outputFormat": "plain_text"
}
```

| Field | Values |
| --- | --- |
| `feature` | `grammar_fix`, `rewrite`, `tone_change`, `translate` or `connection_test` |
| `sourceLanguage` | `auto`, `en`, `ur`, `ur-Latn` or `mixed` |
| `targetLanguage` | One of `en`, `ur` or `ur-Latn` for translation. For other tools the app sends the selected source language, including `auto` or `mixed`. |
| `tone` | `professional`, `formal`, `casual`, `polite`, `friendly`, `short` or `detailed`. Sent only for `tone_change`. |

Grammar should preserve meaning and tone, rewrite should preserve meaning, and tone changes should follow the chosen option. Translation should preserve meaning and tone in the chosen target. When `targetLanguage` is `auto`, preserve the detected input language and script. For `mixed` preserve the mixture of languages, including Latin-script Roman Urdu when present. Use the language tags above consistently in the response.

```json
{
  "feature": "grammar_fix",
  "resultText": "I have a meeting tomorrow.",
  "detectedLanguage": "en",
  "outputLanguage": "en",
  "alternatives": ["I have a meeting scheduled for tomorrow."]
}
```

For a connection test, the app sends an empty `inputText` and expects `{"feature":"connection_test","connected":true}`. On failure, use HTTP 401 or 403 for invalid credentials, 429 for quotas, or 408 or 504 for timeout. The provider also recognizes structured JSON errors with an `error.code` of `invalid_key`, `authentication_failed`, `rate_limit`, `quota_exceeded`, or `timeout`. Server error strings are never shown or logged by the app.

## Editor and privacy boundaries

- Selected text is the initial scope and the default restriction. Turning off **Use selected text only** enables current word, current sentence, text before the cursor, and full current draft. Opening the tool menu or choosing an option does not read the draft or send a request. **Send** shows the destination and starts a request for the chosen scope. **Retry** can resend when the source still matches.
- Source and result are kept in memory while the panel is open. There is no saved AI request history. **Clear Local AI History** clears temporary data in the panel. Copy explicitly puts the result on the Android clipboard with the sensitive flag where available. The keyboard’s own clipboard history skips this result.
- Apply verifies the active editor, selection, and source text. It uses a batch edit and verified selection replacement through Android `InputConnection`. An editor that provides incomplete information or refuses any required edit is reported as unavailable. Undo checks that the cursor and applied text still match before restoring the original text and selection.
- Password, numeric, incognito and Android private-input contexts are blocked. Local checks also reject likely PINs, payment card numbers and labelled credentials. Applications can mislabel a field or place sensitive text in an ordinary editor. The Send disclosure remains the user's control over what is shared with their configured provider.
- The default timeout is 20 seconds and the default input limit is 2,000 UTF-16 code units. Configuration can raise these to 60 seconds and 8,000 code units. The response is limited to 64 KiB, with a maximum result and alternative length of 8,000 code units. Closing the panel cancels pending work and clears the preview. It cannot recall text the provider has already received.

Relevant Android APIs include [InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection), [EditorInfo](https://developer.android.com/reference/android/view/inputmethod/EditorInfo) and the [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

## Verification to perform after approval

Run the existing regression suite and the Phase 2 provider, text-boundary and panel tests. The mock uses the same structured response parser with synthetic English, Urdu, Roman Urdu and mixed examples. Verify the inline settings, secure storage across restart, and the whole flow in portrait and landscape on an emulator. Exercise a compatible HTTPS test endpoint with synthetic input for authentication failures, network timeout, invalid and oversized responses, and disconnection during processing. Check actual transformation quality separately on the configured model.
