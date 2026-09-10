# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with local English input, Pinyin 26-key input, local dictionary ranking, a user-controlled clipboard panel, Translate Preview, local skins, a keyboard-only pet MVP, and a Pro offline translation pack placeholder.

## Product boundary

This project is still not a full Chinese IME and does not try to compete with mature commercial IMEs in prediction quality.

Version `0.8.0` is a usability-fix release. It focuses on making the existing keyboard easier to use instead of adding new large features.

## Privacy boundary

Version `0.8.0` deliberately avoids network and advertising logic.

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

## Usability fixes in v0.8.0

- Hub buttons are localized in Pinyin mode:
  - 粘贴
  - 剪贴板
  - 翻译
  - 宠物
  - 返回键盘
- English mode keeps short English labels:
  - Paste
  - Clips
  - Translate
  - Pet
  - Keyboard
- Prompt-style actions are removed from the first-level Hub row.
- Save is moved into the Clips panel as `保存当前剪贴板`.
- Keyboard bottom padding is increased to reduce overlap with Android's input-method switcher bubble.
- Key rows are slightly shorter to reduce total keyboard height.
- Pet chip is hidden while Pinyin composition is active so candidates keep priority.
- Settings page uses product sections instead of version-number section titles.
- Settings page keeps the version only as `About · v0.8.0`.
- Pinyin dictionary is expanded with generic chat, development, input-method, and study terms.
- Generic shorthand candidates are added, such as `wgj -> 文件夹`, `wj -> 文件`, `wt -> 问题`, and `xg -> 修改`.

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
- Keyboard Pet MVP with keyboard-only display, check-in, local growth, open-egg, hide/show, local template chat, and outfit placeholder.

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
orbit-ime-v0.8-debug-apk
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
6. Switch to `拼音` mode and confirm Hub labels are Chinese.
7. Type `nihao` and confirm `你好` appears.
8. Type `wgj`, `wj`, `wt`, `xg`, `shurufa`, `jianqieban`, and confirm useful candidates appear.
9. Select candidates repeatedly and confirm local dictionary ranking changes.
10. Open Clips and confirm `保存当前剪贴板` is inside the Clips panel.
11. Confirm Android's long-press menu is not treated as an Orbit bug.
12. Use Translate Preview sources.
13. Confirm free users get prompt generation only.
14. Open Pet panel from the Hub when not composing Pinyin.
15. Confirm pet does not appear in password fields.
16. Confirm privacy mode hides Hub actions and clears composition/Translate/Pet panels.
17. Confirm Manifest still has no network, ad, analytics, Accessibility, or overlay permission.

## Commercial direction

The intended business model is free base version plus paid Pro unlock.

Pro can later unlock more pets, more outfits, higher local dictionary quota, higher clipboard quota, Pro Aurora, and optional offline packs. Do not put ads inside the keyboard input surface. Version `0.8.0` contains no advertising, billing, network, or cloud translation code.
