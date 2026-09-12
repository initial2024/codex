# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.20.0`.

## Network

Orbit IME `0.20.0` does not request `INTERNET` permission. The installed app does not upload input text, clipboard text, composing buffers, translation source/result text, candidate-ranking state, personal dictionary entries, pet data, expression history, skins, saved clips, or generated sticker images.

Public dictionary/Emoji files are downloaded only by the build machine, hash-verified, converted to packaged offline assets, and read locally by the installed IME.

## Advertising and analytics

Orbit contains no ad SDK, analytics SDK, tracking SDK, or remote-configuration SDK.

## Input engine and next-phrase association

Local candidate generation can use AOSP/Jieba/CC-CEDICT Chinese assets, ESDB/SCOWL English assets, project fallback vocabulary, project-maintained software/platform names, DP Pinyin segmentation, adaptive Beam Search, packaged 1/2/3-gram counts, local explicit selection frequency, prefix association and lower-confidence fuzzy/keyboard-typo recovery.

v0.20 expands the visible candidate pool to up to 32 entries and can show next-word/next-phrase associations after a Chinese candidate is committed. Association reads only a short text tail before the cursor, combines packaged local 2/3-gram evidence with project-authored high-confidence mappings, and does not persist the surrounding sentence.

Pinyin/English composing buffers are temporary. Candidate commit replaces Android's active composing region; Orbit does not persist a raw key stream. Context used for ranking/association is not saved with app/package identity.

## Personal learning library

Local learning supports 20,000 entries for the free base and 100,000 for the future Pro capacity placeholder. The store uses app-private files:

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

Orbit provides local Recent and Pinned sections. The text clipboard listener exists only while the IME window is visibly shown. There is no background clipboard-harvesting service. Suspicious OTP/password/API-key/session-like text is rejected before persistence.

Image sticker clipboard fallback is separate from text history: a sticker PNG URI can be placed on the system clipboard only after the user explicitly taps/long-presses a sticker. That URI is not converted into a text history item.

## Emoji / kaomoji / symbols

Orbit packages Unicode Emoji 17.0 fully-qualified sequences under the Unicode License v3 alongside project-authored categorized Emoji/kaomoji and symbol mappings. Recent expression storage contains only the selected expression itself. Long-press copy happens only after explicit user action.

## Local pet stickers

Orbit has 16 catalog pets and 8 local sticker states per pet, producing 128 local sticker definitions. PNGs are generated into app-private cache. New catalog pets reuse one of the eight stable local renderer archetypes so they remain visible without remote artwork.

Sticker delivery uses two local paths:

1. if the target editor advertises `image/png`, Orbit tries Android `InputContentInfo` / `commitContent`;
2. if direct IME image content is unsupported, Orbit can put the generated PNG content URI on the system clipboard and temporarily grant the current target package read access, so apps such as some WeChat/QQ versions may accept manual long-press paste.

Clipboard-image paste support is controlled by the target app; Orbit cannot guarantee that every WeChat/QQ version accepts URI image paste. If neither image path works, Orbit falls back to the sticker's Emoji text.

`OrbitStickerProvider` remains `exported=false` with `grantUriPermissions=true`. No external-storage permission or runtime sticker download is used.

## Pet and outfit state

The pet module stores only local state such as catalog pet id, owned ids, EXP, Stars, streak, typed-character counters, display mode, current outfit and a short recent-action code/timestamp used for temporary micro-feedback. The catalog contains 16 pets and 24 outfits. It does not store the surrounding message that caused a feedback line.

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

v0.20 derives a four-character idiom/phrase boost layer from the already-audited CC-CEDICT pack and adds a project-authored common software/platform/product vocabulary. Orbit does not add a separate internet-scraped idiom database.

Required third-party notices are packaged under `assets/ime/third_party_notices/`. CC-CEDICT-derived lexicon, idiom and translation data remains CC BY-SA 4.0 data and is documented separately from application source code.

## Translation and optional context translation

Base/free translation remains single-sentence and fully local at runtime:

```text
project exact phrase tables
-> CC-CEDICT exact lookup
-> CC-CEDICT sharded conservative longest-match composition
-> project local sentence composer
-> explicit unavailable state
```

v0.20 adds an optional **Pro-gated context translation** setting. It is disabled by default and unavailable to ordinary/free users. When explicitly enabled, Orbit may temporarily read up to the previous two sentences (bounded to a short cursor tail) and use them as a local context/block translation reference while translating the current sentence. The previous text and context translation preview are kept in memory only; they are not written to the personal dictionary, clipboard history, analytics, account storage or any network service.

The current sentence translation remains the text inserted into the target editor. Orbit does not automatically resend previous conversation text.

`前一句`, selected text and clipboard text are read only after explicit user action. Orbit has no cloud translation endpoint or external translation API.

## Sensitive fields

For password-like/no-personalized-learning fields, Orbit hides extra tools, detaches clipboard listening, clears composing/translation state, disables pet growth and blocks personal-dictionary learning. Context translation is therefore unavailable in sensitive fields.

## Permissions intentionally not requested

Orbit does not request Internet, Accessibility, overlay/floating window, contacts, SMS, location, camera, microphone, external storage, or notifications.

## Commercial boundary

Orbit IME `0.20.0` still contains Pro capacity/feature placeholders only. It does not implement billing, advertising, analytics, cloud sync, cloud translation, account login, external translation APIs, AI pet chat, paid gacha, or a skin marketplace.
