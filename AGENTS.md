# Project instructions

These are the owner's standing rules for this HeliBoard-based Urdu-English
keyboard. Read this file before future work. Communicate with the owner in Urdu
script unless they explicitly request another language. Code and technical
documentation may use English. This file preserves project instructions; do not
promise account-wide or cross-session memory without reading it.

## Engineering responsibilities

Apply the perspectives of all these roles, proportionately to each change:
technical lead/software architect; Android application engineer; Kotlin developer;
keyboard/IME engineer; Android Input Method Framework specialist; Urdu language
engineer; Roman Urdu transliteration engineer; NLP/language processing engineer;
dictionary/auto-correction specialist; AI integration engineer; UI/UX designer;
Android UI developer; QA engineer; test automation engineer; Android compatibility
engineer; security engineer; privacy engineer; performance optimization engineer;
accessibility specialist; GitHub/DevOps engineer; CI/CD pipeline engineer; APK
build/release engineer; code reviewer; research/validation specialist; technical
documentation writer. These responsibilities do not authorize spawning agents.

## Product and preservation

Develop incrementally toward standard Urdu and English layouts, easy switching,
Roman Urdu transliteration, Urdu/English suggestions and auto-correction,
punctuation, RTL/LTR handling, clipboard, emoji, numbers/symbols, themes/branding,
privacy-first operation, and optional AI. Do not describe planned features as
implemented. Preserve existing HeliBoard functionality; obtain explicit approval
before removing or damaging working features.

## Required development sequence

1. Inspect the repository structure, documentation, relevant code, current branch,
   recent commits, build system, tests, layouts, dictionaries, permissions, and
   dependencies. Identify working/missing features, existing errors, security and
   compatibility risks. Establish build status when permitted; never assume it.
2. Before major implementation, present a technical baseline and a small phased
   plan: behavior, files, tests, risks, privacy/security/internet impact. Baseline
   builds and tests still require the approvals below.
3. Make one logical, maintainable change at a time. Follow existing architecture,
   avoid duplication and unrelated edits, comment only where useful, keep language
   logic modular, and separate AI from core input handling.
4. Review every changed file after each logical change for logic, imports,
   compilation concerns, layouts, leaks, performance, security/privacy,
   regressions, and documentation. Reviewing code is not a passed test.
5. Read complete errors, identify root causes, consult official documentation or
   source as needed, apply the smallest correct fix, review it, obtain testing
   approval according to the selected mode, retest, and report. Do not hide errors
   or introduce temporary workarounds without explanation and approval.

## Testing approval (MODE A selected)

On 2026-11-18 the owner selected MODE A: ASK ME EVERY TIME. After every logical
change, ask before executing tests, including CI tests. Wait for approval.
The owner later approved one corrected test run followed by a Debug APK if all
tests passed ("yes and after then apk okay"). That one-time authorization has
been fulfilled; future logical changes/tests and future APK builds still follow
the approval rules below. No signed distribution Release has been approved.

- MODE A: ASK ME EVERY TIME. After each logical change ask: "The change is complete.
  Should I run the standard test suite now?" Wait for approval before testing.
- MODE B: ALWAYS RUN TESTS. Select only when the owner authorizes automatic tests
  (for example, "Always run tests automatically"). Run appropriate tests after
  logical changes, fix failures, report results; ask for major/destructive decisions.
- MODE C: DO NOT RUN TESTS UNTIL APPROVED. Execute no tests without approval.

Translate approval questions into Urdu for this owner. Record their selected mode
here when provided. Mode B does NOT authorize APK builds. Do not trigger workflows
that combine tests with an unapproved APK build. New test/build automation must
respect the selected mode, with no automatic APK release/build on ordinary pushes.

When approved, run relevant build/compilation, unit, instrumentation, UI, keyboard
input, Urdu, English, Roman Urdu, RTL/LTR, switching, Space/Backspace/Enter,
number/symbol, suggestions, auto-correction, dictionary, clipboard, emoji,
screen-size, orientation, Android compatibility, low-end device, performance,
memory, battery, accessibility, regression, security, privacy, and APK installation
checks. Scope checks to the actual change; disclose unavailable tooling/devices.
Report executed/passed/failed/unavailable separately. Never claim device coverage
from JVM tests, or installation verification from compilation alone.

## AI, security, and privacy

Offline-first and privacy-first. Before any AI feature, research offline options,
online API needs, text leaving the device, privacy/security, latency, battery,
API keys, and costs. Explain consequential options first. Obtain explicit approval
before online AI, external APIs, cloud text processing, telemetry, or new online
services. If safe offline implementation is unavailable, explain and ask.

