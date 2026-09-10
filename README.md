# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with local English input, Pinyin 26-key input, local dictionary ranking, a user-controlled clipboard panel, Translate Preview, local skins, a settings-managed pet MVP, and a Pro offline translation pack placeholder.

## Product boundary

This project is still not a full Chinese IME and does not try to compete with mature commercial IMEs in prediction quality.

Version `0.9.0` is another usability-fix release based on direct device feedback. It corrects confusing controls, weak candidate coverage, and pet UI interference instead of adding new large features.

## Privacy boundary

Version `0.9.0` deliberately avoids network and advertising logic.

- No `INTERNET` permission.
- No ad SDK.
- No analytics SDK.
- No Accessibility permission.
- No overlay / floating-window permission.
- No Contacts, SMS, Location, Camera, Microphone, or Storage permission.
- Clipboard content is saved only after the user taps the Orbit Clips save action.
- Password-like input fields enter privacy mode and hide Hub functions.
- Typed key streams are not persisted.
- User dictionary stores only pinyin, candidate text, frequency, and updatedAt.
- Pet growth stores only local counters and pet state.
- Translate Preview source text and generated prompts are not persisted.
- Offline Translation Pack is local-only and Pro-gated.

## Usability fixes in v0.9.0

- Adds a first-level `切换` / `Switch` Hub button that calls Android's input-method picker.
- Makes the bottom-right key display `回车` / `Enter` instead of a bare arrow, so it is not mistaken for an input-method switcher.
- Keeps Android's own switcher bubble as a system/host-app feature, not an Orbit-controlled UI.
- Removes the pet chip from the first-level keyboard Hub until art assets and interaction quality are ready.
- Keeps pet management in the settings screen for now.
- Separates idle phrase suggestions by mode:
  - Pinyin mode shows Chinese phrases.
  - English mode shows English phrases.
- Expands common Pinyin coverage, including `bing -> 并/病/冰/兵`.
- Keeps the Clips save action inside the Clips panel as `保存当前剪贴板`.
- Preserves the v0.8 simplified settings page and bottom safe area changes.

## Current features

- Android IME service declared in `AndroidManifest.xml`.
- Settings activity with input method setup buttons.
- Settings activity skin selector.
- Settings activity user dictionary stats and clear button.
- Settings activity pet stats, check-in, hide/show, adopt, and reset controls.
- English keyboard.
- Pinyin 26-key mode.
- Candidate bar with static and local user dictionary ranking.
- Candidate tap-to-commit and space-to-select.
- Local clipboard vault backed by `SharedPreferences` JSON.
- Translate Preview with previous sentence, selected text, clipboard, and draft sources.
- Pro Offline Translation Pack for short local phrase translations.
- Built-in skins: Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora.
- Pet MVP managed from settings while keyboard Hub placement is paused.

## Known limitation

Orbit IME cannot replace the host app or Android system long-press text-selection menu. The Orbit clipboard exists inside the keyboard's own Clips panel only.

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
orbit-ime-v0.9-debug-apk
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
5. Tap `切换` / `Switch` and confirm Android's input-method picker opens.
6. Confirm the bottom-right key is labeled `回车` / `Enter` and sends Enter, not input-method switching.
7. Type English letters in `EN` mode.
8. Confirm English mode idle phrases are English, not Chinese.
9. Switch to `拼音` mode and confirm Hub labels are Chinese.
10. Type `nihao` and confirm `你好` appears.
11. Type `bing` and confirm `并/病/冰/兵` candidates appear.
12. Type `wgj`, `wj`, `wt`, `xg`, `shurufa`, `jianqieban`, and confirm useful candidates appear.
13. Select candidates repeatedly and confirm local dictionary ranking changes.
14. Open Clips and confirm `保存当前剪贴板` is inside the Clips panel.
15. Confirm Android's long-press menu is not treated as an Orbit bug.
16. Use Translate Preview sources.
17. Confirm free users get prompt generation only.
18. Confirm pet controls remain available in settings, not first-level keyboard Hub.
19. Confirm pet does not appear in password fields.
20. Confirm privacy mode hides Hub actions and clears composition/Translate/Pet panels.
21. Confirm Manifest still has no network, ad, analytics, Accessibility, or overlay permission.

## Commercial direction

The intended business model is free base version plus paid Pro unlock.

Pro can later unlock more pets, more outfits, higher local dictionary quota, higher clipboard quota, Pro Aurora, and optional offline packs. Do not put ads inside the keyboard input surface. Version `0.9.0` contains no advertising, billing, network, or cloud translation code.
