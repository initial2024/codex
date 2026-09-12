# Codex handoff: build Orbit IME v0.19.0 APK only

## Scope

All non-build implementation is already prepared. Codex's task is **only** to build the current `main` APK and make the smallest compilation/portability repair exposed by that build.

Do not build an old v0.17/v0.18 checkout. Before building, confirm:

```text
versionCode = 19
versionName = 0.19.0
```

Repository:

```text
https://github.com/initial2024/codex
```

## Build

Environment:

```text
JDK 17
Python 3.12 recommended
Android SDK 35
Android build-tools 35.0.0
Gradle 8.10.2 or compatible
```

Run from repository root:

```bash
gradle assembleDebug --no-daemon
```

Do **not** pass `-PorbitSkipMatureImeData=true` for the user-test APK.

## Mandatory preBuild chain

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> AOSP + Jieba + ESDB/SCOWL en_US-large
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon/translation + Unicode Emoji 17.0
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

`augment_v018_data.py` is the historical filename of the licensed augmentation stage; it remains mandatory in v0.19.

## Data gates

Do not lower these thresholds just to obtain a green build:

```text
AOSP Chinese >= 40,000
Jieba-derived >= 40,000
CC-CEDICT >= 110,000
runtime Chinese >= 150,000
English >= 100,000
Unicode Emoji >= 3,000
ZH->EN translation >= 80,000
EN->ZH translation >= 50,000
1-gram >= 5,000
2-gram >= 30,000
3-gram >= 30,000
```

The previous report showing `English=81,373` and `CC-CEDICT not configured` came from the stale v0.17 pipeline. A valid v0.19 build must run the current chain above and must not reuse that result.

## v0.19 feature gates

The validator also rejects these regressions:

```text
personal learning free capacity = 20,000
personal learning Pro placeholder = 100,000
personal learning store = app-private base file + journal + compaction
pet catalog >= 16
outfit catalog >= 24
sticker moods >= 8
local sticker definitions >= 128
```

### Personal learning

Keep `UserDictionaryStore` file-backed. Do not revert to one giant SharedPreferences JSON value. Preserve legacy migration, app-private storage, append journal, periodic compaction and candidate-cache invalidation.

Persistent records remain limited to:

```text
pinyin
candidate text
frequency
updatedAt
```

Do not add surrounding conversation, app/package identity or full typed-stream persistence.

### Pets / outfits / feedback

Keep 16 catalog pets. New v0.19 pet variants intentionally reuse one of the eight stable local renderer archetypes through `visualBaseId`, so every new pet renders without requiring remote artwork.

Keep 24 catalog outfits. New variants map through `visualId` to the stable visible accessory layers, so they must not become invisible list-only items.

Keep local micro-feedback for recent candidate commit, clipboard save, translation, check-in, adoption, pet switch and outfit change. The feedback store contains only a short event code + timestamp, not the user's surrounding text.

### Stickers

With 16 pets × 8 states, `StickerPack` now defines at least 128 local stickers. New pet variants render through `visualBaseId`. Preserve `StickerOverlayRenderer`, PNG commit and Emoji fallback.

### Input / translation / symbols

Do not regress the existing v0.18 behavior:

- candidate commit replaces active composing text instead of appending raw Pinyin;
- continuous long Pinyin + DP segmentation + adaptive Beam + N-gram + local learning;
- large lexicon participates in prefix and fuzzy recovery;
- ESDB/SCOWL `en_US-large` English path;
- 7-page `SymbolLibrary` and 26-key long-press mappings;
- Unicode Emoji 17.0 full asset plus project kaomoji;
- CC-CEDICT local translation shards through `CedictTranslationAsset`;
- Recent/Pinned clipboard;
- visual pet renderer;
- privacy mode.

## If build fails

Only repair the smallest blocker:

- Kotlin syntax/import/method signature;
- Android API/ContentProvider/InputContentInfo signature;
- XML/manifest/resource issue;
- Gradle task ordering;
- Python portability/deterministic parser bug;
- IME metadata.

Do not bypass validation, delete v0.19 features, lower data gates, revert to small hard-coded dictionaries, or add runtime networking.

## Forbidden

Do not add INTERNET, cloud prediction/translation, external APIs, ads/analytics/tracking, Accessibility, overlay, external-storage permission, background clipboard/input harvesting, full typed-stream persistence, app/package learning history, AI pet chat, 9-key, Wubi, handwriting, Compose migration, full keyboard Canvas rewrite, Room/Realm, billing or marketplace features.

## Expected output

```text
versionCode = 19
versionName = 0.19.0
APK = app/build/outputs/apk/debug/app-debug.apk
Actions artifact = orbit-ime-v0.19-debug-apk
```

## Build report

Return:

```text
1. git status before build
2. git log -1 --oneline
3. exact build command
4. data-test result
5. base mature-data preparation result
6. CC-CEDICT/Unicode augmentation result
7. mature validation result
8. mature-report.json counts
9. validator-reported personal-learning/pet/outfit/sticker counts
10. minimum repair files, if any
11. build success/failure
12. APK path and size
13. confirmation prohibited permissions/features were not added
```
