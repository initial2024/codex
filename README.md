# Orbit IME Android

Orbit IME is a privacy-first Android input method with local Chinese/English prediction, local personalization, clipboard history, offline translation, visual keyboard pets, Emoji/kaomoji, local stickers and skins.

## Current version

```text
0.19.0
```

v0.19 keeps the v0.18 composing/data-engine work and closes two remaining gaps found during testing:

- the previous `English=81,373 / CC-CEDICT not configured` report came from a stale v0.17 build; current main mandates the complete v0.19 mature-data chain;
- local personalization, pets, outfits and micro-feedback are expanded substantially.

## Mature data pipeline

A normal build runs:

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> AOSP PinyinIME + Jieba + ESDB/SCOWL en_US-large
-> tools/augment_v018_data.py
-> broader SCOWL-large repack + CC-CEDICT + Unicode Emoji 17.0
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

`augment_v018_data.py` is the historical filename of the licensed augmentation stage; it remains mandatory in v0.19.

Required minimums:

```text
AOSP Chinese >= 40,000
Jieba-derived additions >= 40,000
CC-CEDICT >= 110,000
combined runtime Chinese >= 150,000
English >= 100,000
Unicode Emoji >= 3,000
ZH->EN translation >= 80,000
EN->ZH translation >= 50,000
1-gram >= 5,000
2-gram >= 30,000
3-gram >= 30,000
```

The English augmentation re-reads pinned `en_US-large` and keeps valid lowercase words, proper names and acronyms under normalized lookup keys instead of discarding all non-lowercase vocabulary.

Exact generated counts are written to:

```text
app/src/main/assets/ime/mature-report.json
```

## Chinese input

```text
continuous Pinyin
-> exact packaged/user candidates
-> DP segmentation
-> compact sharded lexicon
-> adaptive phrase Beam Search
-> static frequency + 1/2/3-gram
-> local user-frequency boost
-> prefix association
-> lower-confidence fuzzy/keyboard-typo recovery
-> top candidates
```

Candidate commit replaces Android's active composing region. Raw Pinyin/English is not committed first, preventing failures such as:

```text
sj数据库不够hy还有问题
```

Exact candidates remain higher confidence than fuzzy guesses.

## Local personalization v0.19

Personal learning capacity is now:

```text
Free base: 20,000 entries
Future Pro capacity placeholder: 100,000 entries
```

The store is no longer one giant SharedPreferences JSON value. It uses app-private files:

```text
files/orbit-user-dictionary/dictionary.tsv
files/orbit-user-dictionary/journal.tsv
```

Candidate selection appends one small absolute-state journal row. The journal is periodically compacted into the base dictionary. Old SharedPreferences user-learning data is migrated locally once.

Persistent fields remain only:

```text
pinyin
candidate text
frequency
updatedAt
```

No full conversation, app/package identity, surrounding sentence or complete key stream is persisted.

## English

The mature path uses pinned ESDB/SCOWL `en_US-large`. English stays in an Android composing buffer, supports candidates/prefixes and keeps project-authored high-frequency/phrase/typo overlays.

## Translation

Translation remains fully local at runtime:

```text
project exact phrase tables
-> CC-CEDICT exact lexical lookup
-> CC-CEDICT sharded longest-match composition
-> project local sentence composer
-> explicit offline-unavailable state
```

The build requires at least 80k ZH->EN and 50k EN->ZH index entries. Prompt fallback is never presented as a translation result.

## Symbols / long press

The `123` keyboard has seven pages:

```text
常用 / 标点 / 括号 / 数学 / 货币 / 箭头 / 标记
```

Letter keys keep visible long-press hints. `q..p` map to `1..0`; other letters provide common punctuation.

## Emoji / kaomoji

The expression panel includes:

- build-generated Unicode Emoji 17.0 fully-qualified sequences;
- project categorized Emoji;
- expanded project-authored kaomoji variants;
- Recent expressions;
- local pet stickers.

Emoji/kaomoji support one-tap insert and long-press copy.

## Pets, outfits and stickers v0.19

The catalog now contains:

```text
16 pets
24 outfits
8 sticker states per pet
128 local sticker definitions
```

The original eight stable renderer archetypes remain the visual foundation. New v0.19 catalog pets map through `visualBaseId`, so every new pet is actually visible without remote artwork. New outfit variants map through `visualId` to existing visible accessory layers such as halo, visor, hat, cloak, bow, orbit, aura and tail.

Pet feedback is still local and lightweight. Recent actions such as candidate selection, clipboard save, translation, check-in, adoption, pet switching and outfit changes can produce a short temporary response. Only a small event code and timestamp are stored; surrounding user text is not.

The local sticker pack is generated into app-private cache. Compatible editors receive PNG content through `InputContentInfo`; unsupported editors receive Emoji fallback text. `OrbitStickerProvider` stays non-exported with temporary URI grants only.

## Clipboard

Clipboard remains `Pinned + Recent`. The listener exists only while the IME window is visible. Orbit has no background clipboard-harvesting service.

## Privacy boundary

Orbit IME v0.19 intentionally has:

- no `INTERNET` permission;
- no ads/analytics/tracking;
- no Accessibility permission;
- no overlay/floating-window permission;
- no cloud prediction/dictionary sync/translation;
- no external translation API;
- no background input/clipboard harvesting;
- no full typed-stream persistence;
- no external-storage permission.

## Data licenses

Pinned build-time data remains independently licensed:

```text
AOSP PinyinIME             Apache-2.0
Jieba frequency data       MIT
CC-CEDICT                  CC BY-SA 4.0
ESDB/SCOWL en_US-large     ESDB redistribution notice
Unicode Emoji 17.0         Unicode License v3
```

Required notices are packaged under `assets/ime/third_party_notices/`. CC-CEDICT-derived lexicon/translation data remains CC BY-SA 4.0 data and is documented separately from application source code.

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

Do not use `-PorbitSkipMatureImeData=true` for the user-test APK.

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Artifact:

```text
orbit-ime-v0.19-debug-apk
```

## Post-build acceptance priorities

1. Confirm composing replacement: raw Pinyin/English is not left before committed candidates.
2. Measure long uninterrupted Pinyin latency and top-1/top-3 quality.
3. Confirm mature-report passes all Chinese/English/CC-CEDICT/Emoji/translation/N-gram gates.
4. Confirm personal learning survives restart and legacy migration; test journal compaction and clear/reset.
5. Confirm all 16 catalog pets can be selected/represented and 24 outfit entries map to visible layers.
6. Confirm pet micro-feedback appears after supported local actions and expires.
7. Confirm 128 sticker definitions render/send/fallback correctly.
8. Re-test Unicode Emoji, kaomoji, symbols, long press, clipboard and local translation.
9. Confirm privacy mode blocks learning/capture and hides extra tools.
