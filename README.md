# Orbit IME Android

Current version: `0.23.0`.

Orbit IME is a privacy-first local Android input method. Current specifications:

- `CODEX_TASK.md` — build-only handoff and acceptance gates;
- `DATA_SOURCES.md` — licensed/offline dictionary pipeline;
- `MODEL_PACKS.md` — Pro local model-pack format/integrity/license policy;
- `PRIVACY.md` — runtime privacy and permission boundary.

## v0.23 focus — input quality and flow

v0.23 keeps the v0.22 secure `.orbitpack` manager and concentrates on everyday typing quality:

- adds pinned THUOCL domain vocabulary (IT, idioms, finance, places, food, law, people, medical, poetry, animals, cars) with conservative Pinyin derivation;
- adds FrequencyWords/OpenSubtitles Chinese and English usage-frequency overlays under CC-BY-SA-4.0 so common conversational words rank above obscure dictionary words;
- generates a 32-shard `association/` asset at build time. Post-commit prediction first queries this compact 1–4-character context index and only then uses bounded N-gram Beam continuation;
- keeps 32 visible Chinese/English candidates while expanding prefix/fuzzy candidate sources without making long-sentence Beam unbounded;
- adds persistent fuzzy correction levels: Off / Standard / Enhanced. Enhanced adds missing-key insertion, repeated-key collapse and bounded second-layer fuzzy variants; English uses bounded transpose/extra-key/neighbor/missing-key recovery too;
- upgrades local translation with coverage-scored dynamic-programming phrase selection before the older greedy fallback;
- Pro long-form translation preserves paragraph/line breaks, processes up to 8,000 characters locally and retains uncovered source text rather than fabricating a result;
- adds appearance policy independent from skins: Follow system / Light / Dark / AMOLED black / Custom skin. The default is Follow system.

## Licensed v0.23 data layers

Existing audited layers remain:

- AOSP PinyinIME — Apache-2.0;
- Jieba — MIT;
- CC-CEDICT — CC-BY-SA-4.0;
- ESDB/SCOWL `en_US-large` — ESDB redistribution terms;
- Unicode Emoji 17.0 — Unicode Data Files and Software License;
- Orbit project-authored software/platform vocabulary and seed data.

v0.23 additionally uses:

- THUOCL — MIT; source README explicitly permits research and commercial use and provides DF frequency data;
- HermitDave/FrequencyWords content — CC-BY-SA-4.0; used as a usage-frequency/ranking layer, not as an unfiltered source of arbitrary new spellings.

All upstream files are pinned/hash-verified on the build machine and are packaged as offline assets with notices. The installed IME still has no `INTERNET` permission.

## Translation and local neural model status

Free translation remains local single-sentence translation. Pro retains optional previous-context and selected long-form local translation. v0.23 improves the dictionary/rule fallback substantially, but it does **not** claim that a neural model is executable when the Android native runtime is not bundled.

The v0.22 `.orbitpack` manager remains available for verified local model installation. `ModelRuntimeContracts.kt` continues to report unbundled neural providers as non-executable. A future runtime adapter can activate compatible Bergamot/Marian or other audited local packs without weakening v0.23's offline fallback.

## Preserved features

- selection-aware replace/delete;
- composing replacement (no raw-Pinyin append bug);
- remembered settings and user-defined quick phrases;
- 32-candidate Chinese/English pools;
- continuous Pinyin, DP segmentation, adaptive Beam and 1/2/3-gram;
- file+journal personal learning (20k Free / 100k Pro);
- Recent/Pinned clipboard;
- Unicode Emoji, kaomoji, 7-page symbols and letter long-press;
- 16 pets, 24 outfit entries and 128 local sticker definitions;
- secure Pro `.orbitpack` import/validation/uninstall;
- no cloud translation/prediction, advertising, analytics, Accessibility or overlay.

## Build

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for a user-test APK.

Normal preBuild runs the established mature pipeline plus:

```text
tools/test_ime_data_pipeline_v023.py
-> tools/augment_v023_data.py
-> tools/validate_mature_ime_assets.py
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions stays manual `workflow_dispatch`.

Artifact:

```text
orbit-ime-v0.23-debug-apk
```
