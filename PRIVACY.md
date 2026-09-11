# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.15.0`.

## Network

Orbit IME `0.15.0` does not request `INTERNET` permission.

The app cannot upload input text, clipboard text, Pinyin buffers, English buffers, candidate-ranking state, N-gram data, templates, saved clips, skin selection, Translate Preview source text, generated prompts, user dictionary entries, pet data, or local translation inputs to a server because no network permission is declared.

## Advertising and analytics

Orbit IME `0.15.0` includes:

- No ad SDK.
- No analytics SDK.
- No tracking SDK.
- No remote configuration SDK.

## Clipboard vault

Orbit IME can save clipboard text into a local vault only after the user opens Orbit's own Clips panel and taps `保存当前剪贴板`.

The vault is stored locally using app-private `SharedPreferences` JSON.

Orbit IME does not run a background clipboard harvesting service.

Orbit IME cannot replace the host app or Android system long-press text-selection menu.

Before saving, Orbit IME rejects text that looks like OTP-only numeric codes, passwords, API keys, bearer tokens, authorization headers, cookie/session strings, or very long dense secret-like tokens.

## Pinyin mode and local IME engine

Orbit IME `0.15.0` includes a local Pinyin 26-key engine.

Candidate generation can use:

- packaged `.odict` lexicon assets;
- project-authored fallback dictionaries;
- dynamic-programming Pinyin segmentation;
- bounded phrase-level beam search;
- packaged 1-gram, 2-gram, and 3-gram count data;
- local user selection frequency;
- local fuzzy/typo correction.

All of these operations run on-device.

Pinyin buffers are temporary composing state. They are not uploaded and are not persisted as typed streams.

The N-gram model reads packaged count assets. It does not send text to a model or server.

Candidate ranking may inspect only temporary in-memory context supplied by the current input session. Orbit IME does not persist surrounding sentence context for language-model ranking.

## User dictionary

The user dictionary learns only after explicit candidate commit:

- Candidate tap.
- Space-to-select while a Pinyin buffer exists.

The stored record remains limited to:

```text
pinyin -> committed candidate text -> frequency -> updatedAt
```

Orbit IME does not store the surrounding sentence, app/package name, target field identity, or full input history for user-dictionary learning.

The dictionary is stored using app-private `SharedPreferences` JSON. v0.15 keeps an in-memory cache of these same records to avoid repeatedly parsing JSON during candidate ranking; this cache is process-local and is not a second persistent history.

## Imported dictionary assets

Large dictionary packs are build-time application assets, not user input records.

The v0.15 importer requires source/license metadata and generates compact `.odict` assets. Runtime lookup reads these packaged files locally.

Importing a third-party dictionary does not grant Orbit IME permission to upload user text or use network prediction.

## English candidate mode

English candidates are generated locally from packaged English candidate data.

The English composing buffer is not uploaded and is not persisted as a typed stream.

Typing English letters enters a temporary composing buffer. The user commits by tapping a candidate or pressing space.

## Local phrase translation and Translate Preview

Orbit IME `0.15.0` first attempts local phrase translation using packaged phrase tables.

If a phrase exists in the local table, the translated text can be inserted directly.

If no local phrase translation exists, Orbit falls back to local Translate Preview prompt generation.

Orbit IME does not include cloud translation, external translation APIs, model endpoints, or automatic background translation.

Source text is read only after explicit source-button taps such as `前一句`, `选中文本`, `剪贴板`, `拼音草稿`, `英文草稿`, or `草稿`.

Translate Preview source text, generated prompts, local translation inputs, and translated outputs are not persisted.

## Skin system

The selected skin ID is stored locally using app-private `SharedPreferences`.

Built-in skin tokens are packaged inside the app. Skin selection is not uploaded, synced, tracked, or used for advertising.

## Keyboard Pet

Orbit IME `0.15.0` includes a local keyboard pet panel.

The keyboard Hub has a `宠物` / `Pet` entry. The panel supports local check-in, daily free hatch, Stars-based hatch, owned-pet switching, outfit rotation, catalog display, show/hide, and local status chat.

The pet system stores only local counters and state, including active pet id, owned pet ids, EXP, Stars, streak, total typed character count, today typed character count, display mode, and equipped outfit id.

It does not store full input streams, surrounding sentences, app names, or target field identifiers.

It does not request overlay / floating-window permission. The pet cannot draw outside the IME surface.

It does not send notifications, play sounds, call a model, use cloud chat, or upload pet data.

## Sensitive fields

Orbit IME enters privacy mode for password-like fields and fields that request no personalized learning.

In privacy mode:

- Hub actions are hidden.
- Clipboard vault actions are hidden.
- Pinyin candidate tools are disabled.
- Pinyin composition is cleared.
- English composition is cleared.
- Translate Preview is cleared.
- Keyboard Pet panel is cleared.
- User dictionary learning is blocked.
- Pet growth is blocked.
- Saving is blocked.
- The keyboard visual state uses warning/border colors derived from the selected skin.

## Permissions intentionally not requested

Orbit IME does not request:

- Internet
- Accessibility
- Overlay / floating window
- Contacts
- SMS
- Location
- Camera
- Microphone
- External storage

## Commercial boundary

Orbit IME `0.15.0` contains a Pro placeholder only.

It does not implement billing, advertising, analytics, cloud sync, cloud translation, account login, external translation APIs, AI pet chat, or a skin marketplace.
