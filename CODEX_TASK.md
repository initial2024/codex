# Codex handoff task: build Orbit IME v0.11 APK later

## Status

Orbit IME `0.11.0` Pinyin shortcut and local translation usability fixes have been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.11.0` after the usability corrections are considered ready.

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
orbit-ime-v0.11-debug-apk
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
2. `versionName` is `0.11.0`.
3. `versionCode` is `11`.
4. Settings page keeps the version only as `About · v0.11.0`.
5. GitHub Actions artifact name is `orbit-ime-v0.11-debug-apk`.
6. First-level Hub still has `切换` / `Switch` and it calls Android's input-method picker.
7. Bottom-right key is labeled `回车` / `Enter` and sends Enter.
8. Pinyin-mode Hub labels are Chinese: 切换, 粘贴, 剪贴板, 翻译, 返回.
9. English-mode Hub labels remain short English labels: Switch, Paste, Clips, Translate, Keyboard.
10. Prompt-style actions are not shown in the first-level Hub row.
11. Save is inside the Clips panel as `保存当前剪贴板`.
12. `nh` shows `你好` as a candidate.
13. `nisishei` shows `你是谁` as a candidate.
14. `hsywt` shows `还是有问题` as a candidate.
15. `bing`, `wgj`, `wj`, `wt`, `xg`, `srf`, `jqb`, `shurufa`, and `jianqieban` still show useful candidates.
16. Candidate tap and space-to-select work.
17. User dictionary ranking still works.
18. Translate panel first tries local phrase translation.
19. For known phrases like `你好`, `你是谁`, `还是有问题`, and `I will handle it later`, Translate shows a real local translation that can be inserted.
20. If no local translation exists, Translate falls back to prompt generation rather than pretending to translate.
21. No network, cloud translation, external API, ad, analytics, Accessibility, overlay, billing, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
