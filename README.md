# Orbit IME Android

Orbit IME is a privacy-first Android input method with local English candidates, Pinyin 26-key input, local learning, offline phrase translation, clipboard tools, skins, and a keyboard pet module.

## Current version

```text
0.15.0
```

v0.15 replaces the old small hardcoded-map approach with a real local candidate engine and a reproducible mature-data build pipeline.

## Local IME engine

Chinese candidates now flow through:

```text
raw Pinyin
-> normalization
-> exact user / asset / sentence candidates
-> dynamic-programming Pinyin segmentation
-> phrase beam search
-> static frequency
-> local 1/2/3-gram score
-> local user-frequency boost
-> fuzzy/typo penalty
-> top 12 candidates
```

Key implementation files:

```text
CompactLexiconAsset.kt
PinyinSegmenter.kt
NGramLanguageModel.kt
CandidateRanker.kt
PinyinImeEngine.kt
UserDictionaryStore.kt
```

English candidates use:

```text
EnglishImeEngine.kt
CompactEnglishAsset.kt
EnglishDictionary.kt fallback
```

Detailed architecture is in `IME_ENGINE.md`.

## Mature dictionary build

A normal Gradle build automatically executes:

```text
tools/test_ime_data_pipeline.py
        ↓
tools/prepare_mature_ime_data.py
        ↓
tools/ime_importer.py
        ↓
app/src/main/assets/ime/
```

The build machine downloads only pinned, audited public sources and verifies Git blob hashes before using them.

Current mature sources:

```text
Chinese: AOSP PinyinIME raw dictionary (~65k source entries, Apache-2.0)
English: ESDB/SCOWL en_US generated spelling list (ESDB permission notice)
```

Pinned source metadata and hashes are in:

```text
data/ime_sources/mature_sources.json
```

Third-party notices are copied into the packaged offline asset directory.

The installed keyboard never downloads these sources; Orbit IME still declares no `INTERNET` permission.

## Large dictionary importer

Build-time tool:

```text
tools/ime_importer.py
```

It supports:

- Orbit TSV: Pinyin + text + frequency;
- CC-CEDICT text format;
- English TSV frequency/candidate data;
- 1/2/3-gram TSV counts.

It fails closed for missing sources, non-redistributable sources, missing third-party attribution, and unknown strict-mode licenses.

## Frequency and sentence ranking

`CandidateRanker` combines:

```text
static frequency
+ N-gram evidence
+ Pinyin segmentation quality
+ local user frequency
+ source priority
- fuzzy/typo penalty
```

`PinyinImeEngine` performs bounded phrase-level beam search instead of requiring every full sentence to be a hardcoded exact key.

Current limits:

```text
max segmentation paths: 5
max phrase span: 4 syllables
beam width: 36
max internal beam results: 16
visible candidates: 12
```

## Continuous Pinyin segmentation

`PinyinSegmenter.kt` uses dynamic programming and keeps several high-quality paths.

Examples:

```text
nihaoma -> ni / hao / ma
nishishei -> ni / shi / shei
shurufa -> shu / ru / fa
```

A syllable-count penalty prevents pathological over-segmentation such as preferring `ha + o` over `hao`.

## Local N-gram model

`NGramLanguageModel.kt` reads:

```text
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

It is a deterministic count model, not a neural/network model. Ranking can use both phrase-token and Chinese-character evidence.

The mature preparation step derives bounded character N-grams from accepted AOSP phrases while retaining project-authored word/phrase N-grams.

## Compact assets and memory bounds

Chinese mature assets are sharded:

```text
ime/lexicon/a.odict
...
ime/lexicon/z.odict
```

Large English assets are also sharded automatically:

```text
ime/english/a.odict
...
ime/english/z.odict
```

Small development English packs can still use:

```text
ime/english.odict
```

`CompactLexiconAsset` keeps at most six Chinese shards in memory. `CompactEnglishAsset` keeps at most four English shards. This prevents the mature vocabulary from being loaded as one giant map.

Integer frequencies/counts use base36 inside `ORBIT_ODICT` files.

## Local personalization

`UserDictionaryStore` stores only:

```text
pinyin
committed candidate text
frequency
updatedAt
```

It does not store full chat text, app/package identity, target fields, or surrounding sentences. An in-memory parsed-entry cache avoids repeatedly decoding SharedPreferences JSON while ranking candidates.

Repeated explicit candidate choices can move a personal candidate upward. Clearing local learning restores packaged ordering.

## Fuzzy and typo correction

`PinyinCorrectionEngine` is deliberately lower confidence than exact spelling. Fuzzy candidates receive a ranking penalty.

Existing compatibility examples include:

```text
xhfnivh -> includes 喜欢你
xihvanni -> 喜欢你
nishis -> 你是谁
```

## Existing features retained

- English composing buffer and candidate selection.
- Expanded Chinese and English quick phrases.
- Local clipboard panel.
- Local phrase translation with prompt fallback only when no packaged translation matches.
- Keyboard pet panel with check-in, hatching, switching, outfits, catalog, and local growth.
- Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora placeholder skin.
- Privacy mode for password-like fields.
- Android input-method picker entry.

## Privacy boundary

v0.15 keeps:

- no `INTERNET` permission;
- no analytics SDK;
- no ad SDK;
- no Accessibility permission;
- no overlay/floating-window permission;
- no cloud prediction;
- no cloud dictionary sync;
- no cloud translation;
- no background clipboard harvesting;
- no full typed-stream persistence.

The build machine's public dictionary download is not an app runtime capability.

## Data-source and license rule

Do not paste arbitrary GitHub dictionaries into Orbit.

Every bulk source must have explicit provenance, redistribution terms, attribution, and a pinned source/hash. See `DATA_SOURCES.md`.

AOSP LatinIME's English dictionary was specifically rejected because its NOTICE says the dictionaries include Lexiteria material “Used by permission”; Orbit uses ESDB/SCOWL instead.

## Data-pipeline test

Run without Android SDK:

```text
python tools/test_ime_data_pipeline.py
```

It checks parsers, Git blob hashes, license fail-closed behavior, Chinese/N-gram packing, and automatic English sharding.

## Build policy

Do not build automatically on push. GitHub Actions remains manual-only through `workflow_dispatch`.

A normal build is now enough; preBuild performs the data tests and mature-data preparation automatically:

```text
JDK 17
Python 3.12 recommended
Android SDK 35
Gradle 8.10.2

gradle assembleDebug --no-daemon
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Manual Actions artifact:

```text
orbit-ime-v0.15-debug-apk
```

For intentionally offline development only, mature preparation can be skipped with:

```text
-PorbitSkipMatureImeData=true
```

Do not use that switch for the APK intended for user testing.

## v0.15 device acceptance

At minimum verify:

```text
nihaoma -> 你好吗 near top
nishishei -> 你是谁 near top
shurufa -> 输入法 near top
haishiyouwenti -> 还是有问题 near top
xhfnivh -> useful Chinese correction candidate
```

Also verify English composing/candidates, local learning, translation result insertion, Clips, pet panel, skins, privacy mode, and the input-method picker.

## Commercial direction

The intended direction remains free base + optional paid Pro. Do not place ads in the keyboard input surface. v0.15 contains no billing, advertising, analytics, runtime network, cloud translation, or external translation API implementation.
