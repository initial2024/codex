# Orbit IME data source strategy

Orbit IME builds a reproducible offline data pack on the build machine. The installed keyboard itself remains offline.

## v0.20 pipeline

```text
tools/test_ime_data_pipeline_v020.py
-> tools/prepare_mature_ime_data.py
-> base AOSP/Jieba/ESDB pack
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon + translation shards
-> Unicode Emoji 17.0
-> tools/augment_v020_data.py
-> CC-CEDICT four-character phrase/idiom boost
-> project-authored software/platform/product vocabulary
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

`augment_v018_data.py` is a historical filename. It remains mandatory because that stage owns pinned CC-CEDICT translation/lexicon data and Unicode Emoji.

Pinned third-party source metadata is stored in `data/ime_sources/mature_sources.json`.

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

CC-CEDICT is used for:

1. lower-confidence Chinese lexical rows;
2. sharded local Chinese->English / English->Chinese translation assets;
3. a v0.20 four-character phrase/idiom boost layer with a higher synthetic ranking frequency.

The four-character layer is derived from the already-audited CC-CEDICT source instead of adding a separate internet-scraped idiom repository. It is therefore still CC BY-SA 4.0 data and keeps the same required notice/share-alike boundary.

## Project-authored software/platform/product vocabulary

File:

```text
data/ime_sources/seed_software.tsv
```

License boundary:

```text
PROJECT
```

This is a manually maintained Orbit vocabulary layer for common product/platform names such as Chinese messaging/e-commerce/media apps, AI products, developer tools, operating systems, browsers, cloud/database tools and common international platforms.

Examples include:

```text
微信 / QQ / 支付宝 / 淘宝 / 京东 / 抖音 / 小红书 / 哔哩哔哩
ChatGPT / OpenAI / Codex / DeepSeek / Qwen / Gemini / Claude
GitHub / VS Code / Android Studio / Gradle / Kotlin / Python
Docker / Vercel / Cloudflare / Supabase / Windows / Android / iOS
```

The build requires at least 100 project-authored rows. These frequencies are explicit curated product weights, not claims of corpus frequency.

## ESDB / SCOWL en_US-large

```text
Repository: en-wl/wordlist-diff
Tag: rel-2026.02.25
File: en_US-large.txt
Git blob SHA-1: f802304dc6e436964094cdb6c6b743092e7af971
License identifier in Orbit: ESDB-2026
Copyright blob: 562ec7df17753481162f2b993e2dbd47cea77b2f
```

SCOWL ordering is not treated as usage frequency. Project-authored high-frequency English remains the ranking overlay.

AOSP/Lineage LatinIME's bundled dictionary remains rejected because its NOTICE includes third-party dictionary material marked `Used by permission`.

## Unicode Emoji 17.0

```text
Source: https://www.unicode.org/Public/emoji/17.0/emoji-test.txt
SHA-256: 07ee0565612af5d8cf36ea7d2cd7d255429441059133c60f863e97e648ebeb29
License: Unicode License v3
```

Only fully-qualified sequences are packaged into `ime/emoji_unicode.txt`.

## Required v0.20 minimums

```text
AOSP Chinese >= 40,000
Jieba-derived additions >= 40,000
CC-CEDICT parsed entries >= 110,000
CC-CEDICT four-character layer >= 3,000
project software/platform vocabulary >= 100
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

Exact generated counts are written to:

```text
app/src/main/assets/ime/mature-report.json
```

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

Readers use bounded caches; the complete pack is not loaded into one giant Kotlin map.

## Project-authored local data

```text
data/ime_sources/seed_lexicon.tsv
data/ime_sources/seed_english.tsv
data/ime_sources/seed_ngram.tsv
data/ime_sources/seed_software.tsv
NextPhraseData.kt
ExpressionLibrary.kt
SymbolLibrary.kt
```

`NextPhraseData.kt` is a high-confidence post-commit association overlay. The longer tail comes from packaged 2/3-gram data through `NextAssociationEngine`.

## Validation

`tools/test_ime_data_pipeline_v020.py` runs the original mature-data tests and additionally verifies the v0.20 four-character derivation plus software-vocabulary size.

`tools/validate_mature_ime_assets.py` fails closed on stale/small data, missing source metadata, wrong version, forbidden Android capabilities, missing v0.20 idiom/software stages, reduced candidate pools, missing association/context-translation wiring, or regressed local pet/sticker/personalization features.

## Runtime privacy boundary

Build-machine downloads do not grant runtime network capability. Orbit IME requests no INTERNET, Accessibility, overlay, external storage, ads/analytics, cloud prediction, cloud translation, or background input collection.
