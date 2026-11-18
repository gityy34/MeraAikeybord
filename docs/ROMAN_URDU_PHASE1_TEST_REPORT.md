# Phase 1 approved validation report

Date: 2026-11-18. The owner explicitly approved running the new and existing
Roman Urdu tests and compilation checks. No APK build was approved.

Tested source: **d329fb8e4ae96f16adc90cebd6925871bb3c0aa8**.
Branch: `codex/roman-urdu-improvements`.

## Executed checks

| Check | Result |
| --- | --- |
| Local Kotlin 2.4.0 compilation of the isolated engine and its tests, JVM target 17 | Passed |
| Local JUnit engine tests | 15 passed, 0 failed |
| Local original Java transliterator / Kotlin combiner regression tests | 8 passed, 0 failed; combiner Android dependencies substituted as documented in the earlier prototype |
| CI Android app Kotlin compilation (`compileDebugKotlin`) | Passed |
| CI Android app Java compilation (`compileDebugJavaWithJavac`) | Passed |
| CI Kotlin and Java unit-test compilation | Passed |
| CI targeted unit/Robolectric tests | **26 passed, 0 failed, 0 skipped** |
| APK build and upload | Not authorized; both workflow steps skipped |

The 26 CI tests are 15 engine tests, 4 original transliterator tests, 4 original
combiner tests and 3 original editor integration tests. The original editor tests
still exercised the old prototype. They did **not** exercise new engine-to-IME
integration. The full project's test suite was not run.

CI summary duration for test execution was 14.064 seconds; the entire Gradle
invocation took 3 minutes 47 seconds. These are runner measurements, not phone
typing latency, battery or low-end device performance results.

Workflow: [44254057873](https://github.com/gityy34/MeraAikeybord/actions/runs/44254057873)

Report artifact: [roman-urdu-test-reports](https://github.com/gityy34/MeraAikeybord/actions/runs/44254057873/artifacts/14405341385)

The manually dispatched workflow used `run_tests=true` and
`build_debug_apk=false`; no automatic standing test permission was enabled.

## Security and remaining limits

The engine's malformed-data, directional-control, unknown-word and preservation
checks passed. Source review found no new permission, network, telemetry, secret,
storage or third-party dependency addition in phase 1. This is not a vulnerability
scan of all upstream dependencies or a full application security audit.

No emulator/physical-device installation, on-screen UI/accessibility, orientation,
battery, phone memory or latency tests ran. Password/incognito learning and actual
new suggestion selection require the phase-2/3 implementation and approved tests.

Subsequent phase-2 changes are a **new untested revision**. None of the above
results should be represented as a pass for that newer code.
