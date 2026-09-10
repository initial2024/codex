# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with local English input, Pinyin 26-key input, local dictionary ranking, an expanded bilingual quick phrase bar, a user-controlled clipboard panel, Translate Preview, local skins, a keyboard-only pet MVP, and a Pro offline translation pack placeholder.

## Product boundary

This project is still not a full Chinese IME and does not try to compete with mature commercial IMEs in prediction quality.

Version `0.10.0` is a quick-typing usability release. It expands the built-in Chinese and English quick phrase library so the idle phrase bar is useful for fast replies instead of showing only demo text.

## Privacy boundary

Version `0.10.0` deliberately avoids network and advertising logic.

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
- Offline Translation Pack is local-only and Pro-gated.

## Quick phrase improvements in v0.10.0

- Chinese and English quick phrases are now maintained in `TemplateLibrary.kt`.
- Pinyin mode reads `TemplateLibrary.quickPhrasesForPinyin()`.
- English mode reads `TemplateLibrary.quickPhrasesForEnglish()`.
- Chinese phrases cover confirmation, communication, Codex/development handoff, study/writing, and daily replies.
- English phrases cover confirmation, requests, development collaboration, writing/study, and daily replies.
- The first-level Hub remains minimal; phrase expansion does not add network, AI, ads, or background collection.

## Current features

- Android IME service declared in `AndroidManifest.xml`.
- Settings activity with input method setup buttons.
- English keyboard.
- Pinyin 26-key mode.
- Candidate bar with static and local user dictionary ranking.
- Candidate tap-to-commit and space-to-select.
- Expanded bilingual quick phrase bar.
- Local clipboard vault backed by `SharedPreferences` JSON.
- Translate Preview with previous sentence, selected text, clipboard, and draft sources.
- Pro Offline Translation Pack for short local phrase translations.
- Built-in skins: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
- Pet controls remain in settings while keyboard UX is stabilized.

## Known limitation

Orbit IME cannot replace the host app or Android system long-press text-selection menu. The Orbit clipboard exists inside the keyboard's own Clips panel only.

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
Do not add INTERNET permission, ad SDK, analytics SDK, Accessibility permission, overlay permission, cloud translation, or background services.
Follow CODEX_TASK.md exactly.
```

## Build with GitHub Actions

A manual workflow is included at `.github/workflows/build-apk.yml`.

It uses `workflow_dispatch` only. It does not build automatically on push.

The debug APK artifact name is:

```text
orbit-ime-v0.10-debug-apk
```

## Manual test checklist

1. Install the APK on Android.
2. Open Orbit IME app.
3. Enable Orbit IME in system input method settings.
4. Switch to Orbit IME.
5. Confirm version is `0.10.0`.
6. Switch to Pinyin mode and confirm the idle phrase bar shows many Chinese quick phrases.
7. Switch to English mode and confirm the idle phrase bar shows English quick phrases.
8. Tap several Chinese and English phrases and confirm they insert directly.
9. Type `nihao`, `bing`, `wgj`, `wj`, `wt`, `xg`, `shurufa`, and `jianqieban` and confirm useful candidates appear.
10. Confirm Clips, Translate Preview, and privacy mode still work.
11. Confirm Manifest still has no network, ad, analytics, Accessibility, or overlay permission.

## Commercial direction

The intended business model is free base version plus paid Pro unlock. Do not put ads inside the keyboard input surface. Version `0.10.0` contains no advertising, billing, network, or cloud translation code.
