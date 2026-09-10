# Codex handoff task: build Orbit IME v0.3 APK

## Goal

Build the debug APK for Orbit IME Android `0.3.0`.

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
orbit-ime-v0.3-debug-apk
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
- Canvas keyboard rewrite
- Compose migration
- Room or Realm migration
- Billing implementation
- Skin marketplace

## Functional acceptance criteria

1. APK builds successfully.
2. `versionName` is `0.3.0`.
3. Orbit IME appears in Android input method settings.
4. The settings activity opens.
5. The settings activity shows skin choices: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
6. Orbit Dark, Orbit Light, AMOLED Black, and Study Blue can be selected locally.
7. Pro Aurora is displayed as a locked Pro placeholder unless `ProGate.isProUnlocked()` returns true.
8. Selected skin persists through app restart via local `SharedPreferences`.
9. The IME surface uses the selected skin after the keyboard is reopened.
10. Password fields show `🔒 Privacy mode · Hub disabled`, hide Hub actions, clear Pinyin composition, and use warning/border colors.
11. The IME can type English letters in `EN` mode.
12. The Hub toolbar can switch between `EN` and `拼音`.
13. Pinyin mode uses the same 26-key layout for Pinyin input.
14. Typing `nihao` shows `你好` in the candidate bar.
15. Tapping a candidate commits it to the editor.
16. Pressing space with a Pinyin buffer commits the first candidate.
17. Pressing backspace with a Pinyin buffer deletes the buffer before deleting editor text.
18. Pressing punctuation or switching to `123` after an incomplete Pinyin buffer commits the exact candidate when available; otherwise it commits the raw Pinyin text.
19. In Pinyin mode, `,` commits `，` and `.` commits `。` after resolving any pending Pinyin buffer.
20. `123` and `ABC` mode switching work.
21. Backspace, space, and enter work.
22. Paste inserts the current system clipboard.
23. Save persists the current clipboard only after a user tap.
24. Clips panel can insert saved items.
25. No network, ad, analytics, Accessibility, billing, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
