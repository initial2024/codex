# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with local English input, Pinyin 26-key input, local dictionary ranking, expanded bilingual quick phrases, a user-controlled clipboard panel, local phrase translation, Translate Preview fallback, local skins, and pet controls in settings.

## Product boundary

This project is still not a full Chinese IME and does not try to compete with mature commercial IMEs in prediction quality.

Version `0.11.0` is a Pinyin shortcut and translation usability release. It fixes common test cases where the keyboard previously returned raw letters such as `nh` or `nisishei`, and it makes Translate try a real local phrase translation before falling back to prompt generation.

## Privacy boundary

Version `0.11.0` deliberately avoids network and advertising logic.

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

## Pinyin fixes in v0.11.0

- `nh` now shows `你好`.
- `nisishei` now shows `你是谁`.
- `hsywt` now shows `还是有问题`.
- Common full-pinyin words and shortcuts are preserved, including `bing`, `wgj`, `wj`, `wt`, `xg`, `srf`, `jqb`, `shurufa`, and `jianqieban`.
- This remains a lightweight local dictionary, not a full commercial IME decoder.

## Translation fixes in v0.11.0

- Translate first tries `OfflineTranslationPack.translateOrNull()`.
- Known phrases such as `你好`, `你是谁`, `还是有问题`, `我晚点处理`, and `I will handle it later` produce a real local translation.
- If no local phrase translation exists, Orbit falls back to the existing Translate Preview prompt-generation flow.
- No server, model endpoint, external API, or network permission is used.

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
- Candidate bar with static and local user dictionary ranking.
- Candidate tap-to-commit and space-to-select.
- Expanded bilingual quick phrase bar.
- Local clipboard vault backed by `SharedPreferences` JSON.
- Local phrase translation with Translate Preview fallback.
- Built-in skins: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
- Pet controls remain in settings while keyboard UX is stabilized.

## Known limitation

Orbit IME cannot replace the host app or Android system long-press text-selection menu. The Orbit clipboard exists inside the keyboard's own Clips panel only.

Orbit IME cannot translate arbitrary long text without a network/API/model or a much larger offline dictionary. It only translates phrases included in the packaged local phrase table or very conservative token combinations.

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
- Large user dictionary.
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
orbit-ime-v0.11-debug-apk
```

## Manual test checklist

1. Install the APK on Android.
2. Open Orbit IME app.
3. Enable Orbit IME in system input method settings.
4. Switch to Orbit IME.
5. Confirm version is `0.11.0`.
6. Type `nh` and confirm `你好` appears.
7. Type `nisishei` and confirm `你是谁` appears.
8. Type `hsywt` and confirm `还是有问题` appears.
9. Type `bing`, `wgj`, `wj`, `wt`, `xg`, `shurufa`, and `jianqieban` and confirm useful candidates appear.
10. Use Translate on `你好`, `你是谁`, `还是有问题`, and `I will handle it later`; confirm a real local translation can be inserted.
11. Use Translate on an unsupported long sentence and confirm Orbit clearly falls back to prompt generation instead of pretending to translate.
12. Confirm Clips, quick phrases, and privacy mode still work.
13. Confirm Manifest still has no network, ad, analytics, Accessibility, or overlay permission.

## Commercial direction

The intended business model is free base version plus paid Pro unlock. Do not put ads inside the keyboard input surface. Version `0.11.0` contains no advertising, billing, network, cloud translation, or external translation API code.
