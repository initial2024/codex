# Codex handoff task: build Orbit IME v0.12 APK later

## Status

Orbit IME `0.12.0` Pinyin sentence shortcuts, English candidate input, and local translation data expansion have been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.12.0` after the input and data-layer expansion is considered ready.

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
orbit-ime-v0.12-debug-apk
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
2. `versionName` is `0.12.0`.
3. `versionCode` is `12`.
4. Settings page keeps the version only as `About · v0.12.0`.
5. GitHub Actions artifact name is `orbit-ime-v0.12-debug-apk`.
6. `PinyinSentenceDictionary.kt` exists and contains expanded shorthand and sentence candidates.
7. `EnglishDictionary.kt` exists and contains English word, phrase, and shorthand candidates.
8. `ProfessionalTranslationData.kt` exists and contains expanded local Chinese-English phrase translations.
9. `OfflineTranslationPack.kt` checks `ProfessionalTranslationData` before older boost tables, rough token assembly, and prompt fallback.
10. Exact Pinyin sentence candidates rank before core single-character candidates.
11. `nh` shows `你好` as a candidate.
12. `nisishei` shows `你是谁` as a candidate.
13. `hsywt` shows `还是有问题` as a candidate.
14. `myfyjg` shows `没有翻译结果` as a candidate.
15. `bscgfy` shows `不是成功翻译` as a candidate.
16. `bing`, `wgj`, `wj`, `wt`, `xg`, `srf`, `jqb`, `shurufa`, and `jianqieban` still show useful candidates.
17. Candidate tap and space-to-select work in Pinyin mode.
18. English mode does not commit each letter immediately.
19. Typing `hi` in English mode shows candidates before commit.
20. Typing `whq`, `build`, `translate`, and `problem` in English mode shows useful candidates before commit.
21. Pressing space in English mode commits the first English candidate and appends a space.
22. Tapping an English candidate commits that candidate directly.
23. User dictionary ranking still works for Pinyin.
24. Translate panel first tries local phrase translation.
25. Known phrases like `你好`, `你是谁`, `这是什么`, `怎么办`, `还是有问题`, `没有翻译结果`, `只是提示词`, `请给出可执行步骤`, `不要添加 INTERNET 权限`, and `I will handle it later` show directly insertable local translations.
26. Translate preview line clearly shows `译文：...` when a local translation is available.
27. If no local translation exists, Translate falls back to prompt generation rather than pretending to translate.
28. Password field shows privacy mode, hides Hub actions, clears Pinyin/English composition, clears Translate Preview, hides Pet, blocks learning/growth, and uses warning/border colors.
29. No network, cloud translation, external API, ad, analytics, Accessibility, overlay, billing, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
