# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with local English candidate input, Pinyin 26-key input, local dictionary ranking, expanded bilingual quick phrases, a user-controlled clipboard panel, local phrase translation, Translate Preview fallback, local skins, and a usable keyboard pet panel.

## Product boundary

This project is still not a full Chinese IME and does not yet match mature commercial IMEs in prediction quality.

Version `0.13.0` focuses on making the Keyboard Pet module usable while preserving the input method's offline and low-distraction boundaries.

## Privacy boundary

Version `0.13.0` deliberately avoids network and advertising logic.

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
- Pet data stays local and is not uploaded or synced.

## Pet improvements in v0.13.0

- The keyboard Hub has a `宠物` / `Pet` entry again.
- The pet panel shows current pet, species, stage, level, EXP, Stars, mood, today typed characters, total typed characters, and current outfit.
- The panel supports check-in, hatch egg, switch owned pet, rotate outfit, show pet catalog, show outfit catalog, hide/show, and close.
- The first hatch each day is free. Later hatches cost 30 Stars.
- Owned pets are stored locally and can be cycled.
- Outfit rotation is implemented as a data-layer placeholder until original art assets are supplied.
- Local chat text now appears directly inside the panel at every stage.
- Pet growth uses local typing, candidate commits, clip saves, translation insertion, and daily check-in.

## Pinyin improvements in v0.12.0+

- `PinyinDictionary.kt` keeps the core syllable dictionary.
- `PinyinSentenceDictionary.kt` contains common sentence-level and shorthand candidates.
- Exact sentence candidates rank before core single-character candidates.
- Test cases include `nh -> 你好`, `nisishei -> 你是谁`, `hsywt -> 还是有问题`, `zsm -> 这是什么`, `zmb -> 怎么办`, `smqk -> 什么情况`, `wgj -> 文件夹`, and `jqb -> 剪贴板`.
- This still does not implement a full commercial Pinyin decoder, statistical language model, or smart segmentation engine.

## English candidate input

- `EnglishDictionary.kt` provides local English candidates.
- English letters enter an English composing buffer first.
- Tapping a candidate commits it.
- Pressing space commits the first candidate and appends a space.
- Examples: `hi`, `whq`, `build`, `translate`, and `problem`.

## Translation improvements

- `ProfessionalTranslationData.kt` adds an expanded local Chinese-English phrase table.
- `TranslationBoostData.kt` adds supplemental local translation pairs and token maps.
- `OfflineTranslationPack.kt` checks local phrase tables before rough token assembly and prompt fallback.
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
- English candidate keyboard.
- Pinyin 26-key mode.
- Candidate bar with static boost data and local user dictionary ranking.
- Candidate tap-to-commit and space-to-select.
- Expanded bilingual quick phrase bar.
- Local clipboard vault backed by `SharedPreferences` JSON.
- Local phrase translation with Translate Preview fallback.
- Built-in skins: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
- Usable keyboard pet panel with local check-in, hatching, switching, outfits, catalog, and local chat text.

## Known limitation

Orbit IME cannot replace the host app or Android system long-press text-selection menu. The Orbit clipboard exists inside the keyboard's own Clips panel only.

Orbit IME cannot translate arbitrary long text without a network/API/model or a much larger offline dictionary. It only translates phrases included in the packaged local phrase table or very conservative token combinations.

A truly professional IME requires a large phrase dictionary, frequency data, segmentation, compact indexed assets, and build-time dictionary import tooling.

Pet art is still placeholder-only. Real icons, SVG/vector bodies, and outfit visuals should be added only after original Gemini art specifications are supplied.

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
orbit-ime-v0.13-debug-apk
```

## Manual test checklist

1. Install the APK on Android.
2. Open Orbit IME app.
3. Enable Orbit IME in system input method settings.
4. Switch to Orbit IME.
5. Confirm version is `0.13.0`.
6. Type `nh`, `nisishei`, `hsywt`, `zsm`, `zmb`, `smqk`, `wgj`, `jqb`, `shurufa`, and `jianqieban`; confirm useful candidates appear.
7. Type English `hi`, `whq`, `build`, `translate`, and `problem`; confirm English candidates appear before commit.
8. Confirm candidate tap and space-to-select work.
9. Use Translate on `你好`, `你是谁`, `这是什么`, `怎么办`, `还是有问题`, `请给出可执行步骤`, and `I will handle it later`; confirm a real local translation can be inserted.
10. Open `宠物` / `Pet` from the keyboard Hub.
11. Confirm the pet panel shows pet status, level, EXP, Stars, mood, today typed count, total typed count, and outfit.
12. Tap `签到`, `开蛋`, `切换`, `装扮`, `图鉴`, `装扮库`, `隐藏/显示`, and `关闭`; confirm each action gives visible feedback.
13. Confirm Clips, quick phrases, and privacy mode still work.
14. Confirm Manifest still has no network, ad, analytics, Accessibility, or overlay permission.

## Commercial direction

The intended business model is free base version plus paid Pro unlock. Do not put ads inside the keyboard input surface. Version `0.13.0` contains no advertising, billing, network, cloud translation, or external translation API code.
