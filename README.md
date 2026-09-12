# Orbit IME Android

Current version: `0.23.0`.

Orbit IME is a privacy-first local Android input method. Current specifications:

- `CODEX_TASK.md` — build-only handoff and acceptance gates;
- `DATA_SOURCES.md` — licensed/offline dictionary pipeline;
- `MODEL_PACKS.md` — Pro local model-pack format/integrity/license policy;
- `PRIVACY.md` — runtime privacy and permission boundary.

## v0.23 focus — input quality and flow

v0.23 keeps the v0.22 secure `.orbitpack` manager and concentrates on everyday typing quality:

- pinned THUOCL domain vocabulary covers IT, idioms, finance, places, food, law, people, medical, poetry, animals and cars;
- FrequencyWords/OpenSubtitles Chinese and English usage-frequency overlays improve ranking of common conversational vocabulary;
- runtime Chinese mature-data gate is at least 250,000 entries; English remains at least 100,000 entries;
- a 32-shard precomputed `association/` pack provides fast post-commit next-word/phrase prediction before bounded N-gram continuation;
- Chinese and English visible candidate pools are expanded to 48, while internal prefix/fuzzy pools are larger and long-query Beam remains adaptive/bounded;
- persistent fuzzy correction modes are Off / Standard / Enhanced. Pinyin includes fuzzy initials/finals, adjacent transposition, neighbor substitution, extra-key deletion, repeated-key collapse, missing-key recovery and a bounded second fuzzy layer; English has equivalent bounded typo recovery;
- exact candidates remain ahead of fuzzy results; user-learning and frequency/N-gram ranking are still applied;
- local translation uses exact data first, then coverage-scored dynamic-programming phrase selection, CC-CEDICT composition and conservative fallback;
- long-form translation preserves line/paragraph structure, splits oversized sentences into bounded clauses, processes up to 8,000 source characters, and never drops uncovered source text;
- context translation now preserves sentence boundaries and can reference up to four previous sentences in memory for Pro;
- translated output receives local punctuation/spacing cleanup instead of returning raw dictionary fragments;
- appearance is independent from skins: Follow system / Light / Dark / AMOLED black / Custom skin. Default is Follow system and Android night mode selects Orbit Dark.

The runtime strategy intentionally follows mature IME principles: indexed exact/prefix lookup first, cached bounded fuzzy recovery, full-sentence decoding with adaptive Beam, and cheap indexed next-word prediction before more expensive fallback generation.

## Licensed v0.23 data layers

Existing audited layers:

- AOSP PinyinIME — Apache-2.0;
- Jieba — MIT;
- CC-CEDICT — CC-BY-SA-4.0;
- ESDB/SCOWL `en_US-large` — ESDB redistribution terms;
- Unicode Emoji 17.0 — Unicode Data Files and Software License;
- Orbit project-authored software/platform vocabulary and seed data.

v0.23 additionally uses:

- THUOCL — MIT;
- HermitDave/FrequencyWords content — CC-BY-SA-4.0, used as a usage-frequency/ranking layer.

All upstream files are pinned/hash-verified on the build machine and packaged as offline assets with notices. The installed IME still has no `INTERNET` permission.

## Translation and local neural model status

Free translation remains local single-sentence translation. Pro retains optional context and selected long-form local translation. v0.23 substantially improves the dictionary/rule path but does **not** claim neural output when a native neural runtime is not bundled.

The v0.22 `.orbitpack` manager remains available for verified local model installation. `ModelRuntimeContracts.kt` continues to report unbundled neural providers as non-executable. A later runtime adapter can activate an audited local neural translation pack without weakening the current offline fallback.

## Preserved features

- selection-aware replace/delete and composing replacement;
- remembered settings and user-defined quick phrases;
- 48-candidate Chinese/English pools;
- continuous Pinyin, DP segmentation, adaptive Beam and 1/2/3-gram;
- expanded post-commit association pool up to 48 suggestions;
- file+journal personal learning (20k Free / 100k Pro);
- Recent/Pinned clipboard;
- Unicode Emoji, kaomoji, 7-page symbols and letter long-press;
- 16 pets, 24 outfit entries and 128 local sticker definitions;
- secure Pro `.orbitpack` import/validation/uninstall;
- Follow system / Light / Dark / AMOLED / Custom appearance modes;
- no cloud translation/prediction, advertising, analytics, Accessibility or overlay.

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
-> v0.18 licensed CC-CEDICT / Unicode augmentation
-> v0.20 software / broad-English augmentation
-> tools/augment_v023_data.py
-> tools/validate_mature_ime_assets.py
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions remains manual `workflow_dispatch`.

Artifact:

```text
orbit-ime-v0.23-debug-apk
```
