# Orbit IME Android

Orbit IME is a privacy-first Android input method with local Pinyin/English candidates, local personalization, clipboard history, offline translation, visual keyboard pets, Emoji/kaomoji, local pet stickers, and skins.

## Current version

```text
0.17.0
```

v0.17 keeps the v0.16 long-sentence input/data-engine work and adds two user-facing modules that were previously incomplete:

- the pet is now actually rendered instead of being only text/state;
- the keyboard now has a large local Emoji/kaomoji panel plus locally generated image stickers.

## Chinese input pipeline

```text
continuous Pinyin
-> exact packaged/user candidates
-> dynamic-programming segmentation
-> compact lexicon lookup
-> bounded/adaptive phrase beam search
-> static frequency + 1/2/3-gram
-> local user-frequency boost
-> fuzzy/typo penalty
-> top candidates
```

Current long-input behavior:

- Pinyin composing buffer: up to 192 normalized letters.
- Segmentation paths: up to 6 for short input; progressively fewer paths for long input.
- Phrase span: up to 8 syllables per beam edge.
- Beam width: up to 56 for short input and automatically reduced as the syllable count grows.
- Visible candidates: 12.
- Candidate-query LRU plus phrase-lookup LRU reduce repeated work as one sentence grows letter by letter.
- Very long queries skip whole-sentence fuzzy expansion and use tighter beam/entry limits.
- Normal letter/backspace input refreshes the dynamic candidate/tool region rather than recreating every key row.
- Temporary text-before-cursor context can improve local N-gram ranking but is not persisted.

Examples include:

```text
nihaoma -> 你好吗
nishishei -> 你是谁
shurufa -> 输入法
haishiyouwenti -> 还是有问题
```

## Mature Chinese data

Normal builds merge:

```text
AOSP PinyinIME raw Pinyin/frequency data (Apache-2.0)
+
Jieba default Chinese frequency dictionary (MIT)
```

Jieba does not provide Pinyin. Orbit derives extra word readings conservatively:

1. prefer exact AOSP phrase readings;
2. otherwise use only AOSP single-character readings whose dominant pronunciation is sufficiently clear;
3. skip ambiguous/missing readings instead of guessing;
4. give these derived entries lower confidence than native AOSP phrase rows;
5. derive additional bounded character N-grams from accepted Jieba words;
6. package the pinned Jieba MIT license.

The mature validator requires at least:

```text
AOSP accepted lexicon: 40,000
Jieba-derived additions: 40,000
combined runtime Chinese lexicon: 90,000
English vocabulary: 50,000
```

Exact generated counts are written to `app/src/main/assets/ime/mature-report.json` during a normal build. These are sanity gates, not claims of parity with proprietary commercial IMEs.

## English input

English letters stay in a composing buffer and show local candidates before commit. The mature pack uses pinned ESDB/SCOWL US English vocabulary plus project-authored high-frequency/phrase/typo data. Large English assets are sharded by first letter and read through a bounded LRU.

## Local personalization

Orbit stores only:

```text
pinyin
committed candidate text
frequency
updatedAt
```

Longer learned phrases/sentences are supported up to 192 Pinyin letters / 96 text characters. Orbit does not persist full chat streams, app/package identity, target fields, or surrounding sentences.

## Visual keyboard pet

The pet module is no longer text-only.

v0.17 adds `PetAvatarView` / `PetAvatarRenderer`:

- all 8 existing pets have different local vector-style silhouettes;
- the four growth stages change scale and visual detail;
- later stages gain aura/orbit details;
- equipped outfits are drawn on top of the pet;
- the full pet panel contains a large visual preview;
- when the pet is not hidden, the idle quick-phrase bar also contains a small clickable pet preview;
- settings shows the current pet visually as well.

Existing local pet state remains intact:

```text
pet id / owned pets
EXP / Stars / streak
stage / level / typed-character counters
visibility
outfit
```

No overlay permission is used; the pet is drawn only inside Orbit's own IME/settings surfaces.

## Emoji and kaomoji panel

The top keyboard toolbar now has a `表情 / Emoji` entry.

Local categories include:

```text
😀 faces
👍 gestures
❤️ hearts
🐱 animals
🍜 food
🎉 activity/atmosphere
✨ symbols
happy kaomoji
sad kaomoji
angry kaomoji
weird/funny kaomoji
love kaomoji
```

The library contains hundreds of selectable Unicode Emoji and kaomoji strings. The panel supports:

- horizontal category navigation;
- paging for large categories;
- one-tap commit to the current input field;
- long-press copy;
- a local Recent list (up to 48 unique recently used expressions);
- Clear Recent.

The recent-expression store contains only the expression string itself, not the message around it.

## Local image sticker pack

`🪐 贴图` provides 24 locally generated pet stickers:

