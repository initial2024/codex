# Codex handoff task: build Orbit IME v0.5 APK

## Goal

Build the debug APK for Orbit IME Android `0.5.0` after the v0.5 feature work is considered ready.

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
orbit-ime-v0.5-debug-apk
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
- Full typed-key-stream persistence
- Translate Preview history persistence
- User dictionary cloud sync
- User dictionary upload
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
2. `versionName` is `0.5.0`.
3. Orbit IME appears in Android input method settings.
4. The settings activity opens.
5. The settings page states Translate Preview is local prompt generation only.
6. The settings page shows user dictionary stats: entry count, total selection count, and quota.
7. The settings page can clear the local user dictionary.
8. The settings activity shows skin choices: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
9. Orbit Dark, Orbit Light, AMOLED Black, and Study Blue can be selected locally.
10. Pro Aurora is displayed as a locked Pro placeholder unless `ProGate.isProUnlocked()` returns true.
11. Selected skin persists through app restart via local `SharedPreferences`.
12. The IME surface uses the selected skin after the keyboard is reopened.
13. The keyboard types English letters in `EN` mode.
14. `EN` / `拼音` switching works.
15. Pinyin mode uses the same 26-key layout for Pinyin input.
16. Typing `nihao` shows `你好` in the candidate bar.
17. Candidate tap and space-to-select work.
18. After repeated candidate selection, the committed candidate is learned into the local user dictionary.
19. Exact user dictionary matches rank before generic static dictionary candidates.
20. The candidate bar shows local dictionary entry count.
21. User dictionary learning stores only `pinyin`, `candidate text`, `frequency`, and `updatedAt`.
22. User dictionary learning does not store surrounding sentences, app names, target fields, or full input history.
23. User dictionary learning is blocked in privacy mode.
24. Secret-like or OTP-only candidate text is rejected before learning.
25. Static dictionary entries remain generic; developer-personal target terms must not be hardcoded.
26. `123`/`ABC`, backspace, space, enter work.
27. Paste inserts clipboard.
28. Save persists clipboard only after explicit tap.
29. Clips panel inserts saved entries.
30. Tapping `Translate` opens Translate Preview instead of inserting an immediate template.
31. Translate Preview supports `前一句`, `选中文本`, `剪贴板`, and `草稿` sources.
32. `前一句` and `选中文本` are read only after explicit user tap.
33. Draft mode keeps typed text in the IME draft buffer until `生成Prompt` or cancel.
34. `换方向` toggles 中→英 / 英→中.
35. `插入` commits the generated prompt.
36. `复制` copies the generated prompt to the system clipboard.
37. Secret-like or OTP-only source text is rejected before prompt generation.
38. Password field shows privacy mode, hides Hub actions, clears Pinyin composition, clears Translate Preview, blocks user-dictionary learning, and uses warning/border colors.
39. No network, cloud translation, ad, analytics, Accessibility, billing, external API, dictionary upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
