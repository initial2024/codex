# Codex handoff task: build Orbit IME v0.2 APK

## Goal

Build the debug APK for Orbit IME Android `0.2.0`.

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
orbit-ime-v0.2-debug-apk
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
- Pinyin 9-key
- Wubi
- Handwriting recognition
- Skin redesign

## Functional acceptance criteria

1. APK builds successfully.
2. Orbit IME appears in Android input method settings.
3. The settings activity opens.
4. The IME can type English letters in `EN` mode.
5. The Hub toolbar can switch between `EN` and `拼音`.
6. Pinyin mode uses the same 26-key layout for Pinyin input.
7. Typing `nihao` shows `你好` in the candidate bar.
8. Tapping a candidate commits it to the editor.
9. Pressing space with a Pinyin buffer commits the first candidate.
10. Pressing backspace with a Pinyin buffer deletes the buffer before deleting editor text.
11. Pressing punctuation or switching to `123` after an incomplete Pinyin buffer commits the exact candidate when available; otherwise it commits the raw Pinyin text.
12. In Pinyin mode, `,` commits `，` and `.` commits `。` after resolving any pending Pinyin buffer.
13. `123` and `ABC` mode switching work.
14. Backspace, space, and enter work.
15. Paste inserts the current system clipboard.
16. Save persists the current clipboard only after a user tap.
17. Clips panel can insert saved items.
18. Password fields show `Privacy mode · Hub disabled` and hide Hub actions.
19. No network, ad, analytics, Accessibility, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
