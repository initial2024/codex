# Orbit IME data source strategy

Orbit IME builds a reproducible offline dictionary pack on the build machine. The installed keyboard itself remains offline.

## v0.16 default pipeline

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> tools/ime_importer.py
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

Pinned source metadata is stored in `data/ime_sources/mature_sources.json`. Downloaded files are verified against Git blob SHA-1 values; a changed upstream file fails the build.

## Chinese source 1: AOSP PinyinIME

```text
Repository: LineageOS/android_packages_inputmethods_PinyinIME
Commit: a08d08cdea4319fb74b55289e1b4e1b1a5249bc2
File: jni/data/rawdict_utf16_65105_freq.txt
Git blob SHA-1: 28805ba68eb8df265c1d227fb99e841ff3302aef
License: Apache-2.0
```

Orbit accepts normal `flag == 0` CJK entries, preserves their tone-less Pinyin and frequency signal, and derives bounded character 1/2/3-grams. The pinned AOSP NOTICE is packaged with the APK data assets.

## Chinese source 2: Jieba frequency dictionary

```text
Repository: fxsjy/jieba
Commit: 67fa2e36e72f69d9134b8a1037b83fbb070b9775
File: jieba/dict.txt
Git blob SHA-1: fc6075f64943e1861c420db4da38063de9d8afc5
License: MIT
LICENSE blob SHA-1: 9d7e66b431461c785329a1b52199d4207daefacc
```

Jieba's default dictionary adds much broader Chinese word/frequency coverage, but it does not provide Pinyin. Orbit therefore does **not** blindly generate pronunciations.

Preparation policy:

1. if the same word already exists in AOSP, keep AOSP's exact phrase Pinyin instead;
2. otherwise derive a word reading only when every character has an AOSP single-character reading whose dominant frequency is sufficiently stronger than competing readings;
3. skip ambiguous or missing readings rather than guessing;
4. give these derived entries lower confidence than native AOSP phrase rows;
5. derive additional bounded character N-grams from accepted Jieba words;
6. package the pinned Jieba MIT license.

This trades some theoretical coverage for fewer incorrect polyphonic-word candidates.

## English source: ESDB / SCOWL

```text
Repository: en-wl/wordlist-diff
Tag: rel-2026.02.25
File: en_US.txt
Git blob SHA-1: b4222bda8be5826fce1635230f9503234ec31e5a
License identifier in Orbit: ESDB-2026
Copyright blob: 562ec7df17753481162f2b993e2dbd47cea77b2f
```

The ESDB permission notice grants use/copy/modify/distribute/sell rights subject to retaining the required notice. Orbit treats this as vocabulary/completion data, not as a true frequency corpus; project-authored common English words/phrases keep stronger scores.

## Rejected English source

AOSP/Lineage LatinIME's bundled dictionary is not imported because its NOTICE includes third-party dictionary material marked `Used by permission`. Orbit does not assume the surrounding Apache-licensed code grants equivalent redistribution rights for that data.

## Required mature-pack minimums

The v0.16 validator currently requires at least:

```text
AOSP Chinese entries: 40,000
Jieba-derived additional entries: 40,000
combined runtime Chinese lexicon: 90,000
English entries: 50,000
1-gram rows: 2,000
2-gram rows: 10,000
3-gram rows: 10,000
Chinese shards: 20+
English shards: 20+
```

The exact generated counts are written to `app/src/main/assets/ime/mature-report.json` during a normal build. These thresholds are sanity gates, not claims that the pack equals proprietary commercial IME corpora.

## Project-authored fallback data

```text
data/ime_sources/seed_lexicon.tsv
data/ime_sources/seed_english.tsv
data/ime_sources/seed_ngram.tsv
```

These provide deterministic fallback/product vocabulary and are merged with the mature pack.

## Runtime format

```text
ime/lexicon/a.odict ... z.odict
ime/english/a.odict ... z.odict
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

Counts use base36. Chinese and English runtime readers use bounded LRU shard caches rather than loading the entire vocabulary into one Kotlin map.

Generated notices:

```text
ime/third_party_notices/AOSP-PinyinIME-NOTICE.txt
ime/third_party_notices/Jieba-LICENSE.txt
ime/third_party_notices/ESDB-SCOWL-Copyright.txt
```

## License gate

Every imported source declares source name, path, format, license, URL, redistribution flag, and attribution. Strict accepted identifiers include:

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

The importer fails closed on missing files, disallowed redistribution, missing third-party URL/attribution, or an unknown strict-mode license.

## Offline tests

`python tools/test_ime_data_pipeline.py` covers hash calculation, AOSP parsing, conservative Jieba pronunciation derivation including ambiguous-character rejection, ESDB normalization, license fail-closed behavior, compact packing, N-gram packing, and English sharding.

## Runtime privacy boundary

Build-machine source downloads do not grant runtime network capability. Orbit IME still requests no INTERNET, Accessibility, overlay, ads/analytics, cloud prediction, or background input collection.
