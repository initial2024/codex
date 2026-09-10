# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with local English input, Pinyin 26-key input, local dictionary ranking, expanded bilingual quick phrases, a user-controlled clipboard panel, local phrase translation, Translate Preview fallback, local skins, and pet controls in settings.

## Product boundary

This project is still not a full Chinese IME and does not yet match mature commercial IMEs in prediction quality.

Version `0.12.0` is a data-expansion and architecture cleanup release. It adds separate boost data layers for Pinyin candidates and local phrase translation so future dictionary imports can be added without rewriting the keyboard service.

## Privacy boundary

Version `0.12.0` deliberately avoids network and advertising logic.

- No `INTERNET` permission.
- No ad SDK.
- No analytics SDK.
- No Accessibility permission.
- No overlay / floating-window permission.
- Clipboard content is saved only after the user taps the Orbit Clips save action.
- Password-like input fields enter privacy mode and hide Hub functions.
- Typed key streams are not persisted.
- User dictionary stores only pinyin, candidate text, frequency, and updatedAt.
- Translate Preview source text and generated prompts are not persisted.
- Local phrase translation uses only packaged phrase tables.

## Pinyin improvements in v0.12.0

- `PinyinDictionary.kt` now keeps the core syllable dictionary.
- `PinyinBoostData.kt` contains common full-pinyin, shorthand, and sentence-level candidates.
- Exact boost candidates rank before core single-character candidates.
- The candidate limit is increased to 10.
- Test cases include `nh -> 你好`, `nisishei -> 你是谁`, `hsywt -> 还是有问题`, `zsm -> 这是什么`, `zmb -> 怎么办`, `smqk -> 什么情况`, `wgj -> 文件夹`, and `jqb -> 剪贴板`.
- This still does not implement a full commercial Pinyin decoder, statistical language model, or smart segmentation engine.

## Translation improvements in v0.12.0

- `TranslationBoostData.kt` adds an expanded local Chinese-English phrase table and token map.
- `OfflineTranslationPack.kt` checks the boost translation table before falling back to rough token assembly and prompt generation.
- Known phrases such as `你好`, `你是谁`, `这是什么`, `怎么办`, `还是有问题`, `请给出可执行步骤`, `不要添加 INTERNET 权限`, and `I will handle it later` produce directly insertable local translations.
- Unsupported text falls back to Translate Preview prompt generation instead of pretending to translate.
- No server, model endpoint, external API, or network permission is used.

## Data source strategy

See `DATA_SOURCES.md`.

The current bundled boost data is project-authored. Public sources reviewed for future import pipelines include CC-CEDICT, AOSP Pinyin IME, RIME/Trime, and open phrase-pinyin datasets with explicit licenses.

Do not copy arbitrary GitHub dictionary data into this repository without a compatible license and attribution plan.

## Quick phrase improvements

- Chinese and English quick phrases are maintained in `TemplateLibrary.kt`.
- Pinyin mode reads `TemplateLibrary.quickPhrasesForPinyin()`.
- English mode reads `TemplateLibrary.quickPhrasesForEnglish()`.
- Chinese phrases cover confirmation, communication, Codex/development handoff, study/writing, and daily replies.
- English phrases cover confirmation, requests, development collaboration, writing/study, and daily replies.

## Current features

- Android IME service declared in `AndroidManifest.xml`.
- Settings activity with input method setup buttons.
- English keyboard.
- Pinyin 26-key mode.
- Candidate bar with static boost data and local user dictionary ranking.
- Candidate tap-to-commit and space-to-select.
- Expanded bilingual quick phrase bar.
- Local clipboard vault backed by `SharedPreferences` JSON.
- Local phrase translation with Translate Preview fallback.
- Built-in skins: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
- Pet controls remain in settings while keyboard UX is stabilized.

## Known limitation

Orbit IME cannot replace the host app or Android system long-press text-selection menu. The Orbit clipboard exists inside the keyboard's own Clips panel only.

Orbit IME cannot translate arbitrary long text without a network/API/model or a much larger offline dictionary. It only translates phrases included in the packaged local phrase table or very conservative token combinations.

A truly professional IME requires a large phrase dictionary, frequency data, segmentation, compact indexed assets, and build-time dictionary import tooling.

## Not included yet

- Pinyin 9-key.
- Wubi.
- Handwriting recognition.
- Cloud sync.
- Cloud translation.
- External translation APIs.
- Ad monetization.
- Paid billing implementation.
- Smart segmentation.
- Large imported dictionary assets.
- Canvas keyboard rewrite.
- Compose migration.
- Skin marketplace.
- System-wide floating pet.
- Complex pet animation.
- AI pet chat.

## Build with Codex

```text
Clone https://github.com/initial2024/codex and build the Android debug APK only after the user asks to start the build.
Use JDK 17 and Android SDK 35.
Run: gradle assembleDebug --no-daemon
After building, return app/build/outputs/apk/debug/app-debug.apk.
Do not add INTERNET permission, ad SDK, analytics SDK, Accessibility permission, overlay permission, cloud translation, external translation APIs, or background services.
Follow CODEX_TASK.md exactly.
```

## Build with GitHub Actions

A manual workflow is included at `.github/workflows/build-apk.yml`.

It uses `workflow_dispatch` only. It does not build automatically on push.

The debug APK artifact name is:

```text
orbit-ime-v0.12-debug-apk
```

## Manual test checklist

1. Install the APK on Android.
2. Open Orbit IME app.
3. Enable Orbit IME in system input method settings.
4. Switch to Orbit IME.
5. Confirm version is `0.12.0`.
6. Type `nh`, `nisishei`, `hsywt`, `zsm`, `zmb`, `smqk`, `wgj`, `jqb`, `shurufa`, and `jianqieban`; confirm useful candidates appear.
7. Confirm candidate tap and space-to-select work.
8. Confirm local user dictionary ranking still works.
9. Use Translate on `你好`, `你是谁`, `这是什么`, `怎么办`, `还是有问题`, `请给出可执行步骤`, and `I will handle it later`; confirm a real local translation can be inserted.
10. Use Translate on an unsupported long sentence and confirm Orbit clearly falls back to prompt generation instead of pretending to translate.
11. Confirm Clips, quick phrases, and privacy mode still work.
12. Confirm Manifest still has no network, ad, analytics, Accessibility, or overlay permission.

## Commercial direction

The intended business model is free base version plus paid Pro unlock. Do not put ads inside the keyboard input surface. Version `0.12.0` contains no advertising, billing, network, cloud translation, or external translation API code.
