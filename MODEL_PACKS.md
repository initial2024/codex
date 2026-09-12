# Orbit IME Model Packs (v0.22)

Orbit IME keeps the installed IME runtime offline by default. Pro users can import local `.orbitpack` files through Android's system file picker. v0.22 implements secure pack inspection, installation, enable/disable selection and removal; it **does not yet execute neural inference**.

## Why model packs are separate from the APK

Large neural translation/ASR/TTS checkpoints can be hundreds of MB or several GB. Bundling them in the base APK would make installation, updates, storage use and cold start worse for everyone. Orbit therefore keeps the core keyboard small and stores optional model packs under app-private storage.

No external-storage permission is required. The user explicitly chooses a file through Android Storage Access Framework and Orbit copies the validated files into its private directory.

## Required `.orbitpack` layout

```text
example.orbitpack
├── manifest.json
├── LICENSE.txt
├── NOTICE.txt
├── checksums.sha256
└── model/
    ├── model.onnx / model.gguf / other runtime files
    ├── tokenizer / vocab / config files
    └── ...
```

Every regular file except `checksums.sha256` must have a SHA-256 entry. Orbit rejects absolute paths, `..` traversal, duplicate paths, oversized metadata, too many files and packs that exceed safety limits.

## Manifest v1

See `docs/orbitpack-manifest.example.json`.

Important fields:

- `pack_format`: currently `1`;
- `pack_id`: stable lowercase identifier;
- `type`: `translation`, `asr`, `tts`, or `voice_clone`;
- `runtime`: `onnx`, `gguf`, or `sherpa_onnx`;
- `source_url`: upstream HTTPS source;
- `license`: exact upstream/model license identifier;
- `commercial_use`: model-pack author's audited claim, not a legal guarantee;
- `redistribution`: `allowed`, `conditional`, `prohibited`, or `unknown`;
- `languages`: supported language codes;
- `requires_permissions`: future runtime permissions. `INTERNET` is rejected;
- `privacy`: must be `offline_only` in v0.22;
- `disclaimer_required`: must be `true`.

`LICENSE.txt` and `NOTICE.txt` remain mandatory even if the same license name appears in JSON.

## Build a pack

```text
python tools/build_orbitpack.py \
  --manifest docs/my-manifest.json \
  --payload-dir path/to/model-files \
  --license path/to/LICENSE.txt \
  --notice path/to/NOTICE.txt \
  --output my-model.orbitpack
```

The builder is deterministic in file order/timestamps, rejects symlinks, computes SHA-256 for metadata and model payload files, and does not download anything.

## User-side import flow

```text
Settings -> Orbit Pro -> Local model packs -> Import .orbitpack
```

Orbit then performs:

1. copy selected file into app-private cache;
2. Zip Slip/path validation;
3. parse/validate manifest;
4. require non-empty LICENSE and NOTICE;
5. SHA-256 verification for every regular file;
6. show model source, license, commercial/redistribution claims, size/RAM/permissions and warnings;
7. require explicit user acceptance;
8. extract into app-private `files/orbit-model-packs/<pack_id>/`;
9. enable/disable selection or uninstall independently.

## Disclaimer shown before installation

Users must understand:

- the third-party model may be inaccurate, biased, unsafe or unsuitable for high-stakes use;
- translation/ASR/TTS output can be wrong;
- model size, memory use, heat, latency and battery drain vary by device;
- some third-party licenses prohibit commercial use or redistribution;
- Orbit validates package integrity and declared metadata but cannot provide legal advice or guarantee that a pack author described a third-party license correctly;
- voice cloning requires authorization from the voice owner and must not be used for impersonation, fraud, harassment or rights infringement;
- v0.22 does not execute neural inference yet.

## Curated source notes

Orbit's settings show source references rather than silently downloading them.

### Neural translation

- `Helsinki-NLP/opus-mt-zh-en` — model card currently lists **CC-BY-4.0**. Attribution/license obligations apply.
- `Helsinki-NLP/opus-mt-en-zh` — model card currently lists **Apache-2.0**.
- `facebook/nllb-200-distilled-600M` — model card currently lists **CC-BY-NC-4.0**. It is therefore treated as a research/personal candidate and is **not** the default commercial Orbit path.

v0.23 should prioritize a commercially compatible OPUS-MT path before considering NLLB for research mode.

### Speech runtime

- `k2-fsa/sherpa-onnx` is the planned Android ASR runtime candidate; its runtime repository is Apache-2.0, while each downloaded ASR/TTS model still needs its own license check.
- Piper-style ONNX TTS is a candidate for local speech synthesis. Voice/model licenses differ and must be checked individually; Orbit must not treat every Piper voice as automatically commercial-safe.

## Permission policy

Model-pack import itself adds no `INTERNET`, external-storage, Accessibility, overlay or microphone permission.

Future ASR will require `RECORD_AUDIO`; that permission must be introduced only when the ASR runtime is implemented, shown to the user at feature enable time, and used only while explicit voice input is active. Importing a speech pack alone must never start recording.

## Pro boundary

Model-pack import/management is Pro-only. Debug builds may use the existing tester Pro activation path. Production licensing remains the signed-license-token design; do not use one reusable plaintext production invite code.