```text
8 pets × 3 moods
happy / love / angry
```

The sticker panel shows actual graphical pet thumbnails rather than text labels alone.

Sending behavior:

```text
target editor advertises image/png support
-> commit InputContentInfo with a temporary content URI
-> host app receives the locally generated PNG

otherwise
-> automatically commit the sticker's fallback Emoji text
```

Sticker PNGs are generated into the app-private cache from the same pet renderer. There is no sticker download, external storage permission, cloud service, or runtime network dependency.

The provider is:

```text
exported = false
grantUriPermissions = true
```

so other apps do not get general browsing access to Orbit's cache. Temporary URI read access is granted only through an explicit user sticker action.

## Clipboard

The keyboard clipboard is split into:

```text
Pinned
Recent
```

Behavior:

- while the IME window is visible, newly copied non-sensitive text can enter Orbit's local Recent history;
- the clipboard listener is detached when the IME window hides;
- unpinned entries expire after about one hour;
- long-press an entry to pin/unpin it;
- pinned entries do not expire automatically;
- tap an entry to paste;
- Clear recent preserves pinned items; Clear all removes everything.

Orbit does not run a background clipboard-harvesting service and cannot replace Android/host-app long-press menus.

## Translation keyboard

Translate remains an input mode rather than only a prompt/source panel.

Chinese -> English:

```text
enter Translate while in Pinyin mode
-> keep typing continuous Pinyin
-> current best Chinese candidate participates in source preview
-> space/candidate commits Chinese chunks into temporary translation source
-> panel shows 原文 and 译文
-> tap 译文上屏
-> translation is inserted into the current app
```

English -> Chinese works analogously from EN mode. Sources can also be loaded from the previous sentence, selected text, or clipboard.

Translation order:

```text
exact packaged phrase tables
-> conservative local longest-phrase sentence composer
-> explicit "offline dictionary does not cover this sentence" state
```

The translation prompt fallback is never displayed as if it were a translation result.

## Build-time data pipeline

A normal build automatically runs:

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> tools/ime_importer.py
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

Sources are pinned by Git blob SHA. Required AOSP, Jieba, and ESDB/SCOWL notices are packaged with generated assets. The validator rejects suspiciously small packs, missing notices/pins, wrong version metadata, and forbidden runtime capabilities.

## Runtime asset layout

```text
ime/lexicon/a.odict ... z.odict
ime/english/a.odict ... z.odict
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

Frequencies/counts are encoded in base36. Chinese and English readers use bounded shard caches instead of loading the whole mature pack as one giant Kotlin map.

## Other functions retained

- Chinese/English quick phrases.
- Fuzzy/typo correction at lower confidence than exact spelling.
- Keyboard pet check-in, hatch, switch, outfits, catalog and growth.
- Orbit Dark, Orbit Light, AMOLED Black, Study Blue, Pro Aurora placeholder.
- Privacy mode for password-like/no-personalized-learning fields.
- Android input-method picker button.

## Privacy boundary

Orbit IME v0.17 intentionally has:

- no `INTERNET` permission;
- no ads, analytics, or tracking SDK;
- no Accessibility permission;
- no overlay/floating-window permission;
- no cloud prediction/dictionary sync;
- no cloud/external translation API;
- no background clipboard/input harvesting;
- no full typed-stream persistence;
- no external-storage permission for stickers.

Sensitive/password-like fields hide the pet, Emoji/kaomoji/sticker, clipboard, and translation tools.

## Build

GitHub Actions remains manual-only through `workflow_dispatch`.

```text
JDK 17
Python 3.12 recommended
Android SDK 35
Android build-tools 35.0.0
Gradle 8.10.2
```

Command:

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for the user-test APK.

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Actions artifact:

```text
orbit-ime-v0.17-debug-apk
```

## Post-build device acceptance

Prioritize:

- uninterrupted long Pinyin sentences and top-1 quality;
- candidate latency while the buffer grows;
- English composing/candidates;
- local learning and clear/reset;
- Clipboard Recent / pin / expiry / paste;
- Translate 原文/译文 preview and 译文上屏;
- full-size pet preview actually renders for every pet;
- small idle pet preview opens the pet panel;
- stage/outfit changes visibly affect the pet;
- Emoji categories, paging, recent list, one-tap insert and long-press copy;
- kaomoji rendering and insertion;
- all 24 sticker thumbnails render;
- sticker image commit works in an app advertising image/png IME content;
- unsupported image editors fall back to Emoji text;
- privacy mode hides all extra tool panels;
- input-method switching remains correct.

## Commercial boundary

The intended direction remains free base + optional paid Pro. v0.17 contains no billing, ads, analytics, runtime network, cloud translation, external translation API, or paid sticker/gacha implementation.
