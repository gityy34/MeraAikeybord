# Roman Urdu (offline prototype)

This fork adds a separate **Urdu (Pakistan) (Roman Urdu)** input method. Enable
it in the keyboard's language settings alongside your existing English and Urdu
layouts, then switch with the language key or spacebar language menu.

Keys use the familiar English QWERTY arrangement; the composing text in the
destination app is Urdu. For example, `aap kaise hain` becomes `آپ کیسے ہیں`.
Conversion runs on the phone and needs no API key, internet permission, or model.

The first version uses a small original seed vocabulary for common words and
longest-match phonetic rules for other spellings. Output updates while typing;
space, punctuation, numbers and Enter finish the current word. Backspace during
composition removes the last Roman keystroke (so `sh` → `s` becomes `ش` → `س`).
English and the original Urdu layout are unchanged.

## Limits and device checks

This is deterministic transliteration, not contextual translation or prediction.
Roman Urdu has ambiguous spellings: `main` and `mein` both map to `میں` in this
seed vocabulary. Unknown words and names can need corrections. Candidate
selection, personal vocabulary and AI features are not part of this version.
Switch to English for literal English words, URLs, email addresses and passwords.

Before using it as your daily keyboard, check on your phone:

- Type `aap kaise hain`, `main theek hoon`, and `mujhe urdu likhna pasand hai`.
- Delete within a word and delete its final keystroke without losing earlier text.
- Add spaces, punctuation, digits, emoji and newlines; switch languages mid-word.
- Move the cursor, replace selected text, paste, and try multiple messaging apps.
- Repeat in airplane mode and compare responsiveness with the original keyboard.

AI assistance was used to implement this fork's Roman Urdu feature. Retain the
existing project licenses when distributing modified builds.

## Validation

The four transliterator JUnit tests pass on the JVM. The four combiner JUnit tests
also pass with the real Event/Combiner sources and small substitutes for Android
dependencies (constants, StringUtils, and SuggestedWordInfo). These isolated tests
do not validate the Android editor or rendering.

The local Gradle build reaches Android project configuration but cannot proceed
without an Android SDK. Three editor integration tests are included in
InputLogicTest. The `Roman Urdu test APK` GitHub workflow runs both the feature
tests and those integration tests before building the debug APK; inspect its
result before installing the artifact. No device performance measurements have
been made for this change yet.
