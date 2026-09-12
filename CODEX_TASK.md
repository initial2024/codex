# Codex handoff: build Orbit IME v0.26.0 APK only

All non-build implementation from v0.22 through v0.26 is prepared. Build **current main** only. If compilation exposes a blocker, make only the smallest compile/API/resource/Gradle/Python repair; do not redesign or remove features.

## 1. Sync and confirm version

```text
git fetch origin
git checkout main
git pull origin main
git status
git log -1 --oneline
```

Required:

```text
versionCode = 26
versionName = 0.26.0
```

If local checkout is older, stop and sync before building.

## 2. Build

```text
gradle assembleDebug --no-daemon
```

Windows wrapper is acceptable when present:

```text
.\gradlew.bat assembleDebug --no-daemon
```

Do **not** use `-PorbitSkipMatureImeData=true`.

Expected:

```text
APK = app/build/outputs/apk/debug/app-debug.apk
artifact = orbit-ime-v0.26-debug-apk
Actions = workflow_dispatch only
```

## 3. Mandatory preBuild

Keep the existing chain:

```text
test_ime_data_pipeline_v020.py
-> test_ime_data_pipeline_v023.py
-> test_model_pack_pipeline.py
-> prepare_mature_ime_data.py
-> AOSP + Jieba + ESDB/SCOWL en_US-large
-> augment_v018_data.py
-> CC-CEDICT + Unicode Emoji 17.0
-> augment_v020_data.py
-> software/broad-English/four-character boost
-> augment_v023_data.py
-> THUOCL + FrequencyWords ranking + association shards
-> validate_mature_ime_assets.py
-> Android compilation
```

Never lower mature-data gates or bypass source/hash/license checks just to make the APK build.

## 4. Preserve v0.22 secure model packs

Required files/behavior:

```text
ModelPackManifest.kt
ModelPackManager.kt
ModelRuntimeContracts.kt
CuratedModelCatalog.kt
MODEL_PACKS.md
docs/orbitpack-manifest.example.json
docs/orbitpack-asr-sherpa.example.json
docs/orbitpack-tts-sherpa.example.json
docs/orbitpack-zipvoice.example.json
tools/build_orbitpack.py
tools/test_model_pack_pipeline.py
```

Keep:

- `ACTION_OPEN_DOCUMENT` user-selected local import;
- app-private model storage;
- mandatory `manifest.json`, `LICENSE.txt`, `NOTICE.txt`, `checksums.sha256`;
- SHA-256 coverage of every regular file;
- Zip Slip/absolute path/drive path/duplicate-path rejection;
- packed/unpacked/single-file/file-count limits;
- explicit source/license/commercial/redistribution/resource/disclaimer UI;
- enable/disable/uninstall;
- model-pack `android.permission.INTERNET` rejection.

`model_family` and `runtime_config` are part of manifest v1 in current main.

## 5. Preserve v0.23 mature input quality

Do not regress:

- THUOCL and FrequencyWords licensed data layers/notices;
- runtime Chinese >= configured mature gate (currently 250k+ path);
- English >= 100k;
- 48 displayed Chinese candidates and 48 English candidates;
- 32-shard precomputed association pack;
- continuous Pinyin, DP segmentation, adaptive Beam, 1/2/3-gram;
- exact-before-fuzzy ranking;
- fuzzy Off / Standard / Enhanced;
- bounded typo/transposition/neighbor/missing-key recovery;
- file+journal local learning (20k Free / 100k Pro);
- local context/long-form translation and uncovered-source preservation;
- remembered settings, custom quick phrases, selection-aware editing;
- Follow system / Light / Dark / AMOLED / Custom appearance;
- Recent/Pinned clipboard, Unicode Emoji, kaomoji, symbols/long press;
- pets/outfits/local stickers.

## 6. v0.24 local ASR

Current runtime dependency must remain:

```text
com.github.k2-fsa:sherpa-onnx:1.13.8
```

and `settings.gradle.kts` must retain JitPack for this build-time dependency.

Executable ASR pack family:

```text
type = asr
runtime = sherpa_onnx
model_family = sherpa_offline_transducer
```

Compile and preserve:

```text
SherpaAsrProvider
LocalSpeechInputController
ImeSpeechController
```

Behavior:

- `RECORD_AUDIO` is allowed/required from v0.24 onward;
- user explicitly taps Voice to start and again to stop/recognize;
- capture is 16 kHz mono PCM16, RAM-only, max 60 seconds;
- no background microphone service;
- hiding the IME cancels active capture;
- sensitive/password-like fields hide speech tools/cancel capture;
- recognized text commits to the current editor;
- importing an ASR pack alone never starts recording.

Do not remove ASR just to avoid a sherpa API compile error. If sherpa 1.13.8 exposes a minor Kotlin API mismatch, adapt **only** the local provider call to the actual 1.13.8 API.

## 7. v0.25 local TTS

Executable model families:

```text
sherpa_vits
sherpa_kokoro
sherpa_supertonic
```

