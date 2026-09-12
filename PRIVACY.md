# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.18.0`.

## Network

Orbit IME `0.18.0` does not request `INTERNET` permission.

The installed app does not upload input text, clipboard text, Pinyin/English composing buffers, translation source/result text, candidate-ranking state, user dictionary entries, N-gram state, pet data, expression history, skin choice, saved clips, or generated sticker images.

Public dictionary/Emoji files are downloaded only by the build machine, hash-verified, converted to packaged offline assets, and then read locally by the installed IME.

## Advertising and analytics

Orbit IME includes no ad SDK, analytics SDK, tracking SDK, or remote-configuration SDK.

## Pinyin / English engine

Candidate generation runs locally and can use:

- sharded AOSP/Jieba/CC-CEDICT-derived Chinese lexicon assets;
- sharded ESDB/SCOWL English assets;
- project-authored fallback/product vocabulary;
- dynamic-programming Pinyin segmentation;
- adaptive phrase-level Beam Search;
- packaged 1/2/3-gram counts;
- explicit local user-selection frequency;
- packaged prefix association;
- lower-confidence fuzzy initials/finals and keyboard-typo variants.

Pinyin/English composing buffers are temporary. Candidate commit replaces Android's active composing region; Orbit does not persist raw key streams.

For ranking, the IME may read a short piece of text immediately before the cursor during the current session. This context is used in memory only and is not saved with app/package identity.

## User dictionary

Persistent learning records remain limited to:

```text
pinyin
committed candidate text
frequency
updatedAt
```

Orbit does not persist the full conversation or surrounding sentence.

## Clipboard history

Orbit provides local `Recent` and `Pinned` sections.

- clipboard listener exists only while the IME window is visibly shown;
- no background clipboard-harvesting service exists;
- non-sensitive Recent entries expire after about one hour;
- pinned items remain until user action;
- suspicious OTP/password/API-key/session-like text is rejected before persistence.

## Emoji / kaomoji / symbols

v0.18 packages the Unicode Emoji 17.0 fully-qualified sequence list as an application asset under the Unicode License v3, alongside project-authored categorized Emoji/kaomoji and symbol mappings.

- selecting Emoji/kaomoji commits only that selected string;
- Recent expressions store only the expression itself;
- long-press copy occurs only after explicit user action;
- 26-key long-press number/symbol mappings are static local data and are not learned from user input.

## Local pet sticker images

Orbit provides 24 locally generated pet stickers (8 pets × 3 moods).

- PNGs are rendered into the app-private cache;
- no sticker image is downloaded at runtime;
- no external-storage permission is requested;
- `OrbitStickerProvider` is `exported=false` and `grantUriPermissions=true`;
- temporary read access is granted only when the user explicitly taps a sticker and the target editor accepts image content;
- unsupported editors receive the fallback Emoji text instead.

## Imported data assets

Large data packs are application assets, not user records.

v0.18 uses pinned/audited:

```text
AOSP PinyinIME             Apache-2.0
Jieba frequency data       MIT
CC-CEDICT                  CC BY-SA 4.0
ESDB/SCOWL en_US-large     ESDB redistribution notice
Unicode Emoji 17.0         Unicode License v3
```

Required notices are packaged in `assets/ime/third_party_notices/`. CC-CEDICT-derived lexicon/translation data remains CC BY-SA 4.0 data and is documented separately from application source code.

## Translation keyboard

Translation remains local at runtime.

Order:

```text
project-authored exact phrase tables
-> CC-CEDICT exact lexical lookup
-> CC-CEDICT sharded conservative longest-match composition
-> project local sentence composer
-> explicit unavailable state
```

`前一句`, selected text and clipboard text are read only after explicit user action. Translation source/result text is not persisted by the translation module. Orbit has no cloud translation endpoint or external translation API.

## Visual pet

The pet stores only local counters/state such as pet id, owned ids, EXP, Stars, streak, typed-character counters, display mode and outfit. Rendering uses only this local state and does not capture screenshots, camera input or user images. No overlay permission or cloud/AI chat is used.

## Sensitive fields

For password-like/no-personalized-learning fields:

- Hub tools are hidden;
- Emoji/kaomoji/sticker panels are hidden;
- clipboard capture/history actions are disabled;
- clipboard listener is detached;
- composing buffers are cleared;
- translation state is cleared;
- pet growth/panel is disabled;
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

Orbit IME `0.18.0` contains only Pro placeholders. It does not implement billing, advertising, analytics, cloud sync, cloud translation, account login, external translation APIs, AI pet chat, or a skin marketplace.
