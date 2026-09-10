# Codex handoff task: build Orbit IME v0.7 APK later

## Status

Orbit IME `0.7.0` feature work has been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.7.0` after the v0.7 feature work is considered ready.

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
orbit-ime-v0.7-debug-apk
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
2. `versionName` is `0.7.0`.
3. Orbit IME appears in Android input method settings.
4. The settings activity opens.
5. The settings page states Translate Preview is local prompt generation only.
6. The settings page states Offline Translation Pack is Pro-gated and local-only.
7. The settings page shows user dictionary stats and a clear button.
8. The settings page shows pet stats, check-in, hide/show, adopt/open-egg, and reset controls.
9. The settings activity shows skin choices.
10. Free skins can be selected and persisted locally.
11. Pro Aurora is locked unless `ProGate.isProUnlocked()` returns true.
12. Keyboard uses the selected skin after reopening the IME.
13. Keyboard types English letters.
14. `EN` / `拼音` switching works.
15. Typing `nihao` shows `你好` in the candidate bar.
16. Candidate tap and space-to-select work.
17. Candidate commits learn into the local user dictionary.
18. Exact user dictionary matches rank before generic static dictionary candidates.
19. User dictionary learning stores only `pinyin`, `candidate text`, `frequency`, and `updatedAt`.
20. User dictionary learning is blocked in privacy mode.
21. Tapping `Translate` opens Translate Preview.
22. Translate Preview supports `前一句`, `选中文本`, `剪贴板`, and `草稿` sources.
23. `前一句` and `选中文本` are read only after explicit user tap.
24. Draft mode keeps typed text in the IME draft buffer until `生成` or cancel.
25. `换方向` toggles 中→英 / 英→中.
26. Free users can insert or copy the generated prompt.
27. Pro users can use the local Offline Translation Pack when a short phrase has a local match.
28. Offline Translation Pack never calls a network API.
29. Secret-like or OTP-only source/candidate text is rejected before prompt generation, offline translation, or learning.
30. Pet chip appears in the Hub outside privacy mode.
31. Pet panel opens inside the keyboard surface only.
32. Pet supports check-in, random adopt/open egg, hide/show, local template chat, and outfit placeholder.
33. Pet growth reacts only to local counters: visible typed chars, candidate commit, clip save, and translate insertion.
34. Pet system does not store full input streams, app names, target fields, or surrounding sentences.
35. Pet system is hidden and blocked in privacy mode.
36. `123`/`ABC`, backspace, space, enter work.
37. Paste inserts clipboard.
38. Save persists clipboard only after explicit tap.
39. Clips panel inserts saved entries.
40. Password field shows privacy mode, hides Hub actions, clears Pinyin composition, clears Translate Preview, hides Pet, blocks learning/growth, and uses warning/border colors.
41. No network, cloud translation, ad, analytics, Accessibility, overlay, billing, external API, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