Compile/preserve:

```text
SherpaTtsProvider
OrbitAudioPlayer
```

Behavior:

- generation is local;
- playback is local through `AudioTrack`;
- keyboard Read uses selected text first, otherwise only the previous sentence;
- sensitive mode does not read text for TTS;
- no audio/text upload and no automatic audio-history persistence.

## 8. v0.26 ZipVoice authorized voice clone

Executable family:

```text
type = voice_clone
runtime = sherpa_onnx
model_family = sherpa_zipvoice
```

Compile/preserve:

```text
SherpaZipVoiceProvider
VoiceReferenceStore
```

Reference safety requirements:

- Pro only;
- explicit authorization confirmation before file picker;
- only mono PCM16 WAV;
- 2–30 seconds;
- matching reference transcript required;
- reference copied to app-private storage;
- user can delete the reference;
- no silent microphone reference capture;
- disclaimer prohibits impersonation, fraud, harassment and unauthorized commercial voice use.

The settings and IME may preview clone speech only after explicit user action.

## 9. Source/license boundary

Do not silently change or simplify current source notes:

- sherpa-onnx runtime: Apache-2.0; each checkpoint/voice separately audited;
- OPUS-MT zh→en: current model card CC-BY-4.0 candidate;
- OPUS-MT en→zh: current model card Apache-2.0 candidate;
- NLLB-200 distilled 600M: CC-BY-NC-4.0 research/personal candidate, not commercial default;
- Audio8 0.6B: current model card Apache-2.0, Android adapter not implemented;
- Audio8 0.1B: Audio8 Community License v1.0 with revenue conditions, not Apache-2.0;
- ZipVoice/checkpoint/data licenses must be taken from the actual pack source, not inferred from sherpa runtime license.

Generic neural-translation packs remain **non-executable** in v0.26 because Orbit does not yet bundle an audited Marian/OPUS-MT Android decoder. Do not fake neural translation output just to claim feature completion.

## 10. Permissions/privacy

Required/allowed:

```text
android.permission.BIND_INPUT_METHOD
android.permission.RECORD_AUDIO
```

Still prohibited:

```text
android.permission.INTERNET
Accessibility Service
SYSTEM_ALERT_WINDOW / overlay
QUERY_ALL_PACKAGES
POST_NOTIFICATIONS
external storage permissions
contacts/SMS/location/camera
ads/analytics/tracking
cloud sync/cloud translation/cloud prediction
background clipboard/input harvesting
background microphone service
full typed-stream persistence
app/package learning history
```

`OrbitStickerProvider` stays `exported=false` + `grantUriPermissions=true`.

## 11. If build fails

Only repair the minimum blocker, for example:

- Kotlin syntax/import/method signature;
- exact sherpa-onnx 1.13.8 Kotlin API naming/constructor mismatch;
- Android AudioRecord/AudioTrack API compatibility;
- SAF/ZIP/file-I/O issue;
- XML/Manifest/resource/IME metadata;
- Gradle/JitPack dependency resolution;
- deterministic Python portability/parser issue.

Do **not**:

- delete ASR/TTS/ZipVoice to get a green build;
- downgrade to v0.23/v0.22;
- remove model-pack integrity checks;
- lower mature-data gates;
- add INTERNET/cloud APIs;
- add Accessibility/overlay/background microphone;
- replace local model execution with fake/demo text;
- expand product scope into 9-key/Wubi/handwriting/payments/marketplace.

## 12. Build report

Return all of the following:

1. `git status` before build;
2. current commit;
3. exact build command;
4. `test_ime_data_pipeline_v020.py` PASS/FAIL;
5. `test_ime_data_pipeline_v023.py` PASS/FAIL;
6. `test_model_pack_pipeline.py` PASS/FAIL;
7. each mature-data stage PASS/FAIL;
8. `mature-report.json` counts and `validate_mature_ime_assets.py` PASS/FAIL;
9. any minimum repair: file + exact reason + change;
10. Kotlin compile result for at least:
   - `MainActivity`;
   - `OrbitInputMethodService`;
   - `ModelPackManifest`;
   - `ModelPackManager`;
   - `ModelRuntimeContracts`;
   - `SherpaSpeechProviders`;
   - `LocalSpeechInputController`;
   - `ImeSpeechController`;
   - `OrbitAudioPlayer`;
   - `VoiceReferenceStore`;
   - existing Pinyin/English/translation/learning core;
11. `BUILD SUCCESSFUL` / `FAILED`;
12. APK absolute path and size;
13. confirm `versionCode=26`, `versionName=0.26.0`;
14. confirm `RECORD_AUDIO=YES`, `INTERNET=NO`, Accessibility=NO, overlay=NO, external-storage=NO;
15. confirm Actions remains manual `workflow_dispatch` and artifact is `orbit-ime-v0.26-debug-apk`.

Actual ASR/TTS/ZipVoice quality/runtime acceptance requires compatible `.orbitpack` model files on a real Android device; compile success alone must not be reported as real-model inference success.
