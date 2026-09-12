# Orbit IME Android

Orbit IME is a privacy-first Android input method with local Chinese/English prediction, local personalization, clipboard history, offline translation, visual keyboard pets, Emoji/kaomoji, local stickers and skins.

Current version: `0.20.0`.

See `CODEX_TASK.md`, `DATA_SOURCES.md` and `PRIVACY.md` for the current build/data/privacy specification. v0.20 adds optional Pro-gated local context/block translation, wider 32-candidate Chinese/English search, post-commit local N-gram association, CC-CEDICT-derived four-character phrase/idiom boosting, project-authored common software/platform vocabulary, broad normalized ESDB/SCOWL `en_US-large` coverage, and a PNG clipboard compatibility path for target apps such as WeChat/QQ when direct IME image insertion is not exposed.

Normal build:

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for a user-test APK.

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions remains manual `workflow_dispatch`; artifact name is `orbit-ime-v0.20-debug-apk`.
