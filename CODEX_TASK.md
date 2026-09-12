# Codex handoff: build Orbit IME v0.21.0 APK only

All non-build implementation is already prepared. Codex must build **current main** only and make only the smallest compile/portability repair exposed by the build.

## Confirm current version first

```text
versionCode = 21
versionName = 0.21.0
```

If local checkout still says v0.20 or older, pull current `main` before doing anything else.

## Build

```text
gradle assembleDebug --no-daemon
```

Do not use:

```text
-PorbitSkipMatureImeData=true
```

Mandatory preBuild remains:

```text
tools/test_ime_data_pipeline_v020.py
-> tools/prepare_mature_ime_data.py
-> AOSP + Jieba + pinned ESDB/SCOWL en_US-large
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon/translation + Unicode Emoji 17.0
-> tools/augment_v020_data.py
-> broad normalized ESDB-large + four-character CC-CEDICT boost + project software vocabulary
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

## Data gates — do not lower

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

## v0.21 behavior gates

### 1. Selected-text replace/delete

Keep Android selection semantics:

- if editor selection is non-empty, Backspace must replace the whole selection with empty text;
- only when there is no selection may Backspace delete the previous Unicode code point;
- typing a letter, space, candidate, paste, Emoji, quick phrase or translation must replace the selected region instead of appending after it;
- do not reintroduce the old `deleteSurroundingText()`-only behavior.

Relevant service helpers include:

```text
hasSelectedText()
resetInternalCompositionState()
deleteSurroundingTextInCodePoints(...)
commitText("", 1)
```

### 2. Remembered preferences

Keep:

```text
ImePreferences.kt
QuickPhraseStore.kt
```

Persist locally:

- last Chinese/English mode;
- quick phrase on/off;
- built-in phrase show/hide;
- post-commit association on/off;
- context translation setting;
- existing skin/pet/outfit state.

Quick phrases must support local custom Chinese and English items. Do not revert to a fixed-only phrase bar.

### 3. Pro activation

Keep:

```text
ProLicenseManager.kt
```

Debug builds may accept the hashed tester-code path so Pro features can be tested. Release builds must not accept that tester code.

Do not replace this with a reusable plain-text production invite code. Production direction remains signed license tokens: APK/public key only; private signing key outside repository/APK.

### 4. Translation tiers

Free:

```text
single-sentence local translation
```

Pro:

```text
optional previous-two-sentence context translation
selected-text long-form translation up to 8,000 characters
```

Long-form translation is explicitly user-triggered after selecting/all-selecting text. It remains local, sentence-by-sentence, and preserves uncovered source segments instead of fabricating translation.

Keep:

```text
LongFormTranslationEngine.kt
PrivacyGuard.isSafeForLocalLongForm(...)
```

No cloud translator/API/INTERNET permission.

### 5. Pet visual polish

Keep:

```text
PetAvatarV21View.kt
PolishedPetOutfits
```

The view intentionally calls the stable pet renderer with:

```text
equippedOutfitId = null
```

and then draws the v0.21 coherent accessory overlay. Do not restore the old stacked outfit rendering in the v0.21 keyboard/settings preview.

Keep the lightweight idle float/breath animation. Do not add overlay permission or a floating desktop window.

### 6. Existing mature input features

Do not regress:

- composing replacement/no raw-Pinyin append bug;
- Chinese/English candidate pool up to 32;
- adaptive long Pinyin Beam + DP segmentation + 1/2/3-gram;
- fuzzy/keyboard typo recovery;
- post-commit next-word/phrase association;
- CC-CEDICT four-character boost;
- software/platform vocabulary;
- ESDB/SCOWL large English path;
- Unicode Emoji, kaomoji, 7-page symbols, long press;
- Recent/Pinned clipboard;
- 16 pets, 24 outfit catalog entries, 128 local sticker definitions;
- image commit -> image clipboard -> Emoji fallback;
- file+journal personal learning (20k Free / 100k Pro).

## Privacy/permission hard boundary

Do not add:

```text
INTERNET
Accessibility
SYSTEM_ALERT_WINDOW
overlay/floating window
external storage
ads
analytics
tracking
cloud sync
cloud translation
background clipboard/input harvesting
full typed-stream persistence
app/package learning history
```

`OrbitStickerProvider` must stay non-exported with temporary URI grants only.

## If build fails

Only fix the minimum blocker:

- Kotlin syntax/import/method signature;
- Android API compatibility;
- ContentProvider/InputContentInfo issue;
- XML/Manifest/resource issue;
- Gradle ordering/config;
- Python portability/deterministic parser issue;
- IME metadata.

Do not redesign features, lower data gates, delete v0.21 behavior, or add network capabilities.

## Expected output

```text
APK = app/build/outputs/apk/debug/app-debug.apk
artifact = orbit-ime-v0.21-debug-apk
Actions = workflow_dispatch only
```

## Build report

Return:

```text
1. git status before build
2. git log -1 --oneline
3. exact build command
4. every data-stage PASS/FAIL
5. mature-report.json counts
6. validate_mature_ime_assets.py PASS/FAIL
7. any minimum repair files + exact reason
8. Kotlin compile result for:
   OrbitInputMethodService
   ImePreferences
   QuickPhraseStore
   ProLicenseManager
   LongFormTranslationEngine
   PetAvatarV21View
9. BUILD SUCCESSFUL / FAILED
10. APK full path and size
11. versionCode/versionName confirmation
12. confirmation prohibited permissions were not added
```
