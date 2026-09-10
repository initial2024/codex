# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with an Orbit Hub style toolbar, local prompt templates, a user-controlled local clipboard vault, and a minimal local Pinyin 26-key mode.

## Product boundary

This project is not yet a full Chinese IME and does not try to compete with Gboard, Sogou, Baidu IME, or iFlytek IME in prediction quality.

Version `0.2.0` focuses on six things:

1. A real Android IME based on `InputMethodService`.
2. A dark Orbit Hub style keyboard surface.
3. Local prompt/phrase insertion.
4. User-initiated local clipboard saving.
5. English / Pinyin mode switching.
6. A minimal local Pinyin 26-key candidate bar.

Skin design is intentionally unchanged in `0.2.0`; custom skins will be added after the design assets/specification are supplied.

## Privacy boundary

Version `0.2.0` deliberately avoids network and advertising logic.

- No `INTERNET` permission.
- No ad SDK.
- No analytics SDK.
- No Accessibility permission.
- No Contacts, SMS, Location, Camera, Microphone, or Storage permission.
- Clipboard content is saved only after the user taps `Save`.
- Password-like input fields enter privacy mode and hide Hub functions.
- Typed key streams are not persisted.
- Pinyin candidates come from a small local static dictionary.

## Features in v0.2.0

- Android IME service declared in `AndroidManifest.xml`.
- Settings activity with input method setup buttons.
- English keyboard.
- Pinyin 26-key mode.
- `EN` / `拼音` toggle in the Hub toolbar.
- Local Pinyin buffer with composing text.
- Candidate bar for exact and prefix Pinyin matches.
- Candidate tap-to-commit.
- Space commits the first Pinyin candidate when a buffer exists.
- Backspace deletes the Pinyin buffer before deleting surrounding editor text.
- Number/symbol mode.
- Backspace, space, enter, caps toggle.
- Orbit Hub toolbar:
  - EN / 拼音
  - Paste
  - Save
  - Clips
  - Polish
  - Translate
  - Explain
  - Study
  - Prompt
  - Pro placeholder
- Local clipboard vault backed by `SharedPreferences` JSON.
- Secret/OTP filtering before persistence.
- ProGate placeholder for later paid unlocks.

## Not included yet

- Pinyin 9-key.
- Wubi.
- Handwriting recognition.
- Cloud sync.
- Ad monetization.
- Paid billing implementation.
- Smart segmentation.
- Large user dictionary.
- Custom skin system.

## Build with Codex

Recommended Codex task:

```text
Clone https://github.com/initial2024/codex and build the Android debug APK.
Use JDK 17 and Android SDK 35.
Run: gradle assembleDebug --no-daemon
If the environment has no Gradle command, install/provision Gradle 8.10.2 first, then rerun the build.
After building, return app/build/outputs/apk/debug/app-debug.apk.
Do not add INTERNET permission, ad SDK, analytics SDK, Accessibility permission, or background services.
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
orbit-ime-debug-apk
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
13. Toggle `123` and return with `ABC`.
14. Copy normal text in another app.
15. Tap `Save` in Orbit IME.
16. Tap `Clips` and insert the saved text.
17. Open a password field and confirm Hub functions are hidden.

## Commercial direction

The intended business model is free base version plus paid Pro unlock.

Do not put ads inside the keyboard input surface. If ads are ever tested later, restrict them to non-input surfaces such as settings, theme market, or template market. Version `0.2.0` contains no advertising code.
