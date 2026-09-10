# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.2.0`.

## Network

Orbit IME `0.2.0` does not request `INTERNET` permission.

The app cannot upload input text, clipboard text, Pinyin buffers, templates, or saved clips to a server because no network permission is declared.

## Advertising and analytics

Orbit IME `0.2.0` includes:

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

Orbit IME `0.2.0` includes a minimal local Pinyin 26-key mode.

Pinyin candidates are generated from a small static local dictionary inside the app package. Pinyin buffers are not uploaded and are not persisted.

Typed key streams are not saved.

## Sensitive fields

Orbit IME enters privacy mode for password-like fields and fields that request no personalized learning.

In privacy mode:

- Hub actions are hidden.
- Clipboard vault actions are hidden.
- Pinyin candidate tools are disabled.
- Saving is blocked.

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

Orbit IME `0.2.0` contains a Pro placeholder only.

It does not implement billing, advertising, analytics, cloud sync, or account login.
