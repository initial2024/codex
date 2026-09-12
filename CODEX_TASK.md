# Codex handoff: build Orbit IME v0.18.0 APK only

## Scope

All non-build work is already prepared in the repository: composing replacement fix, expanded licensed datasets, fuzzy/prefix engine changes, multi-page symbols, long-press mappings, Unicode Emoji, CC-CEDICT translation assets, visual pet, expressions/stickers, clipboard, privacy boundaries and Gradle wiring.

**Codex's task is only to build the APK and make the minimum compilation/portability repair exposed by the build. Do not redesign or expand features.**

Repository:

```text
https://github.com/initial2024/codex
```

## Environment

```text
JDK 17
Python 3.12 recommended
Android SDK 35
Android build-tools 35.0.0
Gradle 8.10.2 or compatible
```

## Build command

Run exactly:

```bash
gradle assembleDebug --no-daemon
```

Do **not** pass `-PorbitSkipMatureImeData=true` for the user-test APK.

## Automatic preBuild chain

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> base AOSP + Jieba + ESDB-large assets
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon/translation shards + Unicode Emoji 17.0
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

## Expected

```text
versionCode = 18
versionName = 0.18.0
APK = app/build/outputs/apk/debug/app-debug.apk
Actions artifact = orbit-ime-v0.18-debug-apk
```

## Required data gates

```text
AOSP Chinese >= 40,000
Jieba-derived additions >= 40,000
CC-CEDICT parsed entries >= 110,000
combined runtime Chinese >= 150,000
English >= 100,000
Unicode Emoji >= 3,000
ZH->EN translation index >= 80,000
EN->ZH translation index >= 50,000
1-gram >= 5,000
2-gram >= 30,000
3-gram >= 30,000
```

Do not lower these gates merely to obtain a green build. If a gate fails, report the actual generated counts and fix only a deterministic parser/configuration bug.

## v0.18 behavior that must remain intact

### Composing replacement

- `commitPinyinCandidate()` must commit a candidate directly over the active composing region;
- it must **not** call `finishComposingText()` before candidate commit;
- English candidate commit follows the same rule;
- clearing composition / translation-mode transfer must remove raw composing text rather than leaving it in the target field;
- do not regress to failures such as `sj数据库不够hy还有问题`.

### Chinese / fuzzy prediction

- AOSP + conservative Jieba + CC-CEDICT mature Chinese path;
- DP segmentation + adaptive Beam Search + 1/2/3-gram + local learning;
- mature lexicon participates in prefix association;
- mature lexicon participates in fuzzy/typo variants;
- exact candidates rank ahead of fuzzy guesses;
- QWERTY-neighbor, transposition and extra-key recovery remain lower-confidence paths.

### English

- ESDB/SCOWL `en_US-large` sharded data path;
- English composing buffer/candidates remain active.

### Symbols / long press

- `SymbolLibrary` remains active;
- 7 pages: 常用 / 标点 / 括号 / 数学 / 货币 / 箭头 / 标记;
- `符号` rotates pages;
- 26-key long-press hints remain visible;
- q..p long press maps to 1..0;
- other letter long presses commit common punctuation.

### Emoji / kaomoji / stickers

- `UnicodeEmojiAsset` loads build-generated Unicode Emoji 17.0 data;
- project-authored Emoji/kaomoji categories remain;
- Recent expressions and long-press copy remain;
- 24 local graphical pet stickers remain with PNG commit + Emoji fallback;
- `OrbitStickerProvider` stays `exported=false`, `grantUriPermissions=true`.

### Translation

- project exact phrase tables remain;
- `CedictTranslationAsset` is initialized by the IME;
- CC-CEDICT exact lookup and sharded longest-match composition remain;
- no cloud/external translation API is added;
- unavailable state remains explicit.

### Other retained modules

- visual pet renderer/stages/outfits;
- Recent/Pinned clipboard with IME-window-only listener;
- privacy mode;
- skins;
- Android input-method picker.

## If build fails

Only repair the smallest blocker:

- Kotlin syntax/import/method signature;
- Android API/ContentProvider/InputContentInfo signature;
- resource/XML/manifest issue;
- Gradle task ordering;
- Python portability/deterministic parser bug;
- IME metadata.

Then rerun the same build command.

Do not delete v0.18 features, bypass data validation, revert to small hard-coded dictionaries, or add runtime network access to make the build pass.

## Forbidden

Do not add:

- `INTERNET` permission;
- cloud prediction/dictionary sync/translation;
- external translation APIs;
- ads/analytics/tracking;
- Accessibility;
- overlay/floating window;
- background clipboard/input harvesting;
- full typed-stream persistence;
- app/package learning history;
- external-storage permission;
- AI pet chat;
- 9-key/Wubi/handwriting;
- Compose migration/full keyboard Canvas rewrite;
- Room/Realm;
- billing/skin marketplace.

## Build report

Return:

```text
1. git status before build
2. exact build command
3. offline data-test result
4. base mature-data preparation result
5. v0.18 augmentation result
6. mature validation result
7. mature-report.json counts (AOSP / Jieba / CC-CEDICT / runtime Chinese / English / Emoji / translation / N-grams / shards)
8. minimum repair files, if any
9. build success/failure
10. APK path and size
11. key error + exact minimum repair if failed
12. confirmation prohibited permissions/features were not added
```
