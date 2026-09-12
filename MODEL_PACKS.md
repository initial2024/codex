# Orbit IME Model Packs (v0.26)

Orbit keeps the installed IME offline. Pro users import local `.orbitpack` files through Android's system file picker. The pack manager performs source/license display, checksum verification, archive-safety checks, disclaimer confirmation, private installation, preferred-pack selection and uninstall.

The speech milestones are now separated clearly:

- **v0.22** — secure `.orbitpack` manager;
- **v0.24** — executable local sherpa-onnx offline ASR;
- **v0.25** — executable local sherpa-onnx TTS;
- **v0.26** — executable sherpa ZipVoice zero-shot voice clone with explicit reference-voice authorization.

Generic neural-translation packs can still be installed/audited, but Orbit does **not** pretend to have a complete Marian/OPUS-MT Android decoder. Dictionary/rule translation remains the executable translation path until an audited neural translator adapter is implemented.

## Why model packs are separate from the APK

ASR/TTS/translation/voice-clone checkpoints can be hundreds of MB or multiple GB. The base APK therefore contains runtime adapters only; optional weights remain under app-private storage. The user explicitly selects a local file through Android Storage Access Framework. No external-storage permission is required.

## Required `.orbitpack` layout

```text
example.orbitpack
├── manifest.json
├── LICENSE.txt
├── NOTICE.txt
├── checksums.sha256
└── model/
    ├── *.onnx / *.gguf / tokenizer / vocab / config
    └── optional model-specific data directories
```

Every regular file except `checksums.sha256` must have a SHA-256 entry. Orbit rejects absolute paths, drive/scheme paths, `..` traversal, duplicate paths, unsafe metadata and archive-size/file-count violations. Speech packs may contain many small phonemizer/data files, so v0.26 keeps a hard but larger file-count cap rather than disabling the safety gate.

## Manifest v1

Common fields:

- `pack_format`: currently `1`;
- `pack_id`: stable lowercase identifier;
- `type`: `translation`, `asr`, `tts`, `voice_clone`;
- `runtime`: `onnx`, `gguf`, `sherpa_onnx`;
- `model_family`: runtime adapter family;
- `runtime_config`: relative model paths and small runtime options;
- `source_url`: exact upstream HTTPS source;
- `license`: exact model/checkpoint/voice license label;
- `commercial_use`: pack author's audited claim, not a legal guarantee;
- `redistribution`: `allowed`, `conditional`, `prohibited`, or `unknown`;
- `languages`: supported language codes;
- `requires_permissions`: runtime permissions. `INTERNET` is always rejected;
- `privacy`: must be `offline_only`;
- `disclaimer_required`: must be `true`.

`LICENSE.txt` and `NOTICE.txt` are mandatory even when JSON contains a license label.

Examples:

```text
docs/orbitpack-manifest.example.json
docs/orbitpack-asr-sherpa.example.json
docs/orbitpack-tts-sherpa.example.json
docs/orbitpack-zipvoice.example.json
```

The speech examples intentionally use `VERIFY-*` license placeholders where an exact checkpoint/voice license cannot be inferred from the runtime repository. Replace those placeholders and audit the exact source before packaging.

## Supported executable families

### v0.24 ASR

```text
type = asr
runtime = sherpa_onnx
model_family = sherpa_offline_transducer
```

`runtime_config` keys:

```text
encoder
decoder
joiner
tokens
num_threads
decoding_method
max_active_paths
```

Orbit captures 16 kHz mono PCM16 audio only after an explicit user action. Capture stays in RAM, is capped at 60 seconds, is cancelled when the IME hides or enters a sensitive field, and is never saved as an ASR history file.

`RECORD_AUDIO` is the only new runtime permission for this feature. Importing an ASR pack alone does not start recording.

### v0.25 TTS

Executable sherpa families:

```text
sherpa_vits
sherpa_kokoro
sherpa_supertonic
```

The pack should point `runtime_config` at its own model/tokens/voices/data files. Generated PCM is played locally through `AudioTrack`; Orbit does not upload text or audio.

### v0.26 zero-shot voice clone

```text
type = voice_clone
runtime = sherpa_onnx
model_family = sherpa_zipvoice
```

Runtime keys:

```text
encoder
decoder
vocoder
tokens
lexicon
data_dir
num_threads
num_steps
speed
```

The user must explicitly save a **2–30 second mono PCM16 WAV** plus the exact transcript. Orbit requires a confirmation that the reference is the user's own voice or an explicitly authorized voice. The reference is copied into app-private storage and can be deleted from settings.

Do not use voice cloning to impersonate people, commit fraud, harass, or make unauthorized commercial voice content. Synthetic speech can be inaccurate or misleading.

## Build a pack

```text
python tools/build_orbitpack.py \
  --manifest docs/my-manifest.json \
  --payload-dir path/to/model-files \
  --license path/to/LICENSE.txt \
  --notice path/to/NOTICE.txt \
  --output my-model.orbitpack
```

The builder is deterministic in file order/timestamps, rejects symlinks, computes SHA-256 for metadata/model files, and downloads nothing.

## Import flow

```text
Settings -> Orbit Pro -> Local model packs -> Import .orbitpack
```

Orbit performs:

1. copy into app-private staging cache;
2. path/Zip Slip/duplicate-path checks;
3. manifest validation;
4. mandatory LICENSE and NOTICE;
5. SHA-256 coverage/verification for every regular file;
6. size/file-count guards;
7. display source, license, commercial/redistribution claims, size/RAM/permissions and runtime status;
8. require explicit disclaimer acceptance;
9. extract into `files/orbit-model-packs/<pack_id>/`;
10. let the user select/disable/uninstall the pack.

## Source and license notes

Orbit shows curated **source references**, not automatic downloads. A runtime license and a model/voice license are separate facts.

- `k2-fsa/sherpa-onnx` — Apache-2.0 runtime; individual checkpoints/voices must be audited separately.
- `k2-fsa/sherpa-onnx-models` — useful ASR source collection; per-model license/source must be checked.
- `k2-fsa/ZipVoice` — v0.26 runtime adapter exists, but the actual checkpoint/data license must be included in the pack rather than guessed by Orbit.
- `Helsinki-NLP/opus-mt-zh-en` — current model card: **CC-BY-4.0**.
- `Helsinki-NLP/opus-mt-en-zh` — current model card: **Apache-2.0**.
- `facebook/nllb-200-distilled-600M` — **CC-BY-NC-4.0**, research/personal candidate, not default commercial path.
- `Audio8/Audio8-TTS-Preview-0.6b` — current model card states **Apache-2.0**, 11 languages and zero-shot voice cloning; its Transformers custom code is not adapted to Android in v0.26.
- `Audio8/Audio8-TTS-Preview-0.1b` — **Audio8 Community License v1.0**, including a revenue threshold; it must not be mislabeled as Apache-2.0.

Always re-check the exact upstream revision before redistribution or commercial release.

## Permission policy

Orbit still rejects any model pack declaring `android.permission.INTERNET`. The installed APK has no INTERNET permission.

v0.24+ declares `RECORD_AUDIO` solely for explicit local ASR. No background microphone service exists. Sensitive input mode hides speech actions and cancels an active capture; hiding the IME also cancels capture.

TTS and voice-clone playback need no network permission. Voice-reference files and model weights stay in app-private storage.

## Pro boundary

Model-pack import/management and local ASR/TTS/voice clone are Pro features. Debug builds may use the existing tester activation path. Production licensing remains signed-license-token verification with the private signing key outside the APK/repository.
