# Orbit IME v0.18 Local IME Engine

This document defines the local runtime architecture used by Orbit IME `0.18.0`.

## Core Chinese input pipeline

```text
continuous Pinyin composing
-> normalize
-> exact user/asset candidates
-> PinyinSegmenter (dynamic programming)
-> CompactLexiconAsset
-> adaptive phrase Beam Search
-> NGramLanguageModel
-> packaged prefix association
-> lower-confidence fuzzy/typo big-lexicon lookup
-> CandidateRanker
-> UserDictionaryStore local boost
-> top candidates
```

English uses:

```text
EnglishImeEngine
-> CompactEnglishAsset
-> EnglishDictionary fallback / typo overlays
```

## Composing replacement invariant

Raw Pinyin/English belongs to Android's active composing region until the user chooses/commits a candidate.

Correct candidate flow:

```text
setComposingText(raw letters)
-> choose candidate
-> commitText(candidate) directly
```

The IME must not call `finishComposingText()` first, because that would commit raw letters and then append the candidate. Translation-mode transfer and explicit composition clearing replace the active composing region with empty text.

This invariant prevents output such as:

```text
sj数据库不够hy还有问题
```

## Mature Chinese data

Normal v0.18 builds merge:

- AOSP PinyinIME phrase/Pinyin/frequency data;
- conservative AOSP-backed readings for additional Jieba frequency words;
- CC-CEDICT Pinyin/simplified vocabulary at lower synthetic source frequency;
- project fallback/product vocabulary.

The runtime Chinese lexicon must contain at least 150,000 rows after deduplication.

## Continuous long-sentence input

A sentence does not need to exist as one dictionary key. `PinyinSegmenter` produces syllable paths and `PinyinImeEngine` chains lexical edges into sentence hypotheses.

Short-input maximums:

```text
Pinyin buffer: 192 letters
segmentation paths: up to 6
phrase span: up to 8 syllables
entries per span: up to 6
beam width: up to 56
internal results: up to 20
visible candidates: 12
candidate-query LRU: 48
phrase-lookup LRU: 384
```

As input grows, segmentation count, Beam width, phrase span and per-span entries shrink progressively.

## Fuzzy / typo / association

v0.18 no longer limits fuzzy correction to project-authored hard-coded maps.

`PinyinCorrectionEngine.queryVariants()` creates lower-confidence alternate Pinyin spellings from:

```text
zh/z, ch/c, sh/s
an/ang, en/eng, in/ing and related final confusions
n/l, f/h, r/l, u/v
adjacent-letter transposition
QWERTY neighboring-key substitution
one-extra-key deletion recovery
explicit known shortcuts
```

`PinyinImeEngine` then queries the actual packaged mature lexicon for these variants. It also uses `CompactLexiconAsset.prefix()` so partial Pinyin can surface real high-frequency words from the large data pack.

Exact candidates keep higher source priority and no correction penalty.

## Ranking

`CandidateRanker` combines:

```text
static/source frequency
+ token/character N-gram evidence
+ segmentation score
+ explicit local user frequency
+ source priority
- fuzzy/typo/prefix penalty
```

Local user learning stores only:

```text
pinyin
candidate text
frequency
updatedAt
```

## Runtime assets

```text
ime/lexicon/a.odict ... z.odict
ime/english/a.odict ... z.odict
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
ime/translation/zh/*.odict
ime/translation/en/*.odict
ime/emoji_unicode.txt
```

Readers use bounded shard/LRU caches instead of loading all data into one Kotlin map.

## English

v0.18 uses the pinned ESDB/SCOWL `en_US-large` vocabulary. Large English assets are sharded by first letter. Project-authored common words/phrases and typo fixes remain overlays.

## Translation

Translation mode reuses normal Chinese/English composing engines.

```text
project exact phrase table
-> CedictTranslationAsset exact lexical lookup
-> CC-CEDICT sharded longest-match composition
-> project LocalTranslationComposer
-> explicit unavailable state
```

Chinese translation shards are distributed across 64 code-point buckets; English shards use first-letter buckets. Runtime translation remains deterministic and offline.

## Emoji / kaomoji / stickers

`UnicodeEmojiAsset` reads build-generated Unicode Emoji 17.0 fully-qualified sequences. `ExpressionLibrary` keeps project-authored categorized Emoji/kaomoji, and `ExpressionStore` keeps only a bounded Recent expression list.

Pet stickers remain:

```text
8 pets × 3 moods = 24 local PNG stickers
```

PNG files are rendered into private cache and shared only through the non-exported `OrbitStickerProvider` after explicit user action. Unsupported editors receive Emoji fallback text.

## Symbol keyboard and long press

`SymbolLibrary` contains seven pages:

```text
常用 / 标点 / 括号 / 数学 / 货币 / 箭头 / 标记
```

The `符号` control cycles pages.

On the normal 26-key layout, letter keys expose small long-press hints. Top-row letters map to digits 1..0; other letters map to common punctuation/symbols. Long press commits the alternate symbol only after resolving any active composition.

## Visual pet

Persistent state remains in `PetRepository`; rendering remains isolated to `PetAvatarView/PetAvatarRenderer`. Eight pet ids, four stages and outfit overlays are retained. Canvas use remains limited to pet/sticker artwork, not the full keyboard.

## Clipboard

`ClipboardStore` remains independent from the language model:

- Recent + Pinned;
- about-one-hour Recent expiry;
- listener only while IME window is visible;
- no background clipboard service.

## Build-time pipeline

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> AOSP/Jieba/ESDB-large base assets
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon + translation shards
-> Unicode Emoji 17.0 asset
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

See `DATA_SOURCES.md` for pins and license boundaries.

## Performance / privacy rules

- no runtime network I/O;
- no full typed-stream persistence;
- no persisted surrounding sentence/app identity;
- bounded lexicon, translation, Beam and query caches;
- exact paths rank ahead of fuzzy paths;
- sensitive fields disable learning and hide pet/expression/clipboard/translation tools;
- third-party data assets retain their own notices/licenses.

## v0.18 device acceptance priorities

1. verify candidate commit replaces raw Pinyin instead of appending to it;
2. measure long-sentence latency and top-1/top-3 quality;
3. test partial-Pinyin association and typo/fuzzy recovery;
4. test all letter long-press mappings;
5. cycle all seven symbol pages;
6. test English large-vocabulary completion;
7. test Unicode Emoji all-page plus project kaomoji/Recent;
8. test CC-CEDICT-backed translation preview/commit;
9. re-test clipboard, pets and stickers;
10. verify privacy mode and IME stability.
