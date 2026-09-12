# Orbit IME Android

Orbit IME is a privacy-first Android input method with local Chinese/English prediction, local personalization, clipboard history, offline translation, visual keyboard pets, Emoji/kaomoji, local pet stickers and skins.

## Current version

```text
0.18.0
```

v0.18 focuses on the remaining “toy IME” gaps reported from real device testing:

- fixes candidate commit so Chinese/English candidates **replace** active composing text instead of being appended after raw letters;
- greatly expands licensed Chinese, English, translation and Emoji data;
- makes the mature packaged Chinese lexicon participate in fuzzy/typo and prefix association;
- adds a 7-page symbol keyboard;
- adds long-press number/symbol mappings on the 26-key layout;
- keeps v0.17 visual pets, kaomoji, local PNG stickers, clipboard and translation UI.

## Chinese input engine

```text
continuous Pinyin
-> exact packaged/user candidates
-> dynamic-programming segmentation
-> compact sharded lexicon
-> adaptive phrase Beam Search
-> static frequency + 1/2/3-gram
-> local user-frequency boost
-> packaged-prefix association
-> fuzzy initials/finals + keyboard-neighbor typo recovery
-> top candidates
```

The composing buffer supports up to 192 normalized letters. Short input can use a wider search; longer input progressively narrows segmentation/Beam limits to control latency.

### v0.18 composing fix

Candidate commit no longer calls `finishComposingText()` before committing a Chinese/English candidate. The candidate is committed directly over Android's active composing region. Clearing or entering translation mode also replaces the composing region with empty text rather than committing raw Pinyin/English first.

This specifically prevents failures such as:

```text
sj数据库不够hy还有问题
```

where raw Pinyin fragments remained in the target field.

## Mature local data

A normal v0.18 build merges and validates:

```text
AOSP PinyinIME raw Pinyin/frequency data (Apache-2.0)
Jieba default frequency dictionary (MIT; conservative AOSP-backed Pinyin derivation)
CC-CEDICT 2026-09-10 (CC BY-SA 4.0)
ESDB/SCOWL en_US-large (ESDB redistribution notice)
Unicode Emoji 17.0 emoji-test data (Unicode License v3)
project-authored fallback / product vocabulary / kaomoji
```

Minimum build gates are intentionally much higher than earlier versions:

```text
AOSP Chinese >= 40,000
Jieba-derived additions >= 40,000
CC-CEDICT parsed entries >= 110,000
combined runtime Chinese lexicon >= 150,000
English vocabulary >= 100,000
Unicode fully-qualified Emoji >= 3,000
ZH->EN translation index >= 80,000
EN->ZH translation index >= 50,000
```

Exact generated counts are written to:

```text
app/src/main/assets/ime/mature-report.json
```

These are sanity gates, not a claim of parity with proprietary commercial IME corpora.

## Fuzzy / typo / association

Fuzzy correction now uses the mature packaged lexicon instead of only a small hard-coded map. It combines:

- `zh/z`, `ch/c`, `sh/s` and common final confusions;
- `n/l`, `f/h`, `r/l`, `u/v` compatibility variants;
- adjacent-key transposition;
- QWERTY neighboring-key substitutions;
- one-extra-key deletion recovery;
- existing explicit high-confidence shortcuts;
- prefix lookup against the packaged lexicon for partial-Pinyin association.

Exact candidates remain higher confidence than fuzzy guesses.

## English

The build now uses the pinned ESDB/SCOWL `en_US-large` vocabulary. English still stays in a composing buffer and shows candidates before commit; project-authored common words/phrases and typo corrections remain higher-quality overlays where appropriate.

## Translation

The translation keyboard remains fully local at runtime.

Order:

```text
project exact phrase tables
-> CC-CEDICT exact lexical lookup
-> CC-CEDICT sharded longest-match sentence composition
-> project local sentence composer
-> explicit offline-unavailable state
```

CC-CEDICT generates separate Chinese->English and English->Chinese sharded translation assets at build time. The translation prompt fallback is never presented as if it were a translation result.

## Emoji / kaomoji / stickers

`表情 / Emoji` now contains:

- existing project-authored categorized Emoji;
- the complete build-generated Unicode 17.0 fully-qualified Emoji list;
- project-authored happy/sad/angry/funny/love kaomoji categories;
- Recent expressions;
- 24 local graphical pet stickers (8 pets × 3 moods).

Emoji/kaomoji support one-tap insert and long-press copy. Pet stickers are generated locally to private cache and use `InputContentInfo` when a target editor advertises `image/png`; otherwise they fall back to Emoji text.

## Symbols and long press

The `123` keyboard is no longer a single small symbol page. v0.18 contains 7 rotating pages:

```text
常用
标点
括号
数学
货币
箭头
标记
```

The `符号` key rotates pages.

Letter keys also expose visible long-press hints. The top row maps to digits (`q→1 ... p→0`) and the other rows map to common punctuation such as `@ # $ % & - + ( ) * ! ?`.

## Clipboard

Clipboard remains `Pinned + Recent`:

- listener attached only while the IME window is visible;
- non-sensitive Recent entries expire after about one hour;
- long-press pins/unpins;
- no background clipboard service;
- password-like/sensitive fields disable clipboard capture.

## Visual keyboard pet

v0.17 visual-pet work remains intact:

- 8 distinct pets;
- four growth stages;
- actual graphical pet preview in keyboard and settings;
- outfit overlays;
- local check-in, hatch, switch, catalog and growth;
- no overlay permission or cloud AI chat.

## Build-time data pipeline

A normal build runs:

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> tools/ime_importer.py
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon + translation shards
-> Unicode Emoji 17.0 asset
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

The user-test APK must **not** use `-PorbitSkipMatureImeData=true`.

## Runtime privacy boundary

Orbit IME v0.18 intentionally has:

- no `INTERNET` permission;
- no ads/analytics/tracking SDK;
- no Accessibility permission;
- no overlay/floating-window permission;
- no cloud prediction/dictionary sync/translation;
- no external translation API;
- no background clipboard/input harvesting;
- no full typed-stream persistence;
- no external-storage permission.

Build-machine dictionary downloads do not grant runtime network capability to the installed keyboard.

## Build

GitHub Actions remains manual-only through `workflow_dispatch`.

```text
JDK 17
Python 3.12 recommended
Android SDK 35
Android build-tools 35.0.0
Gradle 8.10.2
```

Command:

```text
gradle assembleDebug --no-daemon
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Actions artifact:

```text
orbit-ime-v0.18-debug-apk
```

## Post-build acceptance priorities

1. Type Pinyin, choose a Chinese candidate, and confirm the raw Pinyin is replaced rather than retained.
2. Test long uninterrupted Pinyin sentences and candidate latency.
3. Test typo/fuzzy queries and partial-Pinyin association.
4. Long-press every letter row and confirm the hinted number/symbol commits.
5. Rotate through all seven symbol pages.
6. Confirm Unicode Emoji all-page data, kaomoji, Recent and pet stickers.
7. Test English composing/candidates.
8. Test local CC-CEDICT-backed translation preview and `译文上屏`.
9. Re-test Clipboard Recent/Pinned and visual pets.
10. Confirm privacy mode hides extra tools and disables learning/capture.

## Commercial/data-license boundary

Runtime code remains Orbit code. Third-party datasets keep their own notices/licenses in `assets/ime/third_party_notices/`. In particular, CC-CEDICT-derived dictionary/translation data remains CC BY-SA 4.0 data and is documented separately from the application source-code license.
