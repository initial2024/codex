# Orbit IME Android

Current version: `0.20.0`.

Orbit IME is a privacy-first local Android input method. The authoritative current specifications are:

- `CODEX_TASK.md` — build-only handoff and acceptance gates;
- `DATA_SOURCES.md` — licensed/offline data pipeline;
- `PRIVACY.md` — runtime privacy and permission boundary.

v0.20 adds optional Pro-gated local context/block translation, 32-candidate Chinese/English search, post-commit local N-gram association, CC-CEDICT-derived four-character phrase/idiom boosting, project-authored common software/platform vocabulary, a broad normalized pass over pinned ESDB/SCOWL `en_US-large`, and a PNG clipboard compatibility path when target apps such as WeChat/QQ do not expose direct IME image insertion.

Build:

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for a user-test APK.

Expected APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions stays manual `workflow_dispatch`; artifact: `orbit-ime-v0.20-debug-apk`.
