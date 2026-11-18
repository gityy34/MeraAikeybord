# Roman Urdu seed data

SPDX-License-Identifier: GPL-3.0-only

Project-authored data, migrated from the earlier prototype and extended with the
owner's requested examples. No external corpus or researched project was copied.

`lexicon.tsv` has nine tab-separated fields, with pipe-separated lists:

1. Urdu output
2. English meaning, or empty when ambiguous/unreviewed
3. Curated related Urdu alternatives (not fuzzy spelling candidates)
4. Roman spellings
5. Editorial frequency prior, 0–100 (NOT measured corpus counts)
6. Editorial confidence, 0–1 (NOT a calibrated probability)
7. Previous context hints, Roman or Urdu
8. Following context hints, Roman or Urdu
9. Word category

Blank fields are significant. One spelling may occur in multiple sense entries.
Do not resolve such collisions by overwriting earlier rows. New external data
requires a source, pinned version/hash, license and attribution record, provenance
review, validation and held-out quality evaluation before integration.

`english_protected.txt` is a small original guard list. It is not a comprehensive
English dictionary or language detector. The eventual Android adapter must use
the existing English dictionaries and preserve the dedicated English input path.

This data and the new engine are not wired into the keyboard yet. The old
RomanUrduCombiner/Transliterator remain the active implementation until the
engine tests have been approved and the separate integration phase is completed.
