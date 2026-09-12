# Codex handoff: build Orbit IME v0.22.0 APK only

All non-build implementation is already prepared. Codex must build **current main** only and make only the smallest compile/portability repair exposed by the build.

## Confirm current version first

```text
versionCode = 22
versionName = 0.22.0
```

If local checkout still says v0.21 or older, pull current `main` before doing anything else.

## Build

```text
gradle assembleDebug --no-daemon
```

Do not use:

```text
-PorbitSkipMatureImeData=true
```

## Mandatory preBuild

```text
tools/test_ime_data_pipeline_v020.py
-> tools/test_model_pack_pipeline.py
-> tools/prepare_mature_ime_data.py
-> AOSP + Jieba + pinned ESDB/SCOWL en_US-large
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon/translation + Unicode Emoji 17.0
-> tools/augment_v020_data.py
-> broad normalized ESDB-large + four-character CC-CEDICT boost + project software vocabulary
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

Do not lower any existing mature-data gate.

## v0.22 model-pack gates

Keep these files and behaviors:

```text
ModelPackManifest.kt
ModelPackManager.kt
ModelRuntimeContracts.kt
CuratedModelCatalog.kt
MODEL_PACKS.md
docs/orbitpack-manifest.example.json
tools/build_orbitpack.py
tools/test_model_pack_pipeline.py
```

Required `.orbitpack` policy:

```text
manifest.json
LICENSE.txt
NOTICE.txt
checksums.sha256
model/...
```

ModelPackManager must continue to:

- import only after explicit user selection through Android `ACTION_OPEN_DOCUMENT`;
- store installed packs under app-private `files/orbit-model-packs/<pack_id>/`;
- reject absolute/traversal/duplicate ZIP paths;
- enforce file-count, packed-size, unpacked-size and per-entry limits;
- require non-empty LICENSE and NOTICE;
- require `privacy=offline_only`;
- reject a pack that declares `android.permission.INTERNET`;
- verify SHA-256 for every regular file before install and again while extracting;
- show source/license/commercial-use/redistribution/size/RAM/permission warnings before installation;
- require explicit disclaimer acceptance;
- support installed-pack list, preferred-pack enable/disable selection and uninstall.

Do not replace this with a simple unverified ZIP extractor.

## Important v0.22 runtime boundary

v0.22 **does not execute neural inference yet**.

Keep provider contracts for:

```text
OrbitTranslationProvider
OrbitAsrProvider
OrbitTtsProvider
OrbitVoiceCloneProvider
```

but `OrbitModelRuntimeRegistry` must continue to report the imported packs as non-executable in v0.22.

Do not add fake neural output or claim imported models are already active.

## Model source/license notes

Preserve the curated-source warning logic:

- Helsinki-NLP OPUS-MT zh->en: CC-BY-4.0 candidate; attribution obligations apply;
- Helsinki-NLP OPUS-MT en->zh: Apache-2.0 candidate;
- Meta NLLB-200 distilled 600M: CC-BY-NC-4.0 research/personal candidate, **not** the default commercial Orbit path;
- sherpa-onnx: planned Android speech runtime candidate; each speech model still needs its own license audit;
- Piper-style TTS voices: runtime/model/voice licenses must be checked individually.

Do not silently relabel a non-commercial model as commercial-safe.

## Permissions in v0.22

Do not add:

```text
INTERNET
RECORD_AUDIO
Accessibility
SYSTEM_ALERT_WINDOW
overlay/floating window
external storage
contacts
SMS
location
camera
notifications
ads
analytics
tracking
cloud sync
cloud translation
background clipboard/input harvesting
```

Importing an ASR/TTS/voice pack alone must not request microphone permission or start recording.

The settings page may open a model source URL in the user's external browser via `ACTION_VIEW`; this does not require adding INTERNET permission to Orbit.

## Preserved v0.21/v0.20 features

Do not regress:

- selection-aware replace/delete;
- remembered input/settings state;
- custom/disableable quick phrases;
- Free single-sentence translation;
- Pro previous-context + selected long-form local translation;
- 32 Chinese/English candidates;
- continuous long Pinyin + DP segmentation + adaptive Beam + 1/2/3-gram;
- fuzzy/keyboard typo recovery;
- next-word/phrase association;
- AOSP/Jieba/CC-CEDICT Chinese;
- ESDB/SCOWL large English;
- Unicode Emoji, kaomoji, 7-page symbols and long press;
- Recent/Pinned clipboard;
- 16 pets, 24 outfit catalog entries, 128 local stickers;
- polished pet idle/outfit layer;
- image commit -> image clipboard -> Emoji fallback;
- file+journal personal learning (20k Free / 100k Pro).

## If build fails

Only repair the minimum blocker:

- Kotlin syntax/import/method signature;
- Android API compatibility;
- Storage Access Framework / ZIP / file I/O compatibility;
- XML/Manifest/resource issue;
- Gradle ordering/config;
- Python portability/deterministic test issue;
- IME metadata.

Do not redesign features, lower data gates, delete v0.22 model-pack security checks, add runtime networking, or add neural runtimes just to make the APK build.

## Expected output

```text
APK = app/build/outputs/apk/debug/app-debug.apk
artifact = orbit-ime-v0.22-debug-apk
Actions = workflow_dispatch only
```

## Build report

Return:

```text
1. git status before build
2. git log -1 --oneline
3. exact build command
4. test_ime_data_pipeline_v020.py PASS/FAIL
5. test_model_pack_pipeline.py PASS/FAIL
6. every mature-data stage PASS/FAIL
7. mature-report.json counts
8. validate_mature_ime_assets.py PASS/FAIL
9. any minimum repair files + exact reason
10. Kotlin compile result for:
    OrbitInputMethodService
    MainActivity
    ModelPackManifest
    ModelPackManager
    ModelRuntimeContracts
    CuratedModelCatalog
    ImePreferences
    QuickPhraseStore
    ProLicenseManager
    LongFormTranslationEngine
    PetAvatarV21View
11. BUILD SUCCESSFUL / FAILED
12. APK full path and size
13. versionCode/versionName confirmation
14. confirm no INTERNET / RECORD_AUDIO / Accessibility / overlay / external-storage permission was added
15. confirm Actions remains workflow_dispatch only
```