For major changes, review unnecessary internet/file access and permissions,
services, telemetry/analytics, typed-text logs, password fields, sensitive input,
clipboard, dictionaries, backups, exported components, intents, storage, network
requests, third-party dependencies/vulnerabilities, and data leaving the device.
Never secretly collect, save, or transmit user input. Never store passwords or
sensitive text. No hardcoded secrets, credentials, API keys, or exposed signing
keys in code/logs/repository. Use secure secret storage such as GitHub Secrets.
Run available static/dependency/security/privacy checks only within approved
testing scope. Explain security findings, fix them, retest with approval, and report
remaining limits; do not claim a comprehensive security audit from a small review.

## APK approval and distribution

Before EVERY new APK build, ask both (in Urdu):
- "Are all required tests complete and approved? Should I build the APK now?"
- "Do you want a Debug APK for testing or a signed Release APK for distribution?"

Wait for explicit approval; neither testing Mode B nor prior APK approval grants
standing APK approval. Build the requested type. Verify actual build output and
test installation when a device/emulator is available. If installation cannot be
tested, disclose it; never call it installation-verified. Upload approved APKs as
Actions artifacts or Releases, provide a real download link, exact file name,
version, commit/workflow provenance, and build type. Do not silently substitute
Debug for Release: explain missing signing and obtain approval for an alternative.
Keep signing private; never request public disclosure of private signing keys.

### APK revision numbering

For each newly approved APK, increase both the owner-visible APK revision and
the Android `versionCode` by one from the previous APK. Show the revision in
`versionName` and the resulting filename (for example, `4.3-beta1-v3` with
version code `4301`, followed by revision `v4` with code `4302`). Keep the
upstream version prefix unless the owner changes it. Assign the next revision
when preparing the approved build.

## GitHub, reporting, and outstanding decisions

Keep clear commits/history and understandable CI logs. Preserve unrelated work;
no destructive Git actions without approval. Keep documentation current and
secrets out of commits/logs. Changes currently live on `codex/roman-urdu` in
`gityy34/MeraAikeybord`; do not merge to main without authorization.

At each completed phase report changes/files/features, tests executed and their
results/unavailability, security checks, remaining issues, and next steps. At final
APK delivery also give version, build type, commit, test/security reports, known
limits, installation instructions, and download link. No APK link is required for
a documentation-only phase with no approved new build.

Before major work resolve unanswered decisions: testing mode; initial AI features
and offline requirements; Debug versus signed Release; minimum supported Android.
Do not ask again for already answered choices. Earlier user preferences establish
Android, working Urdu/English, offline Roman Urdu and fast/local core typing, with
personal testing before public distribution. They have not yet selected a minimum
Android version or an initial AI feature. Current source minSdk is 21, which is an
existing configuration, not a newly confirmed user requirement.

The previous Roman Urdu prototype passed 11 targeted CI tests and built a Debug
APK at commit bfb11c224bef5c6add305cc6626e5621b3b9706a in workflow 44246942835.
It has a small seed lexicon plus phonetic fallback; it is not comprehensive Roman
Urdu/NLP. Physical-device installation/performance and a full security audit were
not verified in that phase. Do not repeat tests/builds without the new approvals.

## Active Roman Urdu improvement task

Work on `codex/roman-urdu-improvements`, based on the existing Roman Urdu branch.
See `docs/ROMAN_URDU_IMPROVEMENT.md` for research, source licensing, the baseline,
planned shared-component changes and rollback, and phase-by-phase acceptance.
The owner approved phase-1 tests: 26 targeted CI tests passed at d329fb8e in
workflow 44254057873; Kotlin/Java app and test compilation passed. APK steps were
skipped. The owner approved phase-2 tests at cdc87d6e: app/test compilation passed,
41 of 42 tests passed, and the password literal-input test failed in workflow
44257607285. At cc8b6b6b the approved retest passed 44/44 tests and compilation in
workflow 44260667257. After that success, the owner-approved Debug APK was built
separately in workflow 44261292695 from the SAME commit, verified, and uploaded.
See docs/ROMAN_URDU_DEBUG_BUILD_2026-11-18.md for links, exact hashes, signature
checks, installation limits and the previous-Debug signer mismatch. No actual
phone/emulator installation was tested. Protected correction learning/removal,
device performance/accessibility and broader regression/security review remain
pending. See the separate Urdu layout phase below for later changes.

## Urdu layout Debug APK revision 3

The owner approved targeted retesting and chose a Debug APK for personal use
after the tests passed. GitHub Actions run 44588891882 checked product commit
`9fa6511453b24dac84690e2bcc39f77c79e04cc4` and passed 48 tests with zero failures,
errors or skips. The native Urdu layout has four character rows. Its full-stop
popup uses readable labels and single Unicode code points for diacritics.
The same-locale Roman Urdu layouts retain their earlier period popups.

