# Orbit IME Android

Current version: `0.22.0`.

Orbit IME is a privacy-first local Android input method. The authoritative current specifications are:

- `CODEX_TASK.md` — build-only handoff and acceptance gates;
- `DATA_SOURCES.md` — licensed/offline dictionary pipeline;
- `MODEL_PACKS.md` — Pro local neural-model pack format, integrity and license policy;
- `PRIVACY.md` — runtime privacy and permission boundary.

## v0.22 focus

v0.22 keeps every v0.21 input/usability feature and adds the first stage of the optional local neural-model architecture:

- Pro-only `.orbitpack` import through Android's system file picker; no external-storage permission;
- app-private model storage under `files/orbit-model-packs/`;
- mandatory `manifest.json`, `LICENSE.txt`, `NOTICE.txt` and `checksums.sha256`;
- SHA-256 verification for every regular pack file, before install and again while extracting;
- Zip Slip/path traversal protection, duplicate-path rejection, file-count/packed/unpacked size limits and private staging;
- explicit pre-install disclaimer showing upstream source, license, commercial-use claim, redistribution status, languages, model size, RAM requirements and declared future permissions;
- installed-pack list with enable/disable preferred-pack selection and uninstall;
- `TranslationProvider`, `ASRProvider`, `TTSProvider` and `VoiceCloneProvider` contracts prepared for later runtime versions;
- deterministic `tools/build_orbitpack.py` plus offline `tools/test_model_pack_pipeline.py`;
- curated source references in settings. The app itself still has no `INTERNET` permission.

v0.22 deliberately **does not execute neural inference yet**. A user can safely install/inspect/select packs, but translation/ASR/TTS/voice-clone runtimes are added in later milestones rather than pretending an imported checkpoint is already usable.

### Translation-source optimization

The previous NLLB-first idea was revised after checking model licenses:

- `Helsinki-NLP/opus-mt-zh-en`: CC-BY-4.0 — candidate for the commercial-compatible Chinese→English local path, with attribution obligations;
- `Helsinki-NLP/opus-mt-en-zh`: Apache-2.0 — candidate for the English→Chinese path;
- `facebook/nllb-200-distilled-600M`: CC-BY-NC-4.0 — research/personal candidate only, not Orbit's default commercial model.

For speech, `k2-fsa/sherpa-onnx` remains the planned Android ASR runtime candidate. Individual speech/TTS checkpoints and Piper-style voices still need their own model-license audit.

## Preserved mature input features

The v0.21/v0.20 pipeline remains mandatory:

- selection-aware replace/delete;
- remembered settings and custom quick phrases;
- Free single-sentence translation, Pro context/selected long-form local translation;
- 32-candidate Chinese/English pools;
- continuous Pinyin, adaptive Beam, DP segmentation and 1/2/3-gram;
- local next-word/phrase association and fuzzy correction;
- AOSP/Jieba/CC-CEDICT Chinese, ESDB/SCOWL large English, Unicode Emoji 17 and CC-CEDICT translation shards;
- file+journal personal learning;
- Recent/Pinned clipboard;
- symbols/long press, kaomoji, pet visuals and local stickers.

## Build

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for a user-test APK.

Normal preBuild also runs:

```text
tools/test_model_pack_pipeline.py
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions stays manual `workflow_dispatch`.

Artifact:

```text
orbit-ime-v0.22-debug-apk
```
