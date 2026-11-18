# Offline Roman Urdu: staged implementation

**Phase 1 passed. Phase 2 compiled, but one of 42 tests failed; the source fix
awaits new testing approval.**
Do not install/release a new build on the strength of earlier test results.
No APK build is currently authorized.

The existing language settings provide two distinct Roman entries alongside the
unchanged ordinary Urdu and English layouts:

- **Urdu (Pakistan) (Roman Urdu: suggestions)**: the default Roman entry preserves
  Latin spelling in the destination app. Choose an Urdu suggestion to replace
  the current word. The strip has best Urdu in the center, with a reliable English
  meaning and curated related Urdu choice in the other logical slots. Missing
  meanings are omitted, not filled with random or blank clickable words. Existing
  RTL/LTR direction is retained; actual screen positioning needs device/UI checks.
- **Urdu (Pakistan) (Roman Urdu: automatic conversion)**: opt in through the same
  language settings. Words stay Roman while composing; confident completed words
  can become Urdu at safe boundaries. This mode is not enabled automatically.

Both entries use the same QWERTY keys. Example candidate groups include
`thik/thek/theek/teek/tik` → `ٹھیک`, with `okay` and `درست` when reliable.
Backspace during composition removes a Roman keystroke. Normal English mode
keeps its existing behavior.

## Privacy and limits

All matching runs on-device; no API, network permission, external model or newly
imported corpus is involved. This is a compact first-party lexicon with spelling,
phonetic, bounded fuzzy and nearby-context heuristics, not complete language
understanding. Confidence/frequency scores are editorial, not calibrated metrics.

Unknown/weak matches and names can stay literal. An ambiguous first word such as
`main` can offer `میں` manually but cannot use future words before they are typed.
Automatic mode does not retroactively rewrite whole sentences. ASCII dots remain
conservative because they can start domains. If background data is not yet ready,
automatic mode keeps the word literal rather than blocking a keypress.

Password and non-general/suggestion-restricted fields do not get Roman conversion.
InputLogic's optional text-gathering hooks and typed-word/context debug logs are
gated off for Roman modes. Existing generic history add/unlearn is bypassed for
these modes until the separate protected correction-learning/removal feature is
implemented. No new user corrections are persisted in this stage.

## Validation status

[Phase-1 report](docs/ROMAN_URDU_PHASE1_TEST_REPORT.md): 26 targeted CI tests passed,
including app/test Kotlin and Java compilation, at commit d329fb8e. That revision
had not connected the new engine to the IME.

The subsequent adapter/IME revision compiled and passed 41 of 42 targeted tests.
The password literal-input test failed. Its source fix and expanded tests have
**not been compiled or retested** and await MODE A approval. See the
[phase-2 report](docs/ROMAN_URDU_PHASE2_TEST_REPORT.md). No emulator or
physical-device UI, accessibility, installation, battery, memory, or latency
validation has been performed for this revision.

See [research and implementation record](docs/ROMAN_URDU_IMPROVEMENT.md) for source
licensing, changed files, rollback, test scope and remaining acceptance work.

AI assistance was used. Retain the project's licenses when distributing builds.
