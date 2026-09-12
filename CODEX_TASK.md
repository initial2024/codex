# Codex handoff: build Orbit IME v0.17.0 APK only

## Scope

All product design, feature work, data-source audit, dictionary preparation, importer work, local data tests, mature-asset validation, engine changes, visual-pet work, expression/sticker work, documentation, privacy boundaries, and Gradle wiring are already prepared in the repository.

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

The mature dictionary path remains pinned/hash-verified:

- AOSP PinyinIME Chinese Pinyin/frequency data (Apache-2.0).
- Jieba default Chinese frequency dictionary (MIT), with conservative AOSP-backed pronunciation derivation.
- ESDB/SCOWL US-English vocabulary.

## Build command

Run exactly from repository root:

```bash
gradle assembleDebug --no-daemon
```

Do **not** pass:

```text
-PorbitSkipMatureImeData=true
```

for the user-test APK.

## Expected

```text
versionCode = 17
versionName = 0.17.0
APK = app/build/outputs/apk/debug/app-debug.apk
Actions artifact = orbit-ime-v0.17-debug-apk
```

## Architecture/features that must remain intact

Do not remove or bypass:

### Input engine

- continuous long-sentence Pinyin buffer and DP segmentation;
- adaptive long-query search limits;
- candidate-query LRU and phrase-lookup LRU;
- `CompactLexiconAsset` + sentence beam search + 1/2/3-gram + local personalization;
- AOSP + conservative Jieba mature Chinese data path;
- English composing + sharded ESDB/SCOWL asset path;
- dynamic candidate/tool host refresh instead of rebuilding all keyboard keys for every letter.

### Clipboard / translation

- Recent/Pinned clipboard with IME-window-only listener and one-hour Recent expiry;
- live local translation keyboard with source/result preview and explicit translation commit;
- exact phrase translation -> conservative sentence composer -> explicit unavailable state.

### Visual pet

- `PetAvatarView` and `PetAvatarRenderer` must compile and remain active;
- the pet panel must render an actual graphical pet, not text-only state;
- all 8 pet ids must have a distinct visual path;
- 4 stages must affect size/detail;
- stage 3/4 aura/orbit details must remain;
- equipped outfit overlays must remain;
- when the pet is visible, the idle quick-phrase row must show a small clickable pet preview;
- settings must show the current pet preview.

### Emoji / kaomoji

- top keyboard toolbar `表情 / Emoji` entry;
- packaged categories from `ExpressionLibrary`;
- paging for large categories;
- one-tap insert;
- long-press copy;
- local Recent expressions through `ExpressionStore`;
- Clear Recent.

### Local image stickers

- 24 local pet stickers = 8 pets × 3 moods;
- graphical sticker thumbnails through `StickerPreviewView`;
- PNG rendering through `StickerRenderer` into app-private cache;
- `OrbitStickerProvider` remains `exported=false` with `grantUriPermissions=true`;
- compatible editors receive `InputContentInfo` image/png content;
- unsupported editors fall back to Emoji text;
- no external storage or network dependency for stickers.

### Privacy / other

- privacy mode hides pet, expressions/stickers, clipboard and translation tools;
- local pet state, skins and Android IME picker remain intact.

## If build fails

Only repair the smallest build blocker, such as:

- Kotlin syntax/import/method signature;
- Android SDK `InputContentInfo` / `ContentProvider` signature;
- manifest provider declaration;
- resource/XML issue;
- Gradle task wiring;
- Python portability;
- deterministic data-pipeline bug;
- IME metadata.

Then rerun the same Gradle command.

Do not bypass mature-data validation, remove adaptive caches/search, replace the visual pet with text, delete Emoji/kaomoji data, disable sticker fallback, export the sticker provider, or substitute another dictionary source just to obtain a green build.

## Forbidden changes

Do not add or enable:

- `INTERNET` permission;
- cloud prediction/dictionary sync/translation;
- external translation APIs;
- remote sticker downloads;
- external-storage permission for stickers;
- ads/analytics/tracking;
- Accessibility permission;
- overlay/floating-window permission;
- background clipboard/input harvesting;
- full typed-key-stream persistence;
- surrounding-sentence or app/package learning history;
- AI pet chat;
- paid gacha;
- Pinyin 9-key, Wubi, handwriting;
- Compose migration;
- full Canvas keyboard rewrite (the small pet/sticker renderer is intentional and must remain);
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
7. Kotlin/Android compile result for PetAvatarView / StickerPack / OrbitInputMethodService
8. minimum repair files, if any
9. build success/failure
10. APK path and size if successful
11. key error + exact minimum repair if failed
12. confirmation that prohibited permissions/features were not added
```
