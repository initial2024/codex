# Codex handoff task: build Orbit IME v0.14 APK later

## Status

Orbit IME `0.14.0` local data expansion has been prepared. Build only after the user asks to start the APK build.

## Goal

Build the debug APK for Orbit IME Android `0.14.0` after the input-data, fuzzy-correction, English-candidate, translation, and local-learning changes are considered ready.

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
orbit-ime-v0.14-debug-apk
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
2. `versionName` is `0.14.0`.
3. `versionCode` is `14`.
4. Settings page keeps the version only as `About · v0.14.0`.
5. GitHub Actions artifact name is `orbit-ime-v0.14-debug-apk`.
6. `PinyinExpandedData.kt` exists and contains expanded local Pinyin words, shorthand, and sentence candidates.
7. `PinyinCorrectionEngine.kt` exists and returns fuzzy/typo candidates.
8. `PinyinSentenceDictionary.kt` merges base sentence shortcuts, expanded data, and fuzzy correction.
9. `xhfnivh` shows Chinese candidates, including `喜欢你`.
10. `nh` shows `你好` / `你好吗`.
11. `nisishei` shows `你是谁`.
12. `hsywt` shows `还是有问题`.
13. `myfyjg` shows `没有翻译结果`.
14. `bscgfy` shows `不是成功翻译`.
15. `sjkb` shows `数据库不够`.
16. `wgj`, `wj`, `wt`, `xg`, `srf`, `jqb`, `shurufa`, and `jianqieban` still show useful candidates.
17. User dictionary local learning still works after candidate tap and space-to-select.
18. User dictionary stores only pinyin, committed candidate text, frequency, and updatedAt.
19. User dictionary allows longer local learned phrases and returns up to 12 candidates.
20. `EnglishDictionary.kt` includes expanded English word, phrase, shorthand, and typo-correction candidates.
21. Typing `hi`, `whq`, `build`, `translate`, `problem`, `professional`, `dictionary`, `trasnlate`, and `permision` shows useful English candidates before commit.
22. Pressing space in English mode commits the first English candidate and appends a space.
23. `TranslationExpansionData.kt` exists and expands local Chinese-English phrase translation.
24. `OfflineTranslationPack.kt` checks `TranslationExpansionData` before rough token assembly and prompt fallback.
25. Known phrases like `翻译不知道去哪里了`, `没有翻译结果`, `只是提示词`, `不是成功翻译`, `英文没有选择`, `不能形成句子`, `数据库不够`, `加入个人学习功能`, `接近专业版本`, and `I will handle it later` show directly insertable local translations.
26. Unsupported text still falls back to prompt generation instead of pretending to translate.
27. The keyboard Hub still has `宠物` / `Pet`, and the pet panel remains usable.
28. Starting Pinyin or English composing hides the pet panel so it does not block candidates.
29. Privacy mode hides Hub tools and clears Pinyin, English, Translate, and Pet state.
30. No network, cloud translation, external API, ad, analytics, Accessibility, overlay, billing, dictionary upload, pet upload, or background harvesting behavior is introduced.

## Fix policy

If compilation fails, fix only the minimum necessary build, Kotlin, resource, or IME metadata issue. Do not expand product scope.
