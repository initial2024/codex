# Codex handoff: build Orbit IME v0.15.0 APK only

## Scope

All product design, data-source audit, dictionary preparation, importer work, local data-pipeline tests, engine architecture, documentation, privacy boundaries, and build wiring are already prepared in the repository.

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
generated mature offline assets under app/src/main/assets/ime
        ↓
Android build
```

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

Do **not** add:

```text
-PorbitSkipMatureImeData=true
```

for the APK intended for user testing. That switch is only for deliberately offline development.

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

Only make the minimum necessary repair for:

- Kotlin compilation;
- imports or method signatures;
- Android resources/XML;
- Gradle task wiring;
- Python invocation portability;
- IME metadata;
- an obvious deterministic data-pipeline bug.

After the minimum repair, rerun:

```bash
gradle assembleDebug --no-daemon
```

Do not delete or bypass the mature-data preparation merely to make the build pass.

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

Do not replace these with a giant hardcoded Kotlin map.

## Non-negotiable constraints

Do not add or enable:

- `INTERNET` permission;
- cloud prediction;
- cloud dictionary sync;
- cloud translation or an external translation API;
- ad/analytics/tracking SDKs;
- Accessibility permission;
- overlay/floating-window permission;
- background input/clipboard harvesting;
- full typed-key-stream persistence;
- surrounding-sentence or app/package learning history;
- AI pet chat;
- paid gacha;
- Pinyin 9-key, Wubi, handwriting;
- Canvas keyboard rewrite;
- Compose migration;
- Room/Realm migration;
- billing or skin marketplace.

## Final report

Return only the build-relevant result:

```text
1. git status before build
2. exact build command
3. whether preBuild data tests passed
4. whether mature-data preparation passed
5. generated mature-report.json counts (Chinese / English / 1-2-3 gram)
6. any files changed by the minimum compilation repair
7. build success/failure
8. APK path and APK size if successful
9. key error and exact minimum repair if failed
10. confirmation that prohibited permissions/features were not added
```
