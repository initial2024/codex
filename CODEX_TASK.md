# Codex handoff task: build Orbit IME v0.12 APK later

## Status

Orbit IME `0.12.0` Pinyin and local translation data expansion has been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.12.0` after the data-layer expansion is considered ready.

## Repository

```text
https://github.com/initial2024/codex
```

## Required environment

- JDK 17
- Android SDK platform 35
- Android build-tools 35.0.0
- Gradle 8.10.2 or compatible

## Build command

```bash
gradle assembleDebug --no-daemon
```

## Expected APK

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions artifact

```text
orbit-ime-v0.12-debug-apk
```

## Build trigger policy

The GitHub Actions workflow is manual-only via `workflow_dispatch`. Do not rely on push-triggered builds.

## Non-negotiable constraints

Do not add:

- INTERNET permission
- Cloud translation
- External translation API
- Ad SDK
- Analytics SDK
- Accessibility permission
- Overlay / floating-window permission
- Background service
- Notification spam
- Sound effects
- Clipboard background harvesting
- Password-field saving
- Full typed-key-stream persistence
- Translate Preview history persistence
- User dictionary cloud sync
- User dictionary upload
- Pet data upload or sync
- AI pet chat
- Paid gacha
- Pinyin 9-key
- Wubi
- Handwriting recognition
- Canvas keyboard rewrite
- Compose migration
- Room or Realm migration
- Billing implementation
- Skin marketplace

## Functional acceptance criteria

1. APK builds successfully.
2. `versionName` is `0.12.0`.
3. `versionCode` is `12`.
4. Settings page keeps the version only as `About · v0.12.0`.
5. GitHub Actions artifact name is `orbit-ime-v0.12-debug-apk`.
6. `PinyinBoostData.kt` exists and contains expanded full-pinyin, shorthand, and sentence candidates.
7. `PinyinDictionary.kt` merges `PinyinBoostData.entries` with the core syllable dictionary.
8. Exact boost candidates rank before core single-character candidates.
9. Candidate limit is at least 10.
10. `nh` shows `你好` as a candidate.
11. `nisishei` shows `你是谁` as a candidate.
12. `hsywt` shows `还是有问题` as a candidate.
13. `zsm` shows `这是什么` as a candidate.
14. `zmb` shows `怎么办` as a candidate.
15. `smqk` shows `什么情况` as a candidate.
16. `bing`, `wgj`, `wj`, `wt`, `xg`, `srf`, `jqb`, `shurufa`, and `jianqieban` still show useful candidates.
17. Candidate tap and space-to-select work.
18. User dictionary ranking still works.
19. `TranslationBoostData.kt` exists and contains expanded local phrase translation and token maps.
20. `OfflineTranslationPack.kt` checks `TranslationBoostData` before rough token assembly and prompt fallback.
21. Translate panel first tries local phrase translation.
22. Known phrases like `你好`, `你是谁`, `这是什么`, `怎么办`, `还是有问题`, `请给出可执行步骤`, `不要添加 INTERNET 权限`, and `I will handle it later` show directly insertable local translations.
23. If no local translation exists, Translate falls back to prompt generation rather than pretending to translate.
24. `DATA_SOURCES.md` documents source strategy and licensing rules.
25. No network, cloud translation, external API, ad, analytics, Accessibility, overlay, billing, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
