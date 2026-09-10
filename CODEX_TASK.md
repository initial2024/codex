# Codex handoff task: build Orbit IME v0.9 APK later

## Status

Orbit IME `0.9.0` usability corrections have been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.9.0` after the usability corrections are considered ready.

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
orbit-ime-v0.9-debug-apk
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
2. `versionName` is `0.9.0`.
3. Orbit IME appears in Android input method settings.
4. The settings activity opens.
5. Settings page title is `Orbit IME`, not a versioned title.
6. Settings page keeps the version only as `About · v0.9.0`.
7. First-level Hub has a `切换` / `Switch` button that calls Android's input-method picker.
8. Bottom-right key is labeled `回车` / `Enter` and sends Enter; it is not presented as an input-method switcher.
9. Pinyin-mode Hub labels are Chinese: 切换, 粘贴, 剪贴板, 翻译, 返回.
10. English-mode Hub labels remain short English labels: Switch, Paste, Clips, Translate, Keyboard.
11. Prompt-style actions are not shown in the first-level Hub row.
12. Save is not a first-level Hub button; saving is inside the Clips panel as `保存当前剪贴板`.
13. Keyboard bottom padding is increased to reduce overlap with Android's input-method switcher bubble.
14. English-mode idle phrases are English.
15. Pinyin-mode idle phrases are Chinese.
16. Pet is not shown in the first-level keyboard Hub until assets and UX are complete.
17. Pet controls remain available in settings.
18. Pinyin candidates still have priority during composition.
19. The Pinyin dictionary includes generic common terms and shortcuts such as `bing`, `wgj`, `wj`, `wt`, `xg`, `shurufa`, and `jianqieban`.
20. Static dictionary entries remain generic and not developer-personal.
21. Candidate tap and space-to-select work.
22. Candidate commits learn into the local user dictionary.
23. Exact user dictionary matches rank before generic static dictionary candidates.
24. User dictionary learning stores only `pinyin`, `candidate text`, `frequency`, and `updatedAt`.
25. User dictionary learning is blocked in privacy mode.
26. Translate Preview remains local prompt generation.
27. Offline Translation Pack remains Pro-gated and local-only.
28. Android/host-app long-press text menu is not treated as an Orbit-controlled feature.
29. Password field shows privacy mode, hides Hub actions, clears Pinyin composition, clears Translate Preview, hides Pet, blocks learning/growth, and uses warning/border colors.
30. No network, cloud translation, ad, analytics, Accessibility, overlay, billing, external API, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
