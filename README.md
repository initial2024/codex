# Orbit IME Android

Orbit IME is a privacy-first Android input method MVP with an Orbit Hub style toolbar, local prompt templates, a user-controlled local clipboard vault, a minimal local Pinyin 26-key mode, a local skin system, local Translate Preview prompt generation, and a local user dictionary.

## Product boundary

This project is not yet a full Chinese IME and does not try to compete with Gboard, Sogou, Baidu IME, or iFlytek IME in prediction quality.

Version `0.5.0` focuses on nine things:

1. A real Android IME based on `InputMethodService`.
2. A lightweight native View keyboard surface.
3. Local prompt/phrase insertion.
4. User-initiated local clipboard saving.
5. English / Pinyin mode switching.
6. A minimal local Pinyin 26-key candidate bar.
7. A local skin system using tokenized skins.
8. Local Translate Preview prompt generation.
9. A local user dictionary that ranks Pinyin candidates by explicit candidate-selection frequency.

## Privacy boundary

Version `0.5.0` deliberately avoids network, cloud translation, advertising, and background harvesting logic.

- No `INTERNET` permission.
- No cloud translation.
- No external translation API.
- No ad SDK.
- No analytics SDK.
- No Accessibility permission.
- No Contacts, SMS, Location, Camera, Microphone, or Storage permission.
- Clipboard content is saved only after the user taps `Save`.
- Password-like input fields enter privacy mode and hide Hub functions.
- Typed key streams are not persisted.
- Pinyin candidates come from a small generic static dictionary plus the user's local dictionary.
- User dictionary entries are learned only after explicit candidate commit.
- Skin selection is saved locally in `SharedPreferences`.
- Translate Preview generates a prompt locally and does not call a translation service.

## Features in v0.5.0

- Android IME service declared in `AndroidManifest.xml`.
- Settings activity with input method setup buttons.
- Settings activity skin selector.
- Settings activity user dictionary stats and clear button.
- English keyboard.
- Pinyin 26-key mode.
- `EN` / `拼音` toggle in the Hub toolbar.
- Local Pinyin buffer with composing text.
- Candidate bar for exact and prefix Pinyin matches.
- Candidate tap-to-commit.
- Space commits the first Pinyin candidate when a buffer exists.
- Backspace deletes the Pinyin buffer before deleting surrounding editor text.
- Pinyin mode maps `,` to `，` and `.` to `。`.
- User dictionary ranking:
  - exact user matches first,
  - then generic static dictionary candidates,
  - then user prefix matches.
- User dictionary storage via app-private `SharedPreferences` JSON.
- Free user dictionary quota: 300 entries.
- Pro placeholder user dictionary quota: 5000 entries.
- Number/symbol mode.
- Backspace, space, enter, caps toggle.
- Orbit Hub toolbar:
  - EN / 拼音
  - Paste
  - Save
  - Clips
  - Translate Preview
  - Polish
  - Explain
  - Study
  - Prompt
  - Pro placeholder
- Local clipboard vault backed by `SharedPreferences` JSON.
- Secret/OTP filtering before clipboard persistence, Translate Preview, and user dictionary learning.
- ProGate placeholder for later paid unlocks.
- Built-in skins:
  - Orbit Dark
  - Orbit Light
  - AMOLED Black
  - Study Blue
  - Pro Aurora, locked as a Pro placeholder until billing is implemented

## Local user dictionary

User-dictionary files:

```text
app/src/main/java/com/ccwu/orbitime/UserDictionaryStore.kt
USER_DICTIONARY.md
```

Orbit IME learns only after the user commits a Pinyin candidate by tapping a candidate or pressing space while a Pinyin buffer exists.

Stored record shape:

```text
pinyin -> candidate text -> frequency -> updatedAt
```

The app does not store surrounding sentences, app names, target fields, or full typed input history.

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

## Translate Preview

Translate Preview is local prompt generation only.

It does not call a translation API and does not produce a final translated result. It lets the user choose a source, select `中→英` or `英→中`, and insert or copy a translation prompt.

Supported sources:

- Previous sentence, read only after `前一句` is tapped.
- Selected text, read only after `选中文本` is tapped.
- Clipboard, read only after `剪贴板` is tapped.
- In-IME draft, typed into an IME-owned buffer before committing anything to the target editor.

The default roadmap does not include cloud translation.

## Not included yet

- Pinyin 9-key.
- Wubi.
- Handwriting recognition.
- Cloud sync.
- Cloud translation.
- External translation API.
- Ad monetization.
- Paid billing implementation.
- Smart segmentation.
- Large dictionary engine.
- Canvas keyboard rewrite.
- Compose migration.
- Room or Realm migration.
- Skin marketplace.

## Build with Codex

Recommended Codex task for later build:

```text
Clone https://github.com/initial2024/codex and build the Android debug APK.
Use JDK 17 and Android SDK 35.
Run: gradle assembleDebug --no-daemon
If the environment has no Gradle command, install/provision Gradle 8.10.2 first, then rerun the build.
After building, return app/build/outputs/apk/debug/app-debug.apk.
Do not add INTERNET permission, cloud translation, external translation API, ad SDK, analytics SDK, Accessibility permission, or background services.
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
orbit-ime-v0.5-debug-apk
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
11. Repeat the same candidate several times and confirm local dictionary count/frequency increases in settings.
12. Confirm repeated candidates are ranked above generic candidates for the same Pinyin.
13. Press backspace while Pinyin text is composing and confirm it deletes the Pinyin buffer first.
14. In Pinyin mode, press `,` and `.` and confirm they output `，` and `。`.
15. Toggle `123` and return with `ABC`.
16. Copy normal text in another app.
17. Tap `Save` in Orbit IME.
18. Tap `Clips` and insert the saved text.
19. Tap `Translate` and confirm Translate Preview opens instead of inserting an immediate template.
20. Open a password field and confirm Hub functions are hidden and the warning skin state is visible.
21. Open the app settings page and switch among Orbit Dark, Orbit Light, AMOLED Black, and Study Blue.
22. Confirm Pro Aurora is shown as a locked Pro placeholder.
23. Tap `清空用户词库` and confirm local dictionary stats reset.

## Commercial direction

The intended business model is free base version plus paid Pro unlock.

Do not put ads inside the keyboard input surface. If ads are ever tested later, restrict them to non-input surfaces such as settings, theme market, or template market. Version `0.5.0` contains no advertising code.
