# Codex handoff: build Orbit IME v0.20.0 APK only

## Scope

All non-build implementation for v0.20 is already prepared. Codex must only build current `main` and make the smallest compilation/portability repair exposed by the build.

Before building confirm:

```text
versionCode = 20
versionName = 0.20.0
```

Repository:

```text
https://github.com/initial2024/codex
```

## Build

```text
JDK 17
Python 3.12 recommended
Android SDK 35
Android build-tools 35.0.0
Gradle 8.10.2 or compatible
```

Run:

```bash
gradle assembleDebug --no-daemon
```

Do not pass `-PorbitSkipMatureImeData=true`.

## Mandatory preBuild

```text
tools/test_ime_data_pipeline_v020.py
-> tools/prepare_mature_ime_data.py
-> AOSP + Jieba + pinned ESDB/SCOWL en_US-large
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon/translation + Unicode Emoji 17.0
-> tools/augment_v020_data.py
-> broad ESDB-large normalization + CC-CEDICT four-character boost + project software vocabulary
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

The v0.20 English stage deliberately re-parses the already-pinned `en_US-large` cache so valid proper-name/acronym spellings are not discarded solely because they contain uppercase letters. Do not remove that pass if the old `81,373` result reappears.

## Required data gates

```text
AOSP >= 40,000
Jieba-derived >= 40,000
CC-CEDICT >= 110,000
CC-CEDICT four-character layer >= 3,000
project software/platform vocabulary >= 100
runtime Chinese >= 150,000
English >= 100,000
Unicode Emoji >= 3,000
ZH->EN >= 80,000
EN->ZH >= 50,000
1-gram >= 5,000
2-gram >= 30,000
3-gram >= 30,000
```

Do not lower gates simply to get a green build.

## v0.20 behavior that must remain intact

### Candidate depth and association

- Chinese and English visible candidate pools are up to 32.
- `PinyinImeEngine` short-input search remains expanded: `MAX_RESULTS=32`, `MAX_BEAM_RESULTS=32`, `PREFIX_POOL_LIMIT=64`.
- long input still adaptively narrows Beam/segmentation to avoid latency regression.
- after Chinese commit, `NextAssociationEngine` + `NextPhraseData` generate next-word/next-phrase suggestions from bounded local context and packaged N-grams.
- surrounding context is not persisted.

### Idioms/software vocabulary

- `augment_v020_data.py` remains wired into preBuild.
- it derives four-character phrase/idiom boost data from pinned CC-CEDICT.
- it merges `data/ime_sources/seed_software.tsv`.
- do not replace this with an unreviewed internet idiom dump.

### Context translation

- ordinary/free behavior remains single-sentence local translation.
- optional context translation is Pro-gated through `TranslationSettings` and disabled by default.
- when enabled, only a bounded previous two-sentence cursor context is used in memory through `ContextTranslationEngine`.
- previous context is not persisted and is not automatically inserted into the target editor.
- no cloud/external translation API.

### Sticker compatibility

- direct image path remains `InputContentInfo` / `commitContent` when `image/png` is supported.
- if direct IME image commit fails/unsupported, Orbit copies the generated PNG content URI to the system clipboard and grants temporary read permission to the current target package.
- the user can then try long-press paste in WeChat/QQ/etc.
- if image clipboard cannot be prepared, Emoji fallback remains.
- do not add Accessibility/overlay workarounds around target-app restrictions.

### Existing protections

Keep composing replacement, continuous long Pinyin, DP/adaptive Beam/N-gram/local learning, prefix/fuzzy recovery, 7-page symbols, letter long press, Unicode Emoji, file+journal personal learning, Recent/Pinned clipboard, 16 pets, 24 outfits, 128 stickers, privacy mode and non-exported sticker provider.

## If build fails

Only repair the minimum blocker:

- Kotlin syntax/import/method signature;
- Android API/ContentProvider/InputContentInfo/ClipData signature;
- XML/manifest/resource issue;
- Gradle task ordering;
- Python portability/deterministic parser issue;
- IME metadata.

Do not redesign, remove v0.20 features, bypass validators, lower data gates, revert to small dictionaries, or add runtime network access.

## Forbidden

No INTERNET, cloud prediction/translation, external APIs, ads/analytics/tracking, Accessibility, overlay, external-storage permission, background clipboard/input harvesting, full typed-stream persistence, app/package learning history, AI pet chat, 9-key/Wubi/handwriting, Compose migration, full keyboard Canvas rewrite, Room/Realm, billing or marketplace changes.

## Expected

```text
versionCode = 20
versionName = 0.20.0
APK = app/build/outputs/apk/debug/app-debug.apk
artifact = orbit-ime-v0.20-debug-apk
```

Actions must remain manual `workflow_dispatch`.

## Report

Return:

```text
1. git status / current commit
2. exact build command
3. v0.20 offline data-test result
4. base mature-data result
5. v0.18 licensed augmentation result
6. v0.20 augmentation result, including expanded English / idiom / software counts
7. validation result
8. mature-report counts
9. any minimum repair files and why
10. build result
11. APK path and size
12. permission confirmation
```
