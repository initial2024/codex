# Orbit IME v0.16 Local IME Engine

This document defines the offline input engine used by Orbit IME `0.16.0`.

## Core runtime pipeline

```text
continuous Pinyin
-> normalize
-> exact asset/user candidates
-> PinyinSegmenter (dynamic programming)
-> CompactLexiconAsset
-> bounded phrase beam search
-> NGramLanguageModel
-> CandidateRanker
-> UserDictionaryStore local boost
-> top candidates
```

English uses `EnglishImeEngine -> CompactEnglishAsset -> EnglishDictionary fallback`.

## Mature Chinese data

The default build merges:

- AOSP PinyinIME phrase/Pinyin/frequency data;
- conservative Pinyin derivations for additional Jieba MIT frequency-dictionary words;
- project-authored fallback/product vocabulary.

Jieba readings are not guessed indiscriminately. Exact AOSP phrase readings are preferred. New words are generated only from AOSP single-character readings whose dominant pronunciation is sufficiently clear; ambiguous/missing readings are skipped.

## Continuous long-sentence input

A full sentence does not need to exist as one dictionary key. `PinyinSegmenter` creates syllable paths, and `PinyinImeEngine` composes multiple lexical edges into sentence candidates.

v0.16 maximums / short-input settings:

```text
Pinyin buffer: 192 letters
segmentation paths: up to 6
max lexical phrase span: up to 8 syllables
entries per span: up to 6
beam width: up to 56
complete internal results: up to 20
visible candidates: 12
candidate-query LRU: 48
phrase-lookup LRU: 384
```

These are not fixed costs for every sentence. As input grows, v0.16 progressively reduces segmentation paths, beam width, phrase span, entries per span, and complete-result count. Whole-sentence fuzzy expansion is disabled beyond 48 normalized letters. This keeps long continuous input usable without forcing the user to commit every word.

A sentence may exceed eight syllables because a beam hypothesis chains multiple lexical edges.

## Ranking

`CandidateRanker` combines:

```text
static frequency
+ phrase/character N-gram score
+ segmentation score
+ local explicit user frequency
+ source priority
- fuzzy/typo penalty
```

Exact spelling remains higher confidence than fuzzy correction. Raw-fallback commits do not substitute a prefix/fuzzy guess when no exact candidate exists.

## Temporary context

The IME may read a short tail of text before the cursor during the current input session and pass it to the N-gram scorer. The current Pinyin composing suffix is removed first. This context is held only in memory and is never written into user learning records.

## UI latency strategy

The keyboard has two layers:

```text
static key rows / top-level layout
dynamic candidate/tool host
```

During normal letter input and backspace, v0.16 refreshes only the dynamic host rather than removing/recreating every keyboard key. Full rebuilds remain for layout/mode changes such as symbols, language mode, or opening/closing top-level tools.

`PinyinImeEngine` caches recent query/context candidate lists and immutable phrase lookups with bounded LRUs. `OrbitInputMethodService` separately caches the current visible Pinyin result. Extending a sentence therefore reuses many of the same lexical lookups instead of reparsing the same asset rows on each keystroke.

## Local N-gram model

`NGramLanguageModel` reads packaged 1/2/3-gram files. Build preparation derives bounded character N-grams from accepted AOSP and Jieba-derived entries and merges them with project-authored counts. This is a deterministic count model, not a neural/cloud model.

## Compact assets

```text
ime/lexicon/a.odict ... z.odict
ime/english/a.odict ... z.odict
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

Integer frequencies/counts are stored in base36. Chinese and English readers keep only a bounded number of shards in memory.

## Local personalization

Persistent user-learning records contain only:

```text
pinyin
candidate text
frequency
updatedAt
```

v0.16 accepts up to 192 normalized Pinyin letters and 96 text characters per learned mapping. Learned records are cached in process memory for ranking; a learn/clear operation invalidates the candidate cache.

## Fuzzy correction

`PinyinCorrectionEngine` remains a lower-confidence compatibility path. It is not treated as an exact spelling source. Long input deliberately avoids whole-string fuzzy expansion because the search cost grows rapidly and full-sentence typo guesses are less trustworthy than exact segmentation.

## Clipboard runtime design

Clipboard history is separate from the language model. `ClipboardStore` supports Recent/Pinned entries, one-hour expiration for unpinned history, use/copy counters, pin/unpin, removal, clear-recent, and clear-all. The system clipboard listener exists only while the IME window is shown; it is detached when the IME window hides.

## Translation runtime design

Translation mode reuses the normal Chinese/English composing engines instead of creating a second keyboard implementation.

Chinese -> English:

```text
continuous Pinyin composing
-> current best candidate participates in source preview
-> explicit candidate/space commit into temporary translation source
-> exact local translation table
-> conservative longest-phrase LocalTranslationComposer
-> visible 原文 / 译文 preview
-> explicit translation commit to target app
```

English -> Chinese follows the analogous path. English translation-lexicon keys are normalized for case/punctuation before matching.

If local coverage is too low, the composer returns `null`; the UI reports that the offline pack does not cover the sentence rather than presenting a prompt as a translation result.

## Build-time pipeline

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> tools/ime_importer.py
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

See `DATA_SOURCES.md` for pinned source/license rules.

## Performance and privacy rules

- no runtime network I/O;
- no full typed-stream persistence;
- no persisted surrounding sentence/app identity;
- bounded shard caches, beam width, query caches, and lexical caches;
- no dictionary parsing inside drawing callbacks;
- exact paths rank ahead of fuzzy paths;
- sensitive fields disable learning/tools/clipboard capture.

## v0.16 device acceptance priorities

After build, measure rather than assume:

1. latency while a Pinyin buffer grows from a short phrase to a long sentence;
2. top-1/top-3 candidate quality on a fixed sentence set;
3. space/tap commit behavior for an uninterrupted long sentence;
4. English composing/candidate quality;
5. local personalization reorder/reset;
6. Clipboard Recent/pin/expiry/paste;
7. translation source/preview/commit behavior;
8. memory use and IME stability.

A later neural model should only be considered after these deterministic layers are measured.
