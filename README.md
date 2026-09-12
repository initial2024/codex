# Orbit IME Android

Orbit IME is a privacy-first Android input method with local Pinyin/English candidates, local personalization, offline phrase translation, clipboard tools, skins, and a keyboard pet module.

## Current version

```text
0.15.0
```

v0.15 replaces the earlier small hardcoded-map approach with a reproducible local IME engine and mature offline dictionary build pipeline.

## Chinese candidate engine

```text
raw Pinyin
-> exact local/user candidates
-> dynamic-programming Pinyin segmentation
-> compact lexicon lookup
-> bounded phrase beam search
-> static frequency
-> local 1/2/3-gram score
-> local user-frequency boost
-> fuzzy/typo penalty
-> top 12 candidates
```

Core files:

```text
PinyinSegmenter.kt
CompactLexiconAsset.kt
PinyinImeEngine.kt
NGramLanguageModel.kt
CandidateRanker.kt
UserDictionaryStore.kt
```

Examples targeted by the engine include:

```text
nihaoma -> 你好吗
nishishei -> 你是谁
shurufa -> 输入法
haishiyouwenti -> 还是有问题
```

Fuzzy compatibility remains lower-confidence than exact Pinyin.

## English candidate engine

```text
English composing buffer
-> CompactEnglishAsset
-> EnglishImeEngine
-> EnglishDictionary fallback/typo layer
```

Large English data is automatically sharded by first letter and loaded through a bounded LRU rather than one giant runtime map.

## Mature offline dictionary build

A normal Gradle build automatically runs:

```text
tools/test_ime_data_pipeline.py
        ↓
tools/prepare_mature_ime_data.py
        ↓
tools/ime_importer.py
        ↓
generated local dictionary assets
        ↓
tools/validate_mature_ime_assets.py
        ↓
Android build
```

The final validator rejects suspiciously small/incomplete packs, missing notices, missing source pins/licenses, missing 1/2/3-grams, wrong version metadata, and forbidden manifest capabilities.

Current pinned mature sources:

```text
Chinese: AOSP PinyinIME raw dictionary (~65k source entries), Apache-2.0
English: ESDB/SCOWL en_US generated spelling list, ESDB redistribution notice
```

Source URLs, Git blob hashes, licenses, attribution, and minimum-data thresholds are recorded in:

```text
data/ime_sources/mature_sources.json
DATA_SOURCES.md
```

The build process packages the required AOSP and ESDB notices with the generated offline assets.

The installed Android input method does **not** download these datasets and still declares no `INTERNET` permission.

## Large-data format

Runtime data uses compact `ORBIT_ODICT` files with base36 frequencies/counts.

Chinese:

```text
ime/lexicon/a.odict ... z.odict
```

English mature pack:

```text
ime/english/a.odict ... z.odict
```

N-grams:

```text
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

`CompactLexiconAsset` caches at most six Chinese shards. `CompactEnglishAsset` caches at most four English shards.

## Local personalization

The learned user dictionary persists only:

```text
pinyin
committed candidate text
frequency
updatedAt
```

It does not persist full chat/input streams, app/package identity, target fields, or surrounding sentences. Temporary ranking context is not stored.

Repeated explicit candidate selections can move a personal candidate upward. Clearing the local dictionary restores packaged ranking.

Raw-fallback commits such as Enter/punctuation do not use a fuzzy/prefix guess when no exact candidate exists.

## Other retained functions

- English composing buffer and candidate selection.
- Chinese/English quick phrases.
- Local Clips clipboard panel; no background harvesting.
- Local phrase translation; prompt fallback only when no local translation matches.
- Keyboard pet panel with check-in, hatching, switching, outfits, catalog, and local growth.
- Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora placeholder skin.
- Privacy mode for password-like/no-personalized-learning fields.
- Android input-method picker entry.

## Privacy boundary

Orbit IME v0.15 intentionally keeps:

- no `INTERNET` permission;
- no ads/analytics/tracking;
- no Accessibility permission;
- no overlay/floating-window permission;
- no cloud prediction or dictionary sync;
- no cloud/external translation API;
- no background clipboard/input harvesting;
- no full typed-stream persistence.

Build-machine access to pinned public dictionaries is not an app runtime capability.

## Data-source policy

Do not paste arbitrary GitHub dictionaries into Orbit. Every bulk source must have explicit provenance, redistribution terms, attribution, and a pinned source/hash.

AOSP LatinIME's English dictionary was deliberately rejected because its NOTICE includes third-party dictionary material marked “Used by permission”; Orbit uses ESDB/SCOWL instead. See `DATA_SOURCES.md`.

## Build

GitHub Actions remains manual-only through `workflow_dispatch`.

Required environment:

```text
JDK 17
Python 3.12 recommended
Android SDK 35
Android build-tools 35.0.0
Gradle 8.10.2
```

Build command:

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for the APK intended for user testing.

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Actions artifact:

```text
orbit-ime-v0.15-debug-apk
```

## Device acceptance after build

At minimum retest continuous Pinyin, fuzzy correction, English composing/candidates, personal ranking/clear, direct local translation result insertion, Clips, pet controls, skins, privacy mode, and input-method switching.

## Commercial direction

The intended direction remains free base + optional paid Pro. Do not place ads inside the keyboard input surface. v0.15 implements no billing, ads, analytics, runtime network, cloud translation, or external translation API.
