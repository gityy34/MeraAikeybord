# Roman Urdu improvement: assessment, research, and phased implementation

Date: 2026-11-18. Testing mode: **A (ask after each logical change)**.
APK builds: **not authorized**. No external dataset has been imported.

## Baseline and root causes

The inspected baseline is c9b511f7 on `codex/roman-urdu`. The preceding prototype
at bfb11c22 passed 11 targeted CI tests and produced a Debug APK in workflow
44246942835. Those historical results do not validate this change. No fresh
baseline build or test has been run without the newly required approval.

The app uses Gradle, Android Gradle Plugin, Kotlin/Java and native dictionaries;
its current minSdk is 21. Android version and APK identity are unchanged here.

Relevant existing components:

- `event/RomanUrduTransliterator.java`: 261 seed spellings in a hardcoded map,
  followed by character/digraph fallback; no semantic metadata or ranked choices.
- `event/RomanUrduCombiner.kt`: retains Roman keystrokes internally, displays Urdu
  immediately, and handles boundaries/backspace through the existing combiner path.
  It forces unknown English spellings through phonetic conversion in this mode.
- `event/CombinerChain.kt`, `latin/WordComposer.java`, `res/xml/method.xml`, and
  `assets/layouts/main/roman_urdu.txt`: subtype, composing state and QWERTY routing.
- `latin/inputlogic/InputLogic.java`: obtains suggestions, chooses automatic
  corrections and commits user selections; also contains optional gathering hooks.
- `latin/suggestions/Suggest.kt`, `SuggestedWords.kt`, `SuggestedWordInfo.kt`:
  native dictionary results, auto-correction decisions and typed-word conventions.
- `SuggestionStripLayoutHelper.java`: existing typing layout hides index 0,
  places index 1 centrally, index 2 left, and index 3 right. A conditional adapter
  can reuse this without redesigning the shared strip.
- `DictionaryFacilitatorImpl.kt`, user/history dictionaries and
  `settings/SettingsValues.java`: personalized dictionaries, opt-ins, no-learning,
  password and incognito protections. Existing history is not a Roman→Urdu pair
  store; it cannot simply be claimed to learn transliteration corrections.

There is no bundled `main_ur.dict` in the inspected assets. The original map has
only some requested variants; `ja`, `lagta`, and `kaisay` need explicit coverage.
No current Roman-specific normalization, confidence, English equivalents, semantic
alternates or contextual ranking exist. Generic auto-correction is a separate
path, not evidence that Roman Urdu correction already works.

## Researched sources and import decisions

These are source-reported formats/licenses, not independent quality benchmarks.
Research access does not mean data or code was bundled.

