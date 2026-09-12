# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.19.0`.

## Network

Orbit IME `0.19.0` does not request `INTERNET` permission. The installed app does not upload input text, clipboard text, composing buffers, translation source/result text, candidate-ranking state, personal dictionary entries, pet data, expression history, skins, saved clips, or generated sticker images.

Public dictionary/Emoji files are downloaded only by the build machine, hash-verified, converted to packaged offline assets, and read locally by the installed IME.

## Advertising and analytics

Orbit contains no ad SDK, analytics SDK, tracking SDK, or remote-configuration SDK.

## Input engine

Local candidate generation can use AOSP/Jieba/CC-CEDICT Chinese assets, ESDB/SCOWL English assets, project fallback vocabulary, DP Pinyin segmentation, adaptive Beam Search, packaged 1/2/3-gram counts, local explicit selection frequency, prefix association and lower-confidence fuzzy/keyboard-typo recovery.

Pinyin/English composing buffers are temporary. Candidate commit replaces Android's active composing region; Orbit does not persist a raw key stream. A short text tail before the cursor may be used in memory for ranking and is not saved with app/package identity.

## Personal learning library

v0.19 expands local learning to 20,000 entries for the free base and 100,000 for the future Pro capacity placeholder. The store is no longer one large SharedPreferences JSON value. It uses app-private files:

```text
files/orbit-user-dictionary/dictionary.tsv
files/orbit-user-dictionary/journal.tsv
```

Normal learning appends a small local journal record; the store periodically compacts into a base file. Old SharedPreferences learning data is migrated locally once.

Persistent learning records remain limited to:

```text
pinyin
committed candidate text
frequency
updatedAt
```

Orbit does not persist the full conversation, surrounding sentence, target app/package, target-field identity, or complete typed stream.

## Clipboard

Orbit provides local Recent and Pinned sections. The clipboard listener exists only while the IME window is visibly shown. There is no background clipboard-harvesting service. Suspicious OTP/password/API-key/session-like text is rejected before persistence.

## Emoji / kaomoji / symbols

Orbit packages Unicode Emoji 17.0 fully-qualified sequences under the Unicode License v3 alongside project-authored categorized Emoji/kaomoji and symbol mappings. Recent expression storage contains only the selected expression itself. Long-press copy happens only after explicit user action.

## Local pet stickers

v0.19 has 16 catalog pets and 8 local sticker states per pet, producing 128 local sticker definitions. PNGs are generated into app-private cache. New catalog pets reuse one of the eight stable local renderer archetypes so they remain visible without remote artwork.

`OrbitStickerProvider` remains `exported=false` with `grantUriPermissions=true`. Temporary read access is granted only after an explicit sticker action and only to the target editor. Unsupported editors receive Emoji fallback text. No external-storage permission or runtime sticker download is used.

## Pet and outfit state

The pet module stores only local state such as catalog pet id, owned ids, EXP, Stars, streak, typed-character counters, display mode, current outfit and a short recent-action code/timestamp used for temporary micro-feedback. v0.19 expands the catalog to 16 pets and 24 outfits. It does not store the surrounding message that caused a feedback line.

No overlay permission, screenshot capture, camera input, cloud pet service or AI pet chat is used.

## Imported data assets

Normal builds use pinned/audited:

```text
AOSP PinyinIME             Apache-2.0
Jieba frequency data       MIT
CC-CEDICT                  CC BY-SA 4.0
ESDB/SCOWL en_US-large     ESDB redistribution notice
Unicode Emoji 17.0         Unicode License v3
```

Required notices are packaged under `assets/ime/third_party_notices/`. CC-CEDICT-derived lexicon/translation data remains CC BY-SA 4.0 data and is documented separately from application source code.

## Translation

Translation remains local at runtime:

```text
project exact phrase tables
-> CC-CEDICT exact lookup
-> CC-CEDICT sharded conservative longest-match composition
-> project local sentence composer
-> explicit unavailable state
```

`前一句`, selected text and clipboard text are read only after explicit user action. Translation source/result text is not persisted by the translation module. Orbit has no cloud translation endpoint or external translation API.

## Sensitive fields

For password-like/no-personalized-learning fields, Orbit hides extra tools, detaches clipboard listening, clears composing/translation state, disables pet growth and blocks personal-dictionary learning.

## Permissions intentionally not requested

Orbit does not request Internet, Accessibility, overlay/floating window, contacts, SMS, location, camera, microphone, external storage, or notifications.

## Commercial boundary

Orbit IME `0.19.0` still contains only Pro capacity/placeholders. It does not implement billing, advertising, analytics, cloud sync, cloud translation, account login, external translation APIs, AI pet chat, paid gacha, or a skin marketplace.
