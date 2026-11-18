# Phase 2 approved validation and pending fix

**Historical report:** the fix below was subsequently approved and validated at
cc8b6b6b (44/44 passed); an approved Debug APK followed. See
[current results and download](ROMAN_URDU_DEBUG_BUILD_2026-11-18.md). The old
failure/results below are retained for provenance, not the current status.

Date: 2026-11-18. Owner approval covered phase-2 compilation, Roman Urdu tests,
and selected English regression tests. APK building was not approved.

Tested commit: **cdc87d6e1b889e44cc00085f3880acc70cba77b1**.

Workflow: [44257607285](https://github.com/gityy34/MeraAikeybord/actions/runs/44257607285)

Report artifact: [roman-urdu-test-reports](https://github.com/gityy34/MeraAikeybord/actions/runs/44257607285/artifacts/14407080766)

## Executed results (before the fix below)

| Check | Result |
| --- | --- |
| Local isolated engine Kotlin/JVM compilation | Passed |
| Local engine JUnit tests | 16 passed |
| Local original transliterator / revised combiner tests | 8 passed; the combiner run used Android substitutes as previously documented |
| CI Android app Kotlin and Java compilation | Passed |
| CI Kotlin and Java test compilation | Passed |
| CI engine tests | 16 passed |
| CI adapter tests | 4 passed |
| CI combiner tests | 4 passed |
| CI original transliterator tests | 4 passed |
| CI editor / selected English tests | 13 passed, 1 failed |
| CI total | **42 executed, 41 passed, 1 failed, 0 skipped** |
| APK build/upload steps | Both skipped |

The test summary measured 17.324 seconds on the runner; Gradle took 3 minutes
2 seconds. These are not phone typing latency, memory or battery measurements.
No physical-device/emulator UI, accessibility, installation, orientation or
low-end performance checks ran. The full upstream test suite was not run.

## Actual failure and source diagnosis

`InputLogicTest.romanUrduPasswordFieldRemainsLiteral` expected the literal public
test fixture `thik `, but only a space remained. The test was not weakened or
removed.

The Roman combiner retained the word and submitted composing text even when the
field disallowed suggestions. InputLogic's existing selection-update path resets
the entire composing state when `needsToLookupSuggestions()` is false. Logs showed
the composing prefix being discarded/replaced during input. The general literal
typing path avoids composition in precisely these fields; the new Roman path
had bypassed that protection. This is an input-integrity/privacy-path bug, not
evidence of networking or data transmission.

## Small source fix (NOT compiled or tested yet)

- `InputLogic.java`: track the selected Roman mode separately from the active
  combiner. At input start, disable the Roman combiner when the field is not
  eligible or suggestions/composition are disabled. Use existing direct literal
  character commits. Keep the Roman privacy gates active for these fields, so
  history add/unlearn, optional gathering hooks and typed-word/context logs do
  not become enabled by bypassing the combiner. Other language modes keep their
  original combining specifications.
- `RomanUrduAdapter.kt`: when field access is denied, do not slice or interpret
  the supplied editor-context snapshot at all.
- `InputLogicTest.kt`: strengthen the password test to check every character and
  empty word-composer state in both Roman modes. Add visible-password deletion
  and no-suggestion-field literal-input regression cases.

The original password assertion still needs to pass after this fix. No tests or
compilation were rerun on the changed revision because MODE A requires renewed
approval after a logical fix. Request the same targeted suite plus the new
restricted-field cases; keep the APK flag false. Do not describe the fix as
validated before that run completes.

## Additional harness diagnostics and limits

The CI report also contains `UnsatisfiedLinkError` from asynchronous loading of
the existing English native dictionary in the Robolectric JVM. The job logs print
two `NullPointerException` messages from coroutine-thread uncaught handlers near
teardown without a detailed stack. These diagnostics were not suppressed or
counted as successful native-dictionary validation. The precise origin of the
handler messages is not established. All 42 test results were recorded; the
explicit failing assertion was the password input loss above. Investigate any
repeat after the fix, keeping harness/native coverage distinct from app behavior.

No new permissions, network service, corpus, external library, secret or
persistent correction storage was added by the fix. A complete upstream
dependency/logging security audit and device-level privacy checks remain outside
the executed coverage. Protected correction learning/removal remains pending.