| Source | License / commercial use / notices | Format and quality limits | Integration decision |
| --- | --- | --- | --- |
| [Google Dakshina](https://github.com/google-research-datasets/dakshina) | Dataset CC BY-SA 4.0; commercial use allowed subject to attribution, license/link, change notices and ShareAlike obligations for adaptations. Keep data terms separate from the GPL app. | Urdu/native word, human Roman spelling, attestation count in TSV; parallel sentences; Wikipedia domain, not a validated Pakistani chat corpus. Attestation counts are not conversational word frequencies. | Candidate for later offline evaluation/import after pinning version/hash and notices. Never train on held-out dev/test data while claiming held-out evaluation. Not imported in phase 1. |
| [UrduHack words](https://github.com/urduhack/urdu-words), [license](https://github.com/urduhack/urdu-words/blob/master/LICENSE) | Repository MIT license allows commercial use with retained copyright/permission notices. README credits CLE for the word list; trace the underlying data grant before redistribution, rather than assuming a code license settles data provenance. | Newline Urdu words, bigram/trigram lists; no Roman alignment, reliable English glosses or calibrated frequencies. No independent quality evaluation performed. | Possible future Urdu membership filter after provenance review. Not imported. |
| [ISI uroman](https://github.com/isi-nlp/uroman), [license](https://github.com/isi-nlp/uroman/blob/master/LICENSE.txt) | Permission-granting MIT-style text permits commercial use; retain notices and the additional project-publication acknowledgement. Do not describe it as unqualified standard MIT. | Python/Perl romanizer with rule/data tables and text/JSONL outputs. Its direction is native script→Latin, not a reverse Urdu keyboard predictor. | Architectural reference only; no runtime dependency or copied rules. |
| [IRSAAL-Urdu](https://github.com/irsaal/urdu), [license](https://github.com/irsaal/urdu/blob/main/LICENSE) | GPL-3.0; commercial distribution is possible with corresponding source, notices and license obligations. | Research notebook / Sheets-oriented corpus regularization and conversion; no Android latency or broad chat accuracy benchmark established here. | Research reference only; no notebook, code or mappings imported. |

Phase 1 instead migrates the project's own earlier seed vocabulary and adds
owner-provided variants plus original curated glosses/context hints. Data files
carry GPL-3.0-only identifiers. No third-party attribution is newly required by an
import because there is no import. Expansion from a corpus must record exact
source/version/hash, original license, provenance, transformation and notices.

## Proposed solution and approval boundaries

### Phase 1 — isolated candidate engine (implemented, tests NOT RUN)

New package `latin/romanurdu` separates normalization, lexicon loading/indexes,
candidate scoring and sentence conversion. New first-party assets contain forms,
Urdu, optional English meaning, curated related words, editorial frequency prior,
editorial confidence, category and context hints. These scores are heuristic,
not corpus statistics or calibrated probabilities.

Lookup combines exact/normalized forms, a lossy phonetic retrieval key, bounded
one-edit matching, dictionary entry confidence, frequency priors and nearby Roman
or Urdu hints. Colliding spellings retain multiple candidates. English guard
words, unknown title-cased names and weak matches are kept conservative.

The result supplies English → best Urdu → related Urdu choices, omitting absent
or unreliable fields. The engine never invents a third item to fill the strip.
Manual sentence conversion is the default and returns original text unchanged.
The explicit automatic helper requires confidence and a candidate margin; it
preserves separators and avoids identifiers, URLs, email and number-bearing tokens.

The active keyboard still uses the old prototype. No existing source file for
IME input, suggestions, English, layouts, clipboard, emoji or themes was modified.
This stage is not the complete feature and must not be described as working in
the app until integration and relevant tests actually pass.

### Phase 2 — manual/default and optional automatic IME integration (pending)

Before changing shared components explain their exact diff. Expected narrow
changes: Roman-only checks in `InputLogic.getSuggestedWords` and suggestion picks;
Roman combiner/subtype settings for manual versus completed-word automatic mode;
a Roman-specific adapter returning the conventional typed item plus Urdu at
index 1, English at index 2, related Urdu at index 3. Reuse the existing strip.
Keep automatic replacement disabled in manual mode even if general autocorrect
is on. Respect input-field suggestion restrictions and no-learning flags.

Risks: asynchronous stale suggestions, composing ranges, cursor movement,
selection, language switch, word boundaries and strip reorder conventions.
Test the full editor path and English/Urdu regression cases. Reversal: revert
the Roman-gated adapter and subtype/combiner changes on this task branch; no
dictionary migration of general HeliBoard data is planned.

### Phase 3 — private correction learning and removal (pending)

An approved suggestion selection or unambiguously observed correction can update
a bounded Roman→Urdu mapping only when personalized learning is allowed. Do not
monitor or persist surrounding messages. Exclude passwords, sensitive field
types, incognito, no-personalized-learning flags and any field whose sensitivity
is uncertain. Do not use contacts as Roman correction training data.

Plan a credential-protected, no-backup, app-private store for individual word
pairs, with opt-out, delete-one and clear-all controls accessible from Roman
settings. Never claim Android's existing user dictionary is private/no-backup
without checking it. Manual correction detection needs editor tests proving it
cannot learn unrelated edits. Add no storage or history collection in phase 1.

## Tests, security review, and performance plan

New `RomanUrduEngineTest.kt` covers requested spelling groups, all five sentences,
manual-mode preservation, three-choice ordering, missing glosses, contextual
ambiguity, English/mixed/technical text, names/brands, unknowns, conservative fuzzy
matches, punctuation/numbers/identifiers, repeated letters, locale independence,
long input, stateless queries and malformed dictionary records. Tests are written
but **not compiled or executed**. No passes, timings or memory measurements claimed.

On approval run the new JVM tests and existing Roman transliterator/combiner
regression tests, plus project Kotlin compilation and the existing targeted editor
tests when tooling permits. Do not run `assemble*`/`package*` APK tasks. The current
manual workflow's test name filter already includes `RomanUrduEngineTest`; do not
dispatch its APK option. Later integration phases add actual strip selection,
backspace/Enter, mixed RTL/LTR, switching, manual correction, incognito, password
and learned-word deletion tests. Device UI, installation, low-end latency,
orientation, memory and battery require an emulator/device; they have not run.

Source review of the new package finds no networking, persistence, logging,
external dependencies, secrets or permission changes. Parsing bounds line length,
entry count and field shapes, rejects directional control characters, and uses
typed records rather than executable imported content. Context is transient,
limited to 256 characters/8 words per side inside sentence boundaries. Hash
indexes avoid whole-dictionary scans; fuzzy fallback examines at most 256 keys.
Load/index once away from the input thread in the later Android adapter.

This is a code review, not executed security validation or a dependency audit.
Existing manifest enables backups and device-protected storage; newly learned
corrections must not inherit those defaults blindly. Existing gesture gathering
is disabled by default in source but has opt-in hooks. Phase 1 neither enables
nor changes it; integration must ensure Roman/private text cannot enter optional
gathering paths. Preserve unrelated features while isolating the new path.

## Remaining acceptance work

Approval and actual execution of phase-1 tests; IME/manual/automatic settings and
strip integration; protected personal learning/removal; broader vetted data and
held-out evaluation; device performance/security/privacy/regression checks. This
task is not accepted as complete yet. A new APK still needs separate approval.