Build run 44589683135 used commit `4e6b81bed26a1344f3bc7436419e980bf2b5cda7`,
which adds only APK version metadata to the tested source. It produced
`HeliBoard_4.3-beta1-v3-debug.apk` with version code `4301`. The artifact is
<https://github.com/gityy34/MeraAikeybord/actions/runs/44589683135/artifacts/14563507106>.
CI verified the APK signature, version, bundled Urdu assets and permissions.
The Debug certificate differs from the previous APK. Physical installation,
actual screen geometry, cursor movement and Backspace with diacritics across
editors remain unverified. Approval applied only to the completed run and build.
The isolated CI branches address the unavailable workflow-dispatch connection.

## In-keyboard toolbar editor

The keyboard's overflow menu opens editors for regular and pinned tools, lists
the tools outside the visible pins, and exposes the existing floating and docking
actions. Editing uses the existing local preferences. Dragging uses an in-process
tool identifier and sends no text. The new menu is unavailable in password fields
and while the device is locked.

The owner approved targeted tests on 2026-11-20. Run 44662569884 compiled the app
and test sources at `fcc98c60437c2447415c85fc5e1a6e39a6674f15`. All 54 tests
passed, including six toolbar state tests and 48 keyboard regression tests.
See <https://github.com/gityy34/MeraAikeybord/actions/runs/44662569884/artifacts/14595109961>.
There were no manifest or dependency changes. Phone checks for menu placement,
long-press dragging, docking and TalkBack remain pending.

The owner approved the Debug APK for personal testing. Build run 44664130048 used
`9712f429dbeb30c75492c77554dbf72ff616a90a`. The only app change from the tested
source is the version bump. The APK is `HeliBoard_4.3-beta1-v4-debug.apk` with
version code `4302` and size 27,321,879 bytes. Download it from
<https://github.com/gityy34/MeraAikeybord/actions/runs/44664130048/artifacts/14597202016>.

CI verified the v1 and v2 signatures, package and version metadata, packaged
toolbar resources, and Urdu layout asset. The APK has no internet permission.
Its SHA-256 is
`269b8209bd00292968c2ef155b5629522a078c697dfb0040302edd358622a6b9`.
The Debug certificate differs from v3. Back up the old test app's settings
before removing it to install v4. Emulator installation was unavailable because
the runner lacked KVM access. The next newly approved APK is v5 with code 4303.

## Keyboard tools panel

The four-square toolbar button opens a two-column tools panel over the keyboard
area. The pencil opens an editor for pinned shortcuts. Users can drag between
the available tools and shortcuts, reorder with accessibility actions, and save
with the checkmark. The panel uses the existing keyboard theme and actions, with
shortcuts to Appearance settings and the next language. It closes when input
ends and is unavailable in password fields or while the device is locked.

On 2026-11-20 the owner approved targeted tests. Run 44669694421 compiled the
Kotlin and Java sources at `5245d800540cd25baf9621280bf2da9290ba0ed7` and passed
all 58 tests, including 10 toolbar tests and 48 keyboard regression tests.
The [test reports](https://github.com/gityy34/MeraAikeybord/actions/runs/44669694421/artifacts/14599178942)
record zero failures, errors, and skips. Static review found no manifest,
dependency, network, or typed-text logging changes. Dragging carries only an
in-process tool identifier. Phone checks for swiping, drag-and-drop, layout size,
and TalkBack remain pending. The run produced test reports only.

## Tools panel Debug APK revision 5

The owner approved a Debug APK for phone testing. Build run 44672461276 used
`eb2845d5d2e2d33869e468321b9defdc11cfa61d`, which adds only version metadata
to the source that passed 58 tests. The APK is `HeliBoard_4.3-beta1-v5-debug.apk`
with version code `4303` and size 27,337,201 bytes.
[Download the Debug APK](https://github.com/gityy34/MeraAikeybord/actions/runs/44672461276/artifacts/14601195234).

CI verified archive integrity, alignment, v1 and v2 signatures, the packaged
toolbar and Urdu layout assets, and the absence of internet permission.
The APK SHA-256 is
`53f329ddfcaa6664a76e3b34e3ebab8d105a1a126a3348a4f9660a3f74fb5435`.
Run 44673703549 matched that hash, installed the APK on an Android 11
(API 30) emulator, and enabled and selected the keyboard as its input method.
[Installation verification](https://github.com/gityy34/MeraAikeybord/actions/runs/44673703549).

The Debug certificate differs from revision 4. Save a backup from Advanced
settings before removing revision 4 to install revision 5. Physical phone
checks for toolbar swipes, dragging, and TalkBack remain pending. The next
newly approved APK revision is v6 with code 4304.
