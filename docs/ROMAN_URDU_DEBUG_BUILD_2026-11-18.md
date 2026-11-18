# Roman Urdu corrected Debug build — 2026-11-18

The owner approved retesting the fix and, after success, building an APK for
personal testing. A Debug build follows that existing personal-test context; no
signed distribution Release or future automatic build permission was granted.

## Provenance and download

- Source commit: **cc8b6b6b4b53f4f0abfd871f6163b30de6736534**.
- Branch: `codex/roman-urdu-improvements`; not merged into main.
- [Test run 44260667257](https://github.com/gityy34/MeraAikeybord/actions/runs/44260667257): completed successfully first, with APK steps skipped.
- [Separate build run 44261292695](https://github.com/gityy34/MeraAikeybord/actions/runs/44261292695): started after that success, exact same source SHA, `run_tests=false`, `build_debug_apk=true`.
- [Download the Debug APK artifact](https://github.com/gityy34/MeraAikeybord/actions/runs/44261292695/artifacts/14408471577).
- Artifact name: `MeraAiKeyboard-RomanUrdu` (ZIP containing one APK).
- Exact APK filename: **HeliBoard_4.3-beta1-debug.apk**.
- Version name/code: **4.3-beta1 / 4300** (inherited project version).
- Package: `helium314.keyboard.debug`; app label: `HeliBoard debug`.
- APK size: **27,299,726 bytes**, approximately 27.3 MB (26.04 MiB).
- Manifest min/target SDK: **21 / 37**. This is declared compatibility, not device testing across all those versions.
- Included native ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.

Automatic approval review rejected submitting tests and build together. No
rejected combined request was executed; tests and the later approved build were
submitted sequentially. No product code changed between them. Later commits
record these results/documentation and are not the source of this APK.

## Test report

CI Kotlin/Java app and test compilation passed. **44 tests executed, 44 passed,
0 failed, 0 skipped.**

| Group | Passed |
| --- | ---: |
| Candidate engine | 16 |
| Android adapter | 4 |
| Roman combiner | 4 |
| Earlier standalone transliterator | 4 |
| Roman editor cases plus selected English input/deletion regressions | 16 |

[Download detailed test reports](https://github.com/gityy34/MeraAikeybord/actions/runs/44260667257/artifacts/14407734214).

The password test now preserves every typed character in both Roman modes and
keeps the word-composer empty. Visible-password/backspace and no-suggestion-field
cases passed. Manual choices, optional conversion, unknown/English/identifier
preservation, incognito/no-learning and stale-candidate checks passed within the
automated coverage. The full upstream suite was not run.

The test runner summary took 19.930 seconds; its Gradle invocation took 3 minutes
32 seconds. The separate `assembleDebug` succeeded in 3 minutes 47 seconds. None
of these are phone latency/battery measurements.

## APK and scoped security verification

The downloaded artifact ZIP digest matches the GitHub artifact digest:

`667dd20ba107bed795f0895b5aff42307bc8e725e9f56b96d0236440e97ee10a`

Extracted APK SHA-256:

`02c78193fcd4c59fda17543270848fc5acce9d29443d82e9759e73ac2ae52818`

- ZIP CRC validation passed. Manifest, DEX, Roman lexicon and optional-auto layout payloads are present.
- Android apksig 9.1.1 verifier returned `verified=true`, v1=true, v2=true, checking platform range 21–37. This is signature validation, not runtime compatibility or installation validation.
- Signer certificate SHA-256: `9372f4cde2c0ba9df5c88342347ebc8520f9b7c4f3ca49c9cb272c0ca7240171`.
- apksig reported 71 informational warnings about generated `META-INF` metadata not covered by the v1/JAR signature. These warnings do not fail verification and are recorded, not suppressed. v2 verification also succeeded; do not describe the entire warning surface as zero.
- AAPT2 inspected the actual built manifest. No `android.permission.INTERNET` is declared. Permission comparison with the previous Debug artifact found **no additions/removals**.
- Existing permissions remain user-dictionary read/write, boot, vibration, contacts, and the app's non-exported receiver permission. Contact access is an inherited feature, not required by the new Roman engine.
- The exported IME service retains `BIND_INPUT_METHOD`; the clipboard provider is not exported. Existing backup/device-protected storage defaults remain; protected Roman correction persistence has not been added.
- No online AI/API, telemetry, new external dictionary/library, secret or signing key was added to the repository by this task. Roman InputLogic history/gathering/text-log gates remain in place.

This is scoped source/manifest/signature validation, not a complete dependency,
network traffic, storage or third-party privacy audit. Some individual Robolectric
reports still include native-dictionary JNI-loading diagnostics; native dictionary
execution is not validated by a JVM result. The APK contains its native ABIs, but
real-device execution remains to be checked. No emulator/phone was available for
installation, on-screen RTL/LTR UI/accessibility, orientation, memory, battery or
low-end typing latency tests. Do not claim installation verification.

## Installation and update caveat

1. Sign into GitHub if prompted, download the artifact ZIP and extract the APK.
2. If the earlier `HeliBoard debug` APK is installed, export/back up its keyboard settings before removing that **test app**. Its signer certificate (`ad8c3b166a4e12189b9446bb78a833d0d95e1e467907e6557c2a8357b63c3152`) differs from this build, so Android will reject an in-place update. Do not remove the ordinary HeliBoard/Gboard app as a substitute.
3. Install the extracted APK using Android's normal installation flow and enable `HeliBoard debug` in keyboard settings.
4. In the keyboard's language settings, choose Urdu (Pakistan), then **Roman Urdu: suggestions** for literal Roman typing plus manual Urdu choices.
5. Add/select **Roman Urdu: automatic conversion** only if you want confident completed words converted. Normal Urdu/English remain separate selections.
6. Check `thik`, `theek`, `main`, manual Urdu/English/related picks, backspace/Enter, language switching and mixed text in your actual messaging apps and airplane mode. Use public dummy text for any password-field check.

A stable private signing setup should be arranged before repeated updates or
public distribution; never publish a signing key or check it into the repository.

## Known limits and next work

This is the current test build, not completion of every Roman Urdu acceptance
criterion. The seed vocabulary and English guard list remain small. Confidence
and frequency are editorial heuristics. Ambiguous first words may remain Roman
without context; no retroactive whole-sentence rewrite is attempted. Automatic
conversion is conservative around ASCII dots, identifiers, restricted fields and
cold data loading. Protected user-correction learning and removal controls are
still pending, so no new personal correction pairs are stored. Next steps are
device feedback/verification, then a separately reviewed and approved protected
learning phase, broader vetted dictionary evaluation, and release preparation.
