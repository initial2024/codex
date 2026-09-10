# Codex handoff task: build Orbit IME v0.13 APK later

## Status

Orbit IME `0.13.0` usable Keyboard Pet module has been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.13.0` after the pet module changes are considered ready.

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
orbit-ime-v0.13-debug-apk
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
2. `versionName` is `0.13.0`.
3. `versionCode` is `13`.
4. Settings page keeps the version only as `About · v0.13.0`.
5. GitHub Actions artifact name is `orbit-ime-v0.13-debug-apk`.
6. The keyboard Hub shows `宠物` in Pinyin mode and `Pet` in English mode.
7. Tapping `宠物` / `Pet` opens a usable keyboard pet panel.
8. The pet panel shows current pet, species, stage, level, EXP, Stars, mood, today's typed characters, total typed characters, and outfit.
9. The pet panel has working actions: `签到`, `开蛋`, `切换`, `装扮`, `图鉴`, `装扮库`, `隐藏/显示`, and `关闭`.
10. `签到` gives Chinese feedback and grants Stars once per day.
11. `开蛋` gives one free hatch per day; later hatches cost 30 Stars.
12. Owned pets are stored locally and `切换` cycles owned pets.
13. `装扮` cycles local outfit placeholders.
14. `图鉴` shows owned/free/pro pet catalog status.
15. `装扮库` shows available outfit placeholders.
16. Pet growth still increments from local typing, candidate commits, clip saves, translate insertion, and check-in.
17. Starting Pinyin or English composing hides the pet panel so it does not block candidates.
18. Privacy mode hides Hub tools and clears the pet panel.
19. Pinyin and English candidate improvements from v0.12 still work.
20. Local translation improvements from v0.12 still work.
21. No network, cloud translation, external API, ad, analytics, Accessibility, overlay, billing, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
