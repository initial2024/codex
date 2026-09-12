# Orbit IME data source strategy

Orbit IME builds a reproducible offline data pack on the build machine. The installed keyboard itself remains offline.

## v0.19 pipeline

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> base AOSP/Jieba/ESDB pack
-> tools/augment_v018_data.py
-> broader ESDB/SCOWL large repack
-> CC-CEDICT lexicon + translation shards
-> Unicode Emoji 17.0 asset
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

`augment_v018_data.py` is the historical filename of the licensed augmentation stage and remains mandatory in v0.19.

Pinned source metadata is stored in `data/ime_sources/mature_sources.json`.

## AOSP PinyinIME

```text
Repository: LineageOS/android_packages_inputmethods_PinyinIME
Commit: a08d08cdea4319fb74b55289e1b4e1b1a5249bc2
File: jni/data/rawdict_utf16_65105_freq.txt
Git blob SHA-1: 28805ba68eb8df265c1d227fb99e841ff3302aef
License: Apache-2.0
```

AOSP provides direct Pinyin/frequency rows and remains the highest-confidence pronunciation source.

## Jieba frequency dictionary

```text
Repository: fxsjy/jieba
Commit: 67fa2e36e72f69d9134b8a1037b83fbb070b9775
File: jieba/dict.txt
Git blob SHA-1: fc6075f64943e1861c420db4da38063de9d8afc5
License: MIT
```

Jieba adds broad Chinese frequency coverage but does not provide Pinyin. Orbit derives a reading only when AOSP phrase/single-character evidence is sufficiently unambiguous; otherwise the row is skipped.

## CC-CEDICT

```text
Mirror: rhcarvalho/cedict
Pinned commit: 9118dab4ea21849c571a20d56f1f1621a0423d07
File: cedict_1_0_ts_utf-8_mdbg.txt
Git blob SHA-1: 2b05f59d39ed57b4ca3512f9210aa8f11a402cc8
Release metadata: 2026-09-10T22:27:42Z
License: CC BY-SA 4.0
```

CC-CEDICT contributes lower-confidence Chinese lexical rows and generates sharded local Chinese->English / English->Chinese translation assets. v0.19 also indexes conservative variants such as English glosses without a leading `to` and simple comma/or alternatives, improving local translation lookup coverage without a cloud model.

CC-CEDICT-derived assets remain CC BY-SA 4.0 data and keep a separate packaged notice.

## ESDB / SCOWL en_US-large

```text
Repository: en-wl/wordlist-diff
Tag: rel-2026.02.25
File: en_US-large.txt
Git blob SHA-1: f802304dc6e436964094cdb6c6b743092e7af971
License identifier in Orbit: ESDB-2026
Copyright blob: 562ec7df17753481162f2b993e2dbd47cea77b2f
```

The stale v0.17 build used a smaller English path and produced 81,373 entries. v0.19 re-reads pinned `en_US-large` during augmentation and keeps valid lowercase vocabulary plus useful proper-name/acronym spellings under normalized lowercase lookup keys. SCOWL ordering is not treated as real usage frequency; project-authored common words/phrases remain ranking overlays.

AOSP/Lineage LatinIME's bundled dictionary remains rejected because its NOTICE contains third-party dictionary material marked `Used by permission`.

## Unicode Emoji 17.0

```text
Source: https://www.unicode.org/Public/emoji/17.0/emoji-test.txt
SHA-256: 07ee0565612af5d8cf36ea7d2cd7d255429441059133c60f863e97e648ebeb29
License: Unicode License v3
```

Only `fully-qualified` sequences are packaged into `ime/emoji_unicode.txt`. Project-authored categorized Emoji and kaomoji remain curated overlays.

## Required v0.19 minimums

```text
AOSP Chinese >= 40,000
Jieba-derived additions >= 40,000
CC-CEDICT parsed entries >= 110,000
combined runtime Chinese lexicon >= 150,000
English vocabulary >= 100,000
Unicode fully-qualified Emoji >= 3,000
ZH->EN translation index >= 80,000
EN->ZH translation index >= 50,000
1-gram >= 5,000
2-gram >= 30,000
3-gram >= 30,000
Chinese lexicon shards >= 20
English shards >= 20
Chinese translation shards >= 40
English translation shards >= 20
```

Exact generated counts are written to `app/src/main/assets/ime/mature-report.json`.

## Runtime layout

```text
ime/lexicon/*.odict
ime/english/*.odict
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
ime/translation/zh/*.odict
ime/translation/en/*.odict
ime/emoji_unicode.txt
ime/third_party_notices/*
```

Readers use bounded shard caches; the full data pack is not loaded as one giant Kotlin map.

## Project-authored local data

```text
data/ime_sources/seed_lexicon.tsv
data/ime_sources/seed_english.tsv
data/ime_sources/seed_ngram.tsv
ExpressionLibrary.kt
SymbolLibrary.kt
```

These provide deterministic product vocabulary, kaomoji and symbol behavior even in deliberately reduced development builds.

## Validation

`tools/test_ime_data_pipeline.py` covers source hashing/parsing, CC-CEDICT translation sharding, Unicode Emoji parsing, license fail-closed behavior, compact packing, N-grams and English sharding.

`tools/validate_mature_ime_assets.py` rejects stale or suspiciously small packs, missing pins/notices/shards, wrong app version, forbidden manifest capabilities, and v0.19 local-feature regressions such as a tiny personal dictionary, missing pets/outfits, or too few local sticker definitions.

## Runtime privacy boundary

Build-machine source downloads do not grant runtime network capability. Orbit IME requests no INTERNET, Accessibility, overlay, external storage, ads/analytics, cloud prediction, cloud translation, or background input collection.
