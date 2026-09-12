# Codex handoff: build Orbit IME v0.20.0 APK only

All non-build work is already prepared. Build **current main** only.

Confirm first:

```text
versionCode = 20
versionName = 0.20.0
```

Run:

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true`.

Mandatory preBuild:

```text
tools/test_ime_data_pipeline_v020.py
-> tools/prepare_mature_ime_data.py
-> AOSP + Jieba + pinned ESDB/SCOWL en_US-large
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon/translation + Unicode Emoji 17.0
-> tools/augment_v020_data.py
-> broad normalized ESDB-large pass + CC-CEDICT four-character boost + project software vocabulary
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

Required gates:

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

Do not lower gates to force PASS. If the old `English=81,373` result appears, verify the v0.20 broad SCOWL normalization stage actually ran; do not accept the stale result.

Keep these v0.20 behaviors:

- composing candidate replacement, no raw-Pinyin append bug;
- Chinese/English candidate pool up to 32;
- Pinyin internal `MAX_RESULTS=32`, `MAX_BEAM_RESULTS=32`, `PREFIX_POOL_LIMIT=64`, with adaptive narrowing for long input;
- post-commit `NextAssociationEngine + NextPhraseData` local next-word/phrase association;
- four-character CC-CEDICT boost + `seed_software.tsv`;
- ordinary/free translation remains single-sentence;
- optional context/block translation is Pro-gated, disabled by default, bounded to previous two sentences in memory only;
- direct sticker `InputContentInfo` plus PNG clipboard URI fallback with temporary target-package read grant, then Emoji fallback;
- file+journal personal learning (20k free / 100k Pro placeholder);
- 16 pets, 24 outfits, 128 local stickers;
- Recent/Pinned clipboard, Unicode Emoji, kaomoji, 7-page symbols and letter long press;
- no INTERNET / Accessibility / overlay / ads / analytics / external storage / background input harvesting.

If build fails, only repair the smallest Kotlin/API/resource/manifest/Gradle/Python portability or deterministic parser blocker. Do not redesign or delete features.

Expected:

```text
APK = app/build/outputs/apk/debug/app-debug.apk
artifact = orbit-ime-v0.20-debug-apk
```

Actions must remain manual `workflow_dispatch`.

Report the exact build command, every data-stage result, `mature-report.json` counts (including expanded English / idiom / software), validation result, minimum repair files, build result, APK path/size and permission confirmation.
