# Orbit IME Android

Current version: `0.26.0`.

Orbit IME is a privacy-first local Android input method. Current specifications:

- `CODEX_TASK.md` — build-only handoff and acceptance gates;
- `DATA_SOURCES.md` — licensed/offline dictionary pipeline;
- `MODEL_PACKS.md` — Pro local model-pack format/runtime/license policy;
- `PRIVACY.md` — runtime privacy and permission boundary.

## v0.22–v0.26 milestones

### v0.22 — secure Pro model packs

- `.orbitpack` import through Android Storage Access Framework;
- app-private installation;
- Zip Slip/path/duplicate protection;
- mandatory `manifest.json`, `LICENSE.txt`, `NOTICE.txt`, `checksums.sha256`;
- SHA-256 verification for every regular file;
- explicit source/license/commercial/redistribution/disclaimer UI;
- enable/disable/uninstall without granting the model pack network access.

### v0.23 — mature input quality

- THUOCL domain vocabulary and FrequencyWords ranking layers;
- at least 250k mature Chinese entries and 100k English entries under the build gates;
- 48 visible Chinese/English candidates;
- 32-shard post-commit association assets;
- continuous Pinyin, DP segmentation, adaptive Beam, 1/2/3-gram and bounded fuzzy recovery;
- Off / Standard / Enhanced fuzzy modes;
- improved local context/long-form translation while never fabricating uncovered source segments;
- Follow system / Light / Dark / AMOLED / Custom appearance modes.

### v0.24 — local offline ASR

- pinned `sherpa-onnx 1.13.8` Android runtime;
- executable `sherpa_offline_transducer` model packs;
- explicit tap-to-start / tap-to-stop microphone flow;
- 16 kHz mono PCM16 capture in RAM only, maximum 60 seconds;
- recognized text is committed to the active editor;
- capture is cancelled when the IME hides or enters a sensitive field;
- `RECORD_AUDIO` is requested only for this explicit local voice feature.

### v0.25 — local TTS

- executable sherpa VITS, Kokoro and Supertonic pack families;
- local text-to-speech generation and `AudioTrack` playback;
- keyboard `Read` action reads selected text first, otherwise only the previous sentence;
- no text/audio upload.

### v0.26 — authorized zero-shot voice clone

- executable sherpa ZipVoice model-pack adapter;
- explicit reference-voice consent gate;
- only mono PCM16 WAV, 2–30 seconds, with matching reference transcript;
- reference audio stays in app-private storage and can be deleted;
- keyboard/settings preview uses only user-triggered local generation;
- clear anti-impersonation/fraud/unauthorized-commercial-use disclaimer.

Audio8 0.6B/0.1B and OPUS-MT/NLLB remain curated sources/candidates. They are not falsely marked executable when Orbit lacks a matching audited Android adapter. In particular, generic Marian/OPUS-MT neural translation remains installable/auditable through model packs but is not claimed as working neural inference yet.

## Model/source policy

Speech runtime code and individual model/checkpoint/voice licenses are separate. A sherpa runtime being Apache-2.0 does **not** make every ASR/TTS/voice model automatically commercial-safe. Every `.orbitpack` must ship the exact applicable LICENSE/NOTICE and provenance.

Curated references include:

- k2-fsa sherpa-onnx and sherpa model collections;
- k2-fsa ZipVoice;
- Helsinki-NLP OPUS-MT zh→en / en→zh;
- Meta NLLB-200 distilled 600M as a non-commercial research candidate;
- Audio8 TTS Preview 0.6B and 0.1B with their distinct current licenses.

See `MODEL_PACKS.md` for exact boundaries and example manifests.

## Preserved mature input features

- selection-aware replace/delete and composing replacement;
- remembered settings and user-defined quick phrases;
- 48-candidate Chinese/English pools;
- continuous Pinyin, DP segmentation, adaptive Beam and 1/2/3-gram;
- expanded post-commit associations and three-level fuzzy correction;
- AOSP/Jieba/CC-CEDICT/THUOCL/FrequencyWords/ESDB/Unicode Emoji offline data layers;
- file+journal personal learning (20k Free / 100k Pro);
- Recent/Pinned clipboard;
- Unicode Emoji, kaomoji, 7-page symbols and key long-press;
- 16 pets, 24 outfit entries and 128 local sticker definitions;
- local Free/Pro dictionary/context/long-form translation;
- secure Pro model-pack management;
- no cloud prediction/translation, ads, analytics, Accessibility or overlay.

## Runtime permission boundary

The installed APK still has **no `INTERNET` permission**.

v0.24+ declares `RECORD_AUDIO` only for explicit local ASR. There is no background microphone service. Speech capture is cancelled when the IME hides or a sensitive field is detected.

No external-storage permission is used. Optional models and authorized voice references are copied into app-private storage.

## Build

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for a user-test APK.

Normal preBuild includes:

```text
tools/test_ime_data_pipeline_v020.py
tools/test_ime_data_pipeline_v023.py
tools/test_model_pack_pipeline.py
-> mature dictionary preparation
-> CC-CEDICT / Unicode augmentation
-> software / broad-English augmentation
-> THUOCL / FrequencyWords / association augmentation
-> tools/validate_mature_ime_assets.py
-> Android compile with sherpa-onnx 1.13.8
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions remains manual `workflow_dispatch`.

Artifact:

```text
orbit-ime-v0.26-debug-apk
```
