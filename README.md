# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with an Orbit Hub style toolbar, local prompt templates, a user-controlled local clipboard vault, a minimal local Pinyin 26-key mode, a local skin system, and local Translate Preview prompt generation.

## Product boundary

This project is not yet a full Chinese IME and does not try to compete with Gboard, Sogou, Baidu IME, or iFlytek IME in prediction quality.

Version `0.4.0` focuses on eight things:

1. A real Android IME based on `InputMethodService`.
2. A dark Orbit Hub style keyboard surface.
3. Local prompt/phrase insertion.
4. User-initiated local clipboard saving.
5. English / Pinyin mode switching.
6. A minimal local Pinyin 26-key candidate bar.
7. A local skin system using Gemini-provided design tokens.
8. Translate Preview that generates local translation prompts before insertion.

`0.4.0` does not implement cloud translation. The previous cloud-translation roadmap is removed from the default plan.

## Privacy boundary

Version `0.4.0` deliberately avoids network and advertising logic.

- No `INTERNET` permission.
- No ad SDK.
- No analytics SDK.
- No Accessibility permission.
- No Contacts, SMS, Location, Camera, Microphone, or Storage permission.
- Clipboard content is saved only after the user taps `Save`.
- Password-like input fields enter privacy mode and hide Hub functions.
- Typed key streams are not persisted.
- Pinyin candidates come from a small local static dictionary.
- Skin selection is saved locally in `SharedPreferences`.
- Translate Preview only creates prompt text locally; it does not call a translation service.

## Features in v0.4.0

- Android IME service declared in `AndroidManifest.xml`.
- Settings activity with input method setup buttons.
- Settings activity skin selector.
- English keyboard.
- Pinyin 26-key mode.
- `EN` / `拼音` toggle in the Hub toolbar.
- Local Pinyin buffer with composing text.
- Candidate bar for exact and prefix Pinyin matches.
- Candidate tap-to-commit.
- Space commits the first Pinyin candidate when a buffer exists.
- Backspace deletes the Pinyin buffer before deleting surrounding editor text.
- Pinyin mode maps `,` to `，` and `.` to `。`.
- Number/symbol mode.
- Backspace, space, enter, caps toggle.
- Orbit Hub toolbar:
  - EN / 拼音
  - Paste
  - Save
  - Clips
  - Translate
  - Polish
  - Explain
  - Study
  - Prompt
  - Pro placeholder
- Translate Preview sources:
  - Previous sentence, read only after a user tap.
  - Selected text, read only after a user tap.
  - Clipboard, read only after a user tap.
  - In-IME draft buffer, typed before committing to the target editor.
- Translate Preview actions:
  - Direction toggle: 中→英 / 英→中.
  - Generate local prompt.
  - Insert generated prompt.
  - Copy generated prompt.
  - Reset or cancel.
- Local clipboard vault backed by `SharedPreferences` JSON.
- Secret/OTP filtering before clipboard persistence and Translate Preview prompt generation.
- ProGate placeholder for later paid unlocks.
- Built-in skins:
  - Orbit Dark
  - Orbit Light
  - AMOLED Black
  - Study Blue
  - Pro Aurora, locked as a Pro placeholder until billing is implemented

## Translate Preview behavior

Translate Preview is prompt-only in `0.4.0`.

Example generated prompt:

```text
Translate the following Chinese into natural English. Keep the meaning accurate and avoid stiff literal wording.

Text:
我一会处理这个问题。
```

This prompt can be inserted into ChatGPT, Gemini, a browser input box, or another writing surface. Orbit IME itself does not produce the final translated sentence in this version.

## Skin implementation

Skin-related files:

```text
app/src/main/java/com/ccwu/orbitime/OrbitSkin.kt
app/src/main/java/com/ccwu/orbitime/OrbitSkins.kt
app/src/main/java/com/ccwu/orbitime/SkinManager.kt
app/src/main/java/com/ccwu/orbitime/OrbitTheme.kt
SKIN_DESIGN.md
```

`SkinManager` stores the selected skin in local `SharedPreferences`. `OrbitInputMethodService` and `MainActivity` both read the same selected skin. Password/privacy mode derives a stricter visual state from the current skin by replacing accent/border with warning colors.

## Not included yet

- Cloud translation.
- Pinyin 9-key.
- Wubi.
- Handwriting recognition.
- User-generated local dictionary.
- Cloud sync.
- Ad monetization.
- Paid billing implementation.
- Smart segmentation.
- Large user dictionary.
- Canvas keyboard rewrite.
- Compose migration.
- Skin marketplace.

## Build with Codex

Recommended Codex task:

```text
Clone the repository and build the Android debug APK.
Use JDK 17 and Android SDK 35.
Run: gradle assembleDebug --no-daemon
If the environment has no Gradle command, install/provision Gradle 8.10.2 first, then rerun the build.
After building, return app/build/outputs/apk/debug/app-debug.apk.
Do not add INTERNET permission, ad SDK, analytics SDK, Accessibility permission, cloud translation, or background services.
Follow CODEX_TASK.md exactly.
```

## Build with GitHub Actions

A workflow is included at:

```text
.github/workflows/build-apk.yml
```

It installs JDK 17, Android SDK 35, Gradle 8.10.2, then runs:

```text
gradle assembleDebug --no-daemon
```

The debug APK is uploaded as artifact:

```text
orbit-ime-v0.4-debug-apk
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

## First manual test checklist

1. Install the APK on Android.
2. Open Orbit IME app.
3. Tap `打开输入法设置`.
4. Enable Orbit IME.
5. Tap `显示输入法切换器`.
6. Switch to Orbit IME.
7. Type English letters in `EN` mode.
8. Tap `拼音` to switch into Pinyin mode.
9. Type `nihao` and confirm `你好` appears in the candidate bar.
10. Tap `你好` or press space to commit it.
11. Type `kaoyan`, `cailiao`, `shuxue`, `yingyu`, `dongnan`, or `zheda` and confirm local candidates appear.
12. Press backspace while Pinyin text is composing and confirm it deletes the Pinyin buffer first.
13. In Pinyin mode, press `,` and `.` and confirm they output `，` and `。`.
14. Toggle `123` and return with `ABC`.
15. Copy normal text in another app.
16. Tap `Save` in Orbit IME.
17. Tap `Clips` and insert the saved text.
18. Tap `Translate` and choose `剪贴板`; confirm a local prompt preview appears.
19. Tap `插入`; confirm the generated prompt is inserted into the editor.
20. Tap `Translate` -> `草稿`; type text inside the keyboard draft, press `生成Prompt`, then insert it.
21. Tap `Translate` -> `前一句`; confirm it only reads after the explicit tap.
22. Open a password field and confirm Hub functions are hidden, Pinyin composition is cleared, Translate Preview is unavailable, and the warning skin state is visible.
23. Open the app settings page and switch among Orbit Dark, Orbit Light, AMOLED Black, and Study Blue.
24. Confirm Pro Aurora is shown as a locked Pro placeholder.

## Commercial direction

The intended business model is free base version plus paid Pro unlock.

Do not put ads inside the keyboard input surface. If ads are ever tested later, restrict them to non-input surfaces such as settings, theme market, or template market. Version `0.4.0` contains no advertising code.
