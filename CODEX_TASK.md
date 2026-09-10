# Codex handoff task: build Orbit IME v0.4 APK

## Goal

Build the debug APK for Orbit IME Android `0.4.0`.

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
orbit-ime-v0.4-debug-apk
```

## Non-negotiable constraints

Do not add:

- INTERNET permission
- Cloud translation
- External translation API
- Ad SDK
- Analytics SDK
- Accessibility permission
- Background service
- Notification spam
- Clipboard background harvesting
- Password-field saving
- Typed key stream persistence
- Translate history persistence
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
2. `versionName` is `0.4.0`.
3. Orbit IME appears in Android input method settings.
4. The settings activity opens.
5. The settings activity explains that Translate Preview is local prompt generation only.
6. The settings activity shows skin choices: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
7. Orbit Dark, Orbit Light, AMOLED Black, and Study Blue can be selected locally.
8. Pro Aurora is displayed as a locked Pro placeholder unless `ProGate.isProUnlocked()` returns true.
9. Selected skin persists through app restart via local `SharedPreferences`.
10. The IME surface uses the selected skin after the keyboard is reopened.
11. Password fields show `🔒 Privacy mode · Hub disabled`, hide Hub actions, clear Pinyin composition, clear Translate Preview, and use warning/border colors.
12. The IME can type English letters in `EN` mode.
13. The Hub toolbar can switch between `EN` and `拼音`.
14. Pinyin mode uses the same 26-key layout for Pinyin input.
15. Typing `nihao` shows `你好` in the candidate bar.
16. Tapping a candidate commits it to the editor.
17. Pressing space with a Pinyin buffer commits the first candidate.
18. Pressing backspace with a Pinyin buffer deletes the buffer before deleting editor text.
19. Pressing punctuation or switching to `123` after an incomplete Pinyin buffer commits the exact candidate when available; otherwise it commits the raw Pinyin text.
20. In Pinyin mode, `,` commits `，` and `.` commits `。` after resolving any pending Pinyin buffer.
21. `123` and `ABC` mode switching work.
22. Backspace, space, and enter work.
23. Paste inserts the current system clipboard.
24. Save persists the current clipboard only after a user tap.
25. Clips panel can insert saved items.
26. Tapping `Translate` opens Translate Preview instead of inserting an immediate template.
27. Translate Preview can generate a local 中→英 prompt from clipboard text after an explicit `剪贴板` tap.
28. Translate Preview can generate a local 英→中 prompt after `换方向`.
29. Translate Preview can generate a local prompt from selected text after an explicit `选中文本` tap.
30. Translate Preview can read the previous sentence only after an explicit `前一句` tap.
31. Translate Preview draft mode lets the user type into the IME draft before committing to the target editor.
32. Draft mode `生成Prompt` creates a prompt preview without first committing the draft to the target editor.
33. `插入` commits only the generated prompt to the target editor.
34. `复制` copies only the generated prompt to the system clipboard.
35. Secret-like or OTP-only source text is rejected before prompt generation.
36. No network, cloud translation, ad, analytics, Accessibility, billing, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
