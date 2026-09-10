# Codex handoff task: build Orbit IME APK

## Goal

Build the first debug APK for Orbit IME Android.

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

## Non-negotiable constraints

Do not add:

- INTERNET permission
- Ad SDK
- Analytics SDK
- Accessibility permission
- Background service
- Notification spam
- Clipboard background harvesting
- Password-field saving

## Functional acceptance criteria

1. APK builds successfully.
2. Orbit IME appears in Android input method settings.
3. The settings activity opens.
4. The IME can type English letters.
5. `123` and `ABC` mode switching work.
6. Backspace, space, and enter work.
7. Paste inserts the current system clipboard.
8. Save persists the current clipboard only after a user tap.
9. Clips panel can insert saved items.
10. Password fields show `Privacy mode · Hub disabled` and hide Hub actions.

## Fix policy

If compilation fails, fix only the minimum necessary build or Kotlin issue. Do not expand product scope.
