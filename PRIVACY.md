# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.4.0`.

## Network

Orbit IME `0.4.0` does not request `INTERNET` permission.

The app cannot upload input text, clipboard text, Pinyin buffers, templates, saved clips, skin selection, or Translate Preview source text to a server because no network permission is declared.

## Advertising and analytics

Orbit IME `0.4.0` includes:

- No ad SDK.
- No analytics SDK.
- No tracking SDK.
- No remote configuration SDK.

## Clipboard vault

Orbit IME can save clipboard text into a local vault only after the user taps `Save`.

The vault is stored locally using app-private `SharedPreferences` JSON.

Orbit IME does not run a background clipboard harvesting service.

Before saving, Orbit IME rejects text that looks like:

- OTP-only numeric codes.
- Password-like text.
- API keys.
- Bearer tokens.
- Authorization headers.
- Cookie/session strings.
- Very long dense secret-like tokens.

## Pinyin mode

Orbit IME `0.4.0` includes a minimal local Pinyin 26-key mode.

Pinyin candidates are generated from a small static local dictionary inside the app package. Pinyin buffers are not uploaded and are not persisted.

Typed key streams are not saved.

## Translate Preview

Orbit IME `0.4.0` includes Translate Preview.

Translate Preview does not produce a cloud translation result. It only generates a local prompt that the user may insert or copy.

Supported sources are read only after an explicit user tap:

- Previous sentence via `getTextBeforeCursor`.
- Selected text via `getSelectedText`.
- Current clipboard text.
- In-IME draft buffer typed before committing to the target editor.

Translate Preview source text is not persisted. Generated preview prompts are not persisted. If the user taps `复制`, the generated prompt is placed in the system clipboard.

Before generating a prompt, Orbit IME rejects text that looks like OTP-only codes, passwords, API keys, bearer tokens, authorization headers, cookie/session strings, or very long dense tokens.

The default roadmap does not include cloud translation. If a future version ever considers an external translation service, it must be treated as a new privacy and permission decision rather than a continuation of `0.4.0`.

## Skin system

Orbit IME `0.4.0` includes a local skin system.

The selected skin ID is stored locally using app-private `SharedPreferences`.

Built-in skin tokens are packaged inside the app. Skin selection is not uploaded, synced, tracked, or used for advertising.

## Sensitive fields

Orbit IME enters privacy mode for password-like fields and fields that request no personalized learning.

In privacy mode:

- Hub actions are hidden.
- Clipboard vault actions are hidden.
- Pinyin candidate tools are disabled.
- Pinyin composition is cleared.
- Translate Preview is disabled and cleared.
- Saving is blocked.
- The keyboard visual state uses warning/border colors derived from the selected skin.

## Permissions intentionally not requested

Orbit IME does not request:

- Internet
- Accessibility
- Contacts
- SMS
- Location
- Camera
- Microphone
- External storage

## Commercial boundary

Orbit IME `0.4.0` contains a Pro placeholder only.

It does not implement billing, advertising, analytics, cloud sync, account login, a skin marketplace, or cloud translation.
