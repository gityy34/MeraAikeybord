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
No new APK build is authorized by the Roman Urdu improvement task.

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
44257607285. The password/no-suggestion composition root cause has been fixed in
source and tests expanded; the fix has NOT been compiled/retested and needs fresh
MODE A approval. See docs/ROMAN_URDU_PHASE2_TEST_REPORT.md. No APK is authorized.
Protected correction learning/removal remains pending; do not start that next
phase while the phase-2 fix is still awaiting validation.
