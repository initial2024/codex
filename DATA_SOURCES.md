# Orbit IME data source strategy

Orbit IME uses a reproducible, build-time data pipeline so the installed keyboard remains fully offline while still shipping a substantially larger local vocabulary.

## v0.15 mature-data path

The default build now runs:

```text
tools/test_ime_data_pipeline.py
        ↓
tools/prepare_mature_ime_data.py
        ↓
tools/ime_importer.py
        ↓
app/src/main/assets/ime/
```

`gradle assembleDebug` therefore performs the data validation/preparation automatically before Android packages the APK. The Android app itself still has no `INTERNET` permission.

The pinned external-source metadata is stored in:

```text
data/ime_sources/mature_sources.json
```

Every downloaded source is verified against its Git blob SHA-1. A changed upstream file fails the build instead of silently changing the packaged dictionary.

## Audited Chinese source: AOSP PinyinIME

Pinned source:

```text
Repository: LineageOS/android_packages_inputmethods_PinyinIME
Commit: a08d08cdea4319fb74b55289e1b4e1b1a5249bc2
File: jni/data/rawdict_utf16_65105_freq.txt
Git blob SHA-1: 28805ba68eb8df265c1d227fb99e841ff3302aef
License: Apache-2.0
```

The source dictionary is roughly a 65k-entry Pinyin/frequency dictionary. Example records contain a Chinese word/phrase, a numeric frequency, a flag, and one or more Pinyin syllables.

Orbit preparation rules:

- accept only normal `flag == 0` entries;
- keep CJK-only words/phrases up to 12 characters;
- concatenate tone-less syllables into the runtime Pinyin key;
- scale the supplied frequency deterministically;
- merge with project-authored seed entries by maximum frequency;
- derive bounded character 1/2/3-gram counts from accepted phrases;
- preserve the pinned AOSP NOTICE in packaged third-party notices.

The AOSP repository contains `MODULE_LICENSE_APACHE2`, and the pinned NOTICE identifies the Android Open Source Project work under Apache License 2.0.

## Audited English source: ESDB / SCOWL

Pinned source:

```text
Repository: en-wl/wordlist-diff
Tag: rel-2026.02.25
File: en_US.txt
Git blob SHA-1: b4222bda8be5826fce1635230f9503234ec31e5a
License identifier in Orbit: ESDB-2026
Copyright notice blob: 562ec7df17753481162f2b993e2dbd47cea77b2f
```

The ESDB copyright notice explicitly grants permission to use, copy, modify, distribute, and sell ESDB or word lists created from it, provided the required copyright/permission notices are retained in supporting documentation.

Orbit uses the generated US English spelling list as a vocabulary/completion source, not as a frequency corpus. Consequently:

- project-authored high-frequency words retain stronger scores;
- ESDB entries receive a deliberately low synthetic floor frequency;
- proper-name/capitalized-only entries are not imported into the default completion pack;
- ordinary lower-case ASCII words, contractions, and hyphenated words are normalized for lookup;
- display forms such as `don't` remain available as candidate text;
- the full pinned ESDB copyright file is packaged with the generated assets.

## Rejected mature English source: LatinIME dictionary

The LineageOS/AOSP LatinIME tree was inspected, including `en_US_wordlist.combined.gz`. It is not used by Orbit.

Reason: its NOTICE includes:

```text
Includes Dictionaries © Lexiteria LLC. Used by permission.
```

That is not a sufficiently clean redistribution basis for Orbit's planned independent/commercial distribution. The code's Apache license is not treated as proof that every bundled dictionary has the same redistribution rights.

## Project-authored seed data

The following remain in the repository as deterministic fallbacks and high-priority modern/product vocabulary:

```text
data/ime_sources/seed_lexicon.tsv
data/ime_sources/seed_english.tsv
data/ime_sources/seed_ngram.tsv
```

They are merged with the mature pack. If mature assets are missing/malformed during development, runtime legacy/fallback paths still prevent a blank candidate bar.

## N-gram policy

AOSP accepted phrases generate character-level N-grams, bounded to keep IME memory predictable:

```text
1-gram: up to 8,000 rows
2-gram: up to 25,000 rows
3-gram: up to 25,000 rows
```

Project-authored word/phrase N-grams are merged with those generated rows. Runtime ranking can therefore use both phrase-token and Chinese-character evidence.

These are deterministic count features, not a neural language model and not cloud prediction.

## Compact runtime assets

Chinese lexicon:

```text
ime/lexicon/a.odict
...
ime/lexicon/z.odict
```

Large English pack:

```text
ime/english/a.odict
...
ime/english/z.odict
```

Small development English packs may remain as:

```text
ime/english.odict
```

`CompactLexiconAsset` and `CompactEnglishAsset` use bounded LRU shard caches instead of loading all mature vocabulary into IME memory at once.

N-grams:

```text
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

Frequency/count integers are stored in base36.

The preparation step also writes:

```text
ime/mature-report.json
ime/third_party_notices/AOSP-PinyinIME-NOTICE.txt
ime/third_party_notices/ESDB-SCOWL-Copyright.txt
```

## License gate

Every importer source declares:

```text
name
path
format
license
source URL
redistribution_allowed
attribution
```

Strict accepted identifiers currently include:

```text
PROJECT
Apache-2.0
MIT
BSD-2-Clause
BSD-3-Clause
CC-BY-4.0
CC-BY-SA-4.0
ESDB-2026
```

The importer fails closed when redistribution is false, the source is missing, a third-party URL/attribution is missing, or the license is unknown in strict mode.

The allow-list is an engineering guardrail, not a general legal conclusion for arbitrary datasets.

## Offline test coverage

Run:

```bash
python tools/test_ime_data_pipeline.py
```

The test checks:

- Git blob SHA calculation;
- AOSP raw dictionary parsing/filtering;
- AOSP-derived character N-grams;
- ESDB word normalization;
- redistribution/license fail-closed behavior;
- Chinese lexicon packing;
- 1/2/3-gram packing;
- automatic English sharding above 10k entries.

## Product boundary

The build machine downloads public pinned dictionary sources. The installed Orbit IME does not.

Orbit IME still does not request `INTERNET`, Accessibility, overlay/floating-window, advertising, analytics, or background input-harvesting capabilities. User personalization remains local and stores only the explicit candidate-selection statistics documented in `USER_DICTIONARY.md`.
