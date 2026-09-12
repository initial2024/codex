# Orbit IME Android

Current version: `0.21.0`.

Orbit IME is a privacy-first local Android input method. The authoritative current specifications are:

- `CODEX_TASK.md` — build-only handoff and acceptance gates;
- `DATA_SOURCES.md` — licensed/offline data pipeline;
- `PRIVACY.md` — runtime privacy and permission boundary.

## v0.21 focus

v0.21 keeps the v0.20 mature dictionary/input pipeline and fixes higher-level usability gaps:

- selection-aware editing: selected text is replaced by normal typing/space/candidates/phrases/paste/Emoji/translation; Backspace deletes the whole selection instead of calling `deleteSurroundingText()` around it;
- persistent user settings through local SharedPreferences: last Chinese/English mode, quick-phrase visibility, built-in phrase visibility, next-association toggle, translation context toggle, skins/pets/outfits and Pro state survive reopening;
- user-defined Chinese and English quick phrases through `QuickPhraseStore`, with local add/remove/clear and Free/Pro capacity limits;
- Free translation remains one sentence; Pro adds opt-in previous-context translation plus selected-text long-form translation up to 8,000 characters, processed sentence-by-sentence locally and never uploaded;
- local Pro test activation is available only in debug builds; production licensing is intentionally designed around signed activation tokens with a public key in the APK and the private signing key outside the repository/app;
- pet presentation uses `PetAvatarV21View`: the stable pet silhouettes remain, the old outfit layer is suppressed, accessories are redrawn with a coherent scale/material language, and a lightweight idle float/breath animation is added.

The v0.20 data/input improvements remain mandatory: 32-candidate Chinese/English pools, continuous Pinyin, adaptive Beam + 1/2/3-gram, post-commit local association, AOSP/Jieba/CC-CEDICT Chinese assets, ESDB/SCOWL `en_US-large`, Unicode Emoji 17, software/platform vocabulary, four-character CC-CEDICT boost, CC-CEDICT translation shards, local clipboard, symbols/long-press, pets and local stickers.

## Build

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for a user-test APK.

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions stays manual `workflow_dispatch`.

Artifact:

```text
orbit-ime-v0.21-debug-apk
```
