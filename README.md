# Orbit IME Android

Orbit IME is a privacy-first Android input method with local Pinyin/English candidates, local personalization, mature clipboard history, offline translation keyboard, skins, and a local keyboard pet.

## Current version

```text
0.16.0
```

v0.16 focuses on actual typing usability: larger licensed Chinese data, uninterrupted long-sentence Pinyin, lower UI churn while typing, a recent/pinned clipboard, and a translation mode that visibly produces and inserts local translation results.

## Chinese input pipeline

```text
continuous Pinyin
-> exact packaged/user candidates
-> dynamic-programming segmentation
-> compact lexicon lookup
-> bounded phrase beam search
-> static frequency + 1/2/3-gram
-> local user-frequency boost
-> fuzzy/typo penalty
-> top candidates
```

v0.16 changes:

- Pinyin composing buffer: up to 192 normalized letters.
- Segmentation paths: up to 6 for short input; progressively fewer paths for long input.
- Phrase span: up to 8 syllables per beam edge; long sentences are built from multiple edges.
- Beam width: up to 56 for short input and automatically reduced as the syllable count grows.
- Visible candidates: 12.
- Candidate-query LRU plus phrase-lookup LRU reduce repeated work as one sentence grows letter by letter.
- Very long queries skip whole-sentence fuzzy expansion and use tighter beam/entry limits so latency does not grow as aggressively as search space.
- While letters/backspace are typed, Orbit refreshes the dynamic candidate/tool region instead of recreating every key row.
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

The exact counts are produced in `app/src/main/assets/ime/mature-report.json` during a normal build. These minimums are sanity gates, not a claim that Orbit already matches proprietary commercial IME corpora. See `DATA_SOURCES.md`.

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

v0.16 allows longer learned phrases/sentences (up to 192 Pinyin letters / 96 text characters). It does not persist full chat streams, app/package identity, target fields, or surrounding sentences.

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

Translate is an input mode rather than only a prompt/source panel.

Chinese -> English:

```text
enter Translate while in Pinyin mode
-> keep typing continuous Pinyin
-> the current best Chinese candidate participates in the source preview
-> space/candidate commits Chinese chunks into the temporary translation source buffer
-> panel shows 原文 and 译文
-> tap 译文上屏 (or use the translation action once the source is complete)
-> translation is inserted into the current app
```

English -> Chinese works the same way from EN mode. Sources can also be loaded from the previous sentence, selected text, or clipboard.

Translation order:

```text
exact packaged phrase tables
-> conservative local longest-phrase sentence composer
-> explicit "offline dictionary does not cover this sentence" state
```

English lookup keys are normalized before local composition, so case differences such as `Pinyin`, `English`, and `Chinese` do not create false misses.

If local coverage is insufficient, Orbit may offer a translation prompt for copying, but that prompt is never displayed as if it were a translation result. Runtime translation remains offline and deterministic; it is not equivalent to a cloud MT system such as Google Translate or DeepL.

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

Frequencies/counts are encoded in base36. Chinese and English readers use bounded shard caches instead of loading the whole mature pack as a giant Kotlin map.

## Other functions retained

- Chinese/English quick phrases.
- Fuzzy/typo correction at lower confidence than exact spelling.
- Keyboard pet: local check-in, hatch, switch, outfits, catalog and growth.
- Orbit Dark, Orbit Light, AMOLED Black, Study Blue, Pro Aurora placeholder.
- Privacy mode for password-like/no-personalized-learning fields.
- Android input-method picker button.

## Privacy boundary

Orbit IME v0.16 intentionally has:

- no `INTERNET` permission;
- no ads, analytics, or tracking SDK;
- no Accessibility permission;
- no overlay/floating-window permission;
- no cloud prediction/dictionary sync;
- no cloud/external translation API;
- no background clipboard/input harvesting;
- no full typed-stream persistence.

Build-machine downloads of pinned public dictionary sources are not an installed-app network capability.

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
orbit-ime-v0.16-debug-apk
```

## Post-build device acceptance

Prioritize:

- uninterrupted long Pinyin sentences and top-1 quality;
- candidate latency while the buffer grows;
- English composing/candidates;
- local learning and clear/reset;
- Clipboard Recent / pin / expiry / paste;
- Translate 原文/译文 preview and 译文上屏;
- Pet panel, skins, privacy mode, and input-method switching.

## Commercial boundary

The intended direction remains free base + optional paid Pro. v0.16 contains no billing, ads, analytics, runtime network, cloud translation, or external translation API.
