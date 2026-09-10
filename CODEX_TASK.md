# Codex handoff task: build Orbit IME v0.10 APK later

## Status

Orbit IME `0.10.0` bilingual phrase-library expansion has been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.10.0` after the phrase-library expansion is considered ready.

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
orbit-ime-v0.10-debug-apk
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
2. `versionName` is `0.10.0`.
3. Settings page keeps the version only as `About · v0.10.0`.
4. First-level Hub still has `切换` / `Switch` and it calls Android's input-method picker.
5. Bottom-right key is labeled `回车` / `Enter` and sends Enter.
6. Pinyin-mode Hub labels are Chinese: 切换, 粘贴, 剪贴板, 翻译, 返回.
7. English-mode Hub labels remain short English labels: Switch, Paste, Clips, Translate, Keyboard.
8. Prompt-style actions are not shown in the first-level Hub row.
9. Save is inside the Clips panel as `保存当前剪贴板`.
10. `TemplateLibrary.kt` contains expanded `chineseQuickPhrases` and `englishQuickPhrases` lists.
11. `OrbitInputMethodService.buildPhraseBar()` reads `TemplateLibrary.quickPhrasesForPinyin()` in Pinyin mode.
12. `OrbitInputMethodService.buildPhraseBar()` reads `TemplateLibrary.quickPhrasesForEnglish()` in English mode.
13. Pinyin-mode idle phrase bar shows many Chinese quick phrases.
14. English-mode idle phrase bar shows English quick phrases.
15. Tapping a phrase commits it directly to the target input field.
16. Pinyin candidates still have priority during composition.
17. User dictionary ranking still works.
18. Translate Preview remains local prompt generation.
19. Offline Translation Pack remains Pro-gated and local-only.
20. Pet controls remain in settings and do not block first-level keyboard typing.
21. Android/host-app long-press text menu is not treated as an Orbit-controlled feature.
22. Password field shows privacy mode, hides Hub actions, clears Pinyin composition, clears Translate Preview, hides Pet, blocks learning/growth, and uses warning/border colors.
23. No network, cloud translation, ad, analytics, Accessibility, overlay, billing, external API, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
