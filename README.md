# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with an Orbit Hub style toolbar, local prompt templates, a user-controlled local clipboard vault, a minimal local Pinyin 26-key mode, local skins, Translate Preview, a local user dictionary, a keyboard-only pet MVP, and a Pro offline translation pack placeholder.

## Product boundary

This project is not yet a full Chinese IME and does not try to compete with Gboard, Sogou, Baidu IME, or iFlytek IME in prediction quality.

Version `0.7.0` focuses on ten things:

1. A real Android IME based on `InputMethodService`.
2. Local English and Pinyin 26-key input.
3. Local static Pinyin dictionary plus local user dictionary ranking.
4. User-initiated local clipboard Save / Clips.
5. Translate Preview prompt generation.
6. Pro Offline Translation Pack placeholder for short local phrases.
7. Local skin system.
8. Keyboard-only pet MVP.
9. Privacy mode for password-like fields.
10. No network, no ads, no analytics, no Accessibility, and no overlay/floating-window permission.

## Privacy boundary

Version `0.7.0` deliberately avoids network and advertising logic.

- No `INTERNET` permission.
- No ad SDK.
- No analytics SDK.
- No Accessibility permission.
- No overlay / floating-window permission.
- No Contacts, SMS, Location, Camera, Microphone, or Storage permission.
- Clipboard content is saved only after the user taps `Save`.
- Password-like input fields enter privacy mode and hide Hub functions.
- Typed key streams are not persisted.
- User dictionary stores only pinyin, candidate text, frequency, and updatedAt.
- Pet growth stores only local counters and pet state.
- Translate Preview source text and generated prompts are not persisted.
- Offline Translation Pack is local-only and Pro-gated.

## Features in v0.7.0

- Android IME service declared in `AndroidManifest.xml`.
- Settings activity with input method setup buttons.
- Settings activity skin selector.
- Settings activity user dictionary stats and clear button.
- Settings activity pet stats, check-in, hide/show, adopt, and reset controls.
- English keyboard.
- Pinyin 26-key mode.
- `EN` / `拼音` toggle in the Hub toolbar.
- Candidate bar with static and local user dictionary ranking.
- Candidate tap-to-commit and space-to-select.
- Local clipboard vault backed by `SharedPreferences` JSON.
- Translate Preview with previous sentence, selected text, clipboard, and draft sources.
- Pro Offline Translation Pack for short local phrase translations.
- Built-in skins: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
- Keyboard Pet MVP:
  - Compact Hub pet chip.
  - In-keyboard pet panel.
  - Daily check-in.
  - Local typed-character growth.
  - Candidate, clip, and translate growth signals.
  - Random adopt / open-egg action.
  - Hidden / keyboard-only display modes.
  - Local template chat unlocked at Adult stage.
  - Outfit slot placeholder for later Gemini assets.

## Feature documents

```text
SKIN_DESIGN.md
TRANSLATE_PREVIEW.md
USER_DICTIONARY.md
PET_SYSTEM.md
OFFLINE_TRANSLATION_PACK.md
```

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

Recommended Codex task:

```text
Clone https://github.com/initial2024/codex and build the Android debug APK only after the user asks to start the build.
Use JDK 17 and Android SDK 35.
Run: gradle assembleDebug --no-daemon
If the environment has no Gradle command, install/provision Gradle 8.10.2 first, then rerun the build.
After building, return app/build/outputs/apk/debug/app-debug.apk.
Do not add INTERNET permission, ad SDK, analytics SDK, Accessibility permission, overlay permission, cloud translation, or background services.
Follow CODEX_TASK.md exactly.
```

## Build with GitHub Actions

A manual workflow is included at:

```text
.github/workflows/build-apk.yml
```

It uses `workflow_dispatch` only. It does not build automatically on push.

The debug APK artifact name is:

```text
orbit-ime-v0.7-debug-apk
```

## Local build

```bash
git clone https://github.com/initial2024/codex.git
cd codex
gradle assembleDebug --no-daemon
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Manual test checklist

1. Install the APK on Android.
2. Open Orbit IME app.
3. Enable Orbit IME in system input method settings.
4. Switch to Orbit IME.
5. Type English letters in `EN` mode.
6. Switch to `拼音` mode.
7. Type `nihao` and confirm `你好` appears.
8. Select candidates repeatedly and confirm local dictionary ranking changes.
9. Open settings and confirm dictionary stats update.
10. Use Translate Preview sources.
11. Confirm free users get prompt generation only.
12. Confirm Pro offline pack is locked unless `ProGate` is unlocked.
13. Open Pet panel from the Hub.
14. Use check-in, open egg, hide/show, and local chat.
15. Confirm pet does not appear in password fields.
16. Confirm privacy mode hides Hub actions and clears composition/Translate/Pet panels.
17. Confirm Manifest still has no network, ad, analytics, Accessibility, or overlay permission.

## Commercial direction

The intended business model is free base version plus paid Pro unlock.

Pro can later unlock more pets, more outfits, higher local dictionary quota, higher clipboard quota, Pro Aurora, and optional offline packs. Do not put ads inside the keyboard input surface. Version `0.7.0` contains no advertising, billing, network, or cloud translation code.
