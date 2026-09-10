# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.5.0`.

## Network

Orbit IME `0.5.0` does not request `INTERNET` permission.

The app cannot upload input text, clipboard text, Pinyin buffers, templates, saved clips, skin selection, Translate Preview source text, generated prompts, or user dictionary entries to a server because no network permission is declared.

## Advertising and analytics

Orbit IME `0.5.0` includes:

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

Orbit IME `0.5.0` includes a minimal local Pinyin 26-key mode.

Pinyin candidates are generated from a small generic static dictionary and a local user dictionary.

Pinyin buffers are not uploaded and are not persisted as typed streams.

## User dictionary

Orbit IME `0.5.0` includes a local user dictionary.

The user dictionary learns only after explicit candidate commit:

- Candidate tap.
- Space-to-select while a Pinyin buffer exists.

The stored record is limited to:

```text
pinyin -> committed candidate text -> frequency -> updatedAt
```

Orbit IME does not store surrounding sentence text, app name, target field, or full input history for user dictionary learning.

The dictionary is stored locally using app-private `SharedPreferences` JSON.

Free quota is 300 entries. Pro placeholder quota is 5000 entries.

A settings button can clear the local user dictionary.

Candidate learning is blocked in sensitive fields and for text that looks like OTP codes, passwords, API keys, tokens, authorization headers, cookies, sessions, or dense secret-like strings.

## Translate Preview

Translate Preview is local prompt generation only.

Orbit IME `0.5.0` does not include cloud translation, external translation APIs, or automatic translation.

Source text is read only after explicit source-button taps such as `前一句`, `选中文本`, `剪贴板`, or `草稿`.

Translate Preview source text and generated prompts are not persisted.

## Skin system

Orbit IME `0.5.0` includes a local skin system.

The selected skin ID is stored locally using app-private `SharedPreferences`.

Built-in skin tokens are packaged inside the app. Skin selection is not uploaded, synced, tracked, or used for advertising.

## Sensitive fields

Orbit IME enters privacy mode for password-like fields and fields that request no personalized learning.

In privacy mode:

- Hub actions are hidden.
- Clipboard vault actions are hidden.
- Pinyin candidate tools are disabled.
- Pinyin composition is cleared.
- Translate Preview is cleared.
- User dictionary learning is blocked.
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

Orbit IME `0.5.0` contains a Pro placeholder only.

It does not implement billing, advertising, analytics, cloud sync, cloud translation, account login, external translation APIs, or a skin marketplace.
