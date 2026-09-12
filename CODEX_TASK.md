# Codex handoff: build Orbit IME v0.16.0 APK only

## Scope

All product design, feature work, data-source audit, dictionary preparation, importer work, local data tests, mature-asset validation, engine changes, documentation, privacy boundaries, and Gradle wiring are already prepared in the repository.

**Codex's task is only to build the APK and make minimum compilation/portability repairs if the build itself exposes an error. Do not redesign, expand, or replace features.**

Repository:

```text
https://github.com/initial2024/codex
```

## Environment

```text
JDK 17
Python 3.12 recommended
Android SDK platform 35
Android build-tools 35.0.0
Gradle 8.10.2 or compatible
```

## Automatic preBuild chain

A normal build already runs:

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> tools/ime_importer.py
-> generated mature offline assets
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

The data pipeline uses pinned/hash-verified sources documented in `DATA_SOURCES.md` and `data/ime_sources/mature_sources.json`:

- AOSP PinyinIME Chinese Pinyin/frequency data (Apache-2.0).
- Jieba default Chinese frequency dictionary (MIT), converted only with conservative AOSP-backed pronunciation derivation.
- ESDB/SCOWL US-English vocabulary (ESDB permission notice).

Required third-party notices are packaged automatically. The validator rejects changed pins, suspiciously small packs, missing N-grams/shards/notices, wrong version metadata, or forbidden manifest capabilities.

## Build command

Run exactly from repository root:

```bash
gradle assembleDebug --no-daemon
```

Do **not** add:

```text
-PorbitSkipMatureImeData=true
```

for the APK intended for user testing.

## Expected

```text
versionCode = 16
versionName = 0.16.0
APK = app/build/outputs/apk/debug/app-debug.apk
Actions artifact = orbit-ime-v0.16-debug-apk
```

## v0.16 architecture/features that must remain intact

Do not remove or bypass:

- continuous long-sentence Pinyin buffer and DP segmentation;
- adaptive long-query search limits: short input can use wider Beam/search, long input narrows segmentation/beam/entry limits instead of using one fixed expensive setting;
- candidate-query LRU and phrase-lookup LRU used to reduce repeated work while a sentence grows;
- `CompactLexiconAsset` + sentence beam search + 1/2/3-gram + local personalization;
- AOSP + conservative Jieba mature Chinese data path;
- English composing + sharded ESDB/SCOWL asset path;
- dynamic candidate/tool host refresh instead of rebuilding all keyboard keys for every letter;
- Recent/Pinned clipboard with IME-window-only listener and one-hour Recent expiry;
- live translation keyboard showing source and actual local translation preview;
- exact phrase translation -> conservative local sentence composer -> explicit unavailable state;
- normalized English local-translation lookup keys;
- local pet, skins, privacy mode, and system input-method picker.

## If build fails

Only repair the smallest build blocker, such as:

- Kotlin syntax/import/method signature;
- Android SDK API signature;
- resource/XML issue;
- Gradle task wiring;
- Python portability;
- deterministic data-pipeline bug;
- IME metadata.

Then rerun the same Gradle command.

Do not bypass mature-data testing/preparation/validation to get a green build. Do not substitute a different word list or redesign the input engine. Do not remove the adaptive search/cache changes merely to simplify compilation.

## Forbidden changes

Do not add or enable:

- `INTERNET` permission;
- cloud prediction/dictionary sync/translation;
- external translation APIs;
- ads/analytics/tracking;
- Accessibility permission;
- overlay/floating-window permission;
- background clipboard/input harvesting;
- full typed-key-stream persistence;
- surrounding-sentence or app/package learning history;
- AI pet chat;
- paid gacha;
- Pinyin 9-key, Wubi, handwriting;
- Canvas keyboard rewrite;
- Compose migration;
- Room/Realm migration;
- billing or skin marketplace.

## Build report

Return only build-relevant facts:

```text
1. git status before build
2. exact build command
3. data-pipeline test result
4. mature-data preparation result
5. mature-asset validation result
6. mature-report.json counts: AOSP Chinese / Jieba-derived / combined runtime Chinese / English / 1-2-3 gram / shard counts
7. minimum repair files, if any
8. build success/failure
9. APK path and size if successful
10. key error + exact minimum repair if failed
11. confirmation that prohibited permissions/features were not added
```
