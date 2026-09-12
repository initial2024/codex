# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.16.0`.

## Network

Orbit IME `0.16.0` does not request `INTERNET` permission.

The installed app does not upload input text, clipboard text, Pinyin/English buffers, translation source/result text, candidate-ranking state, user dictionary entries, N-gram state, pet data, skin choice, or saved clips.

Public dictionary files are downloaded only by the build machine, verified against pinned Git blob SHA values, converted to packaged offline assets, and then read locally by the installed IME.

## Advertising and analytics

Orbit IME includes no ad SDK, analytics SDK, tracking SDK, or remote-configuration SDK.

## Clipboard history

v0.16 provides local `Recent` and `Pinned` clipboard sections.

- Orbit attaches a clipboard-change listener only while the IME window is visibly shown.
- The listener is removed when the IME window hides and when the service is destroyed.
- There is no background clipboard-harvesting service.
- Non-sensitive copied text can be stored in app-private `SharedPreferences` while the keyboard is visible.
- Unpinned Recent items expire after about one hour.
- Pinned items remain until the user unpins/deletes/clears them.
- The user can clear Recent items without deleting pinned items, or clear all items.

Before persistence, Orbit rejects text that looks like OTP-only numeric codes, passwords, API keys, bearer/authorization strings, cookies/sessions, or dense secret-like tokens.

Orbit cannot replace Android or host-app long-press text-selection menus.

## Pinyin engine and temporary context

Candidate generation runs locally and can use:

- packaged `.odict` lexicon assets;
- AOSP/Jieba-derived packaged Chinese data;
- project-authored fallback data;
- dynamic-programming Pinyin segmentation;
- bounded phrase-level beam search;
- packaged 1/2/3-gram counts;
- explicit local user-selection frequency;
- lower-confidence fuzzy/typo correction.

Pinyin composing buffers are temporary and are not persisted as a typed stream.

For ranking, the IME may read a short piece of text immediately before the cursor from the current input session. This context is used in memory for N-gram ranking only. Orbit does not save surrounding sentences, target-field identity, or the app/package name.

## User dictionary

The user dictionary learns after explicit candidate commit. The persistent record remains limited to:

```text
pinyin
committed candidate text
frequency
updatedAt
```

v0.16 allows longer personal phrase/sentence mappings, but does not persist the full conversation or raw key stream. Parsed entries may be cached in process memory for performance.

## Imported dictionary assets

Large dictionary packs are build-time application assets, not user records.

Current mature sources are pinned/audited AOSP PinyinIME data, Jieba MIT frequency data, and ESDB/SCOWL US English vocabulary. Required third-party notices are packaged with generated assets. See `DATA_SOURCES.md`.

## English composing

English letters are held in a temporary composing buffer until the user chooses a candidate or commits with space/control behavior. The buffer is not uploaded and is not persisted as a typed stream.

## Translation keyboard

v0.16 translation is a local keyboard mode.

- Chinese/Pinyin or English text is accumulated in a temporary translation source buffer.
- The panel can show the current source and a local translation result.
- Translation first uses exact packaged phrase tables, then a conservative local longest-phrase composer.
- If local lexical coverage is insufficient, Orbit explicitly reports that the offline dictionary does not cover the sentence.
- An optional generated translation prompt can be copied for use elsewhere, but is not represented as a translation result.
- `前一句`, selected text, and clipboard text are read only after explicit user action.
- Translation source text, generated prompt text, and translated output are not persisted by the translation module.

Orbit has no cloud translation endpoint, external translation API, or background translation service.

## Skin system

The selected skin ID is stored locally using app-private preferences. Skin selection is not uploaded, synced, tracked, or used for advertising.

## Keyboard Pet

The local pet panel stores only local counters/state such as pet id, owned ids, EXP, Stars, streak, typed-character counters, display mode, and equipped outfit. It does not store full input streams, surrounding sentences, app names, or target-field identity.

The pet does not request overlay permission, cannot draw outside the IME, and does not use cloud/AI chat.

## Sensitive fields

Orbit enters privacy mode for password-like fields and fields requesting no personalized learning. In privacy mode:

- Hub tools are hidden;
- clipboard capture/history actions are disabled;
- clipboard listener is detached;
- Pinyin/English composition is cleared;
- translation state is cleared;
- pet panel/growth is disabled;
- user-dictionary learning is blocked.

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
- Notifications

## Commercial boundary

Orbit IME `0.16.0` contains only Pro placeholders. It does not implement billing, advertising, analytics, cloud sync, cloud translation, account login, external translation APIs, AI pet chat, or a skin marketplace.
