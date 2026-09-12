# Codex handoff: build Orbit IME v0.15.0 APK only

## Scope

All product design, data-source audit, dictionary preparation, importer work, data-pipeline tests, mature-asset validation, engine architecture, documentation, privacy boundaries, and build wiring are already prepared in the repository.

**Codex's task is only to build the APK and make minimum compilation repairs if required. Do not redesign or expand the product.**

Repository:

```text
https://github.com/initial2024/codex
```

## Required environment

```text
JDK 17
Python 3.12 recommended
Android SDK platform 35
Android build-tools 35.0.0
Gradle 8.10.2 or compatible
```

## What Gradle already does automatically

The app module `preBuild` chain runs:

```text
tools/test_ime_data_pipeline.py
        ↓
tools/prepare_mature_ime_data.py
        ↓
tools/ime_importer.py
        ↓
generated mature offline assets
        ↓
tools/validate_mature_ime_assets.py
        ↓
Android build
```

The final validator fails the build if the mature pack is suspiciously small, required N-grams/shards/notices are missing, source pins/licenses are absent, the version is wrong, or forbidden manifest capabilities appear.

Do not manually replace this process with copied dictionary files.

The mature build uses pinned and hash-verified sources documented in `DATA_SOURCES.md` and `data/ime_sources/mature_sources.json`:

- AOSP PinyinIME dictionary for Chinese Pinyin/frequency data.
- ESDB/SCOWL generated US English word list for English vocabulary completion.

Required notices are copied into the packaged asset directory automatically.

## Build command

From repository root, run exactly:

```bash
gradle assembleDebug --no-daemon
```

Do **not** add `-PorbitSkipMatureImeData=true` for the APK intended for user testing.

## Expected APK

```text
app/build/outputs/apk/debug/app-debug.apk
```

Expected version:

```text
versionCode = 15
versionName = 0.15.0
```

## If build fails

Only make the minimum necessary repair for Kotlin compilation, imports/method signatures, Android resources/XML, Gradle task wiring, Python invocation portability, IME metadata, or an obvious deterministic data-pipeline bug. Then rerun the same Gradle command.

Do not delete or bypass mature-data testing/preparation/validation merely to make the build pass.

## Architecture that must remain intact

Chinese:

```text
PinyinSegmenter
-> CompactLexiconAsset
-> PinyinImeEngine beam search
-> NGramLanguageModel
-> CandidateRanker
-> UserDictionaryStore local personalization
```

English:

```text
English composing buffer
-> CompactEnglishAsset (sharded mature asset when present)
-> EnglishImeEngine
-> EnglishDictionary fallback
```

## Non-negotiable constraints

Do not add or enable `INTERNET`, cloud prediction/dictionary sync/translation, external translation APIs, ads/analytics/tracking, Accessibility, overlay/floating-window capabilities, background input/clipboard harvesting, full typed-stream persistence, surrounding-sentence/app-package learning history, AI pet chat, paid gacha, 9-key, Wubi, handwriting, Canvas/Compose rewrites, Room/Realm, billing, or a skin marketplace.

## Final report

Return only the build-relevant result:

```text
1. git status before build
2. exact build command
3. preBuild data-pipeline test result
4. mature-data preparation result
5. mature asset validation result
6. mature-report.json counts (Chinese / English / 1-2-3 gram / shard counts)
7. any files changed by minimum compilation repair
8. build success/failure
9. APK path and size if successful
10. key error + exact minimum repair if failed
11. confirmation prohibited permissions/features were not added
```
