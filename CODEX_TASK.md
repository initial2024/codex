# Codex handoff task: build Orbit IME v0.8 APK later

## Status

Orbit IME `0.8.0` usability fixes have been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.8.0` after the usability fixes are considered ready.

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
orbit-ime-v0.8-debug-apk
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
2. `versionName` is `0.8.0`.
3. Orbit IME appears in Android input method settings.
4. The settings activity opens.
5. Settings page title is `Orbit IME`, not a versioned title.
6. Settings page uses user-facing sections: 输入法设置, 拼音输入, 剪贴板, 翻译, 宠物, 用户词库, 皮肤, 隐私, 高级功能.
7. Settings page keeps the version only as `About · v0.8.0`.
8. Pinyin-mode Hub labels are Chinese: 粘贴, 剪贴板, 翻译, 宠物, 返回键盘.
9. English-mode Hub labels remain short English labels.
10. Prompt-style actions are not shown in the first-level Hub row.
11. Save is not a first-level Hub button; saving is inside the Clips panel as `保存当前剪贴板`.
12. Keyboard bottom padding is increased to reduce overlap with Android's input-method switcher bubble.
13. Pet chip is hidden while Pinyin composition is active.
14. Pinyin candidates still have priority during composition.
15. The Pinyin dictionary includes generic common terms and shortcuts such as `wgj`, `wj`, `wt`, `xg`, `shurufa`, and `jianqieban`.
16. Static dictionary entries remain generic and not developer-personal.
17. Candidate tap and space-to-select work.
18. Candidate commits learn into the local user dictionary.
19. Exact user dictionary matches rank before generic static dictionary candidates.
20. User dictionary learning stores only `pinyin`, `candidate text`, `frequency`, and `updatedAt`.
21. User dictionary learning is blocked in privacy mode.
22. Translate Preview remains local prompt generation.
23. Offline Translation Pack remains Pro-gated and local-only.
24. Pet panel opens inside the keyboard surface only.
25. Pet supports check-in, random adopt/open egg, hide/show, local template chat, and outfit placeholder.
26. Pet system is hidden and blocked in privacy mode.
27. `123`/`ABC`, backspace, space, enter work.
28. Paste inserts clipboard.
29. Clips panel saves and inserts Orbit-saved entries.
30. Android/host-app long-press text menu is not treated as an Orbit-controlled feature.
31. Password field shows privacy mode, hides Hub actions, clears Pinyin composition, clears Translate Preview, hides Pet, blocks learning/growth, and uses warning/border colors.
32. No network, cloud translation, ad, analytics, Accessibility, overlay, billing, external API, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
